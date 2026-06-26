package com.yr.perftest.platform.execution.failure;

import com.yr.perftest.platform.execution.TaskExecutionResult;
import com.yr.perftest.platform.execution.TaskSamplePage;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class FailureSampleStore {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public void initialize(Path dbPath) throws Exception {
        Files.createDirectories(dbPath.getParent());
        try (Connection connection = open(dbPath); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS samples (
                        id INTEGER PRIMARY KEY,
                        ts INTEGER NOT NULL,
                        label TEXT NOT NULL,
                        code TEXT NOT NULL,
                        success INTEGER NOT NULL,
                        elapsed INTEGER NOT NULL,
                        message TEXT,
                        thread_name TEXT,
                        url TEXT,
                        request_headers TEXT,
                        request_body TEXT,
                        response_headers TEXT,
                        response_body TEXT,
                        failure_message TEXT
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_samples_label_code_ts ON samples(label, code, ts)");
        }
    }

    public void insert(Path dbPath, FailureSampleRecord record) throws Exception {
        try (Connection connection = open(dbPath); PreparedStatement statement = connection.prepareStatement("""
                INSERT OR IGNORE INTO samples (
                    id, ts, label, code, success, elapsed, message, thread_name, url,
                    request_headers, request_body, response_headers, response_body, failure_message
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setLong(1, record.id());
            statement.setLong(2, record.ts());
            statement.setString(3, record.label());
            statement.setString(4, record.code());
            statement.setInt(5, record.success() ? 1 : 0);
            statement.setLong(6, record.elapsed());
            statement.setString(7, record.message());
            statement.setString(8, record.threadName());
            statement.setString(9, record.url());
            statement.setString(10, record.requestHeaders());
            statement.setString(11, record.requestBody());
            statement.setString(12, record.responseHeaders());
            statement.setString(13, record.responseBody());
            statement.setString(14, record.failureMessage());
            statement.executeUpdate();
        }
    }

    public TaskSamplePage querySummaries(Path dbPath, FailureSampleQuery query, int page, int pageSize) throws Exception {
        if (!Files.exists(dbPath)) {
            return emptyPage(page, pageSize);
        }
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(100, pageSize));
        int offset = (safePage - 1) * safePageSize;
        FilterSql filterSql = buildFilter(query);
        int total = count(dbPath, filterSql);
        List<TaskExecutionResult.Sample> samples = new ArrayList<>();
        String sql = """
                SELECT id, ts, label, code, success, elapsed, message, thread_name
                FROM samples
                """ + filterSql.whereClause() + """
                ORDER BY id DESC
                LIMIT ? OFFSET ?
                """;
        try (Connection connection = open(dbPath); PreparedStatement statement = connection.prepareStatement(sql)) {
            bindFilter(statement, filterSql, 1);
            statement.setInt(filterSql.paramCount() + 1, safePageSize);
            statement.setInt(filterSql.paramCount() + 2, offset);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    samples.add(toSummary(resultSet));
                }
            }
        }
        return new TaskSamplePage(safePage, safePageSize, total, samples);
    }

    public Optional<TaskExecutionResult.Sample> findDetail(Path dbPath, long sampleId) throws Exception {
        if (!Files.exists(dbPath)) {
            return Optional.empty();
        }
        try (Connection connection = open(dbPath); PreparedStatement statement = connection.prepareStatement("""
                SELECT id, ts, label, code, success, elapsed, message, thread_name, url,
                       request_headers, request_body, response_headers, response_body, failure_message
                FROM samples WHERE id = ?
                """)) {
            statement.setLong(1, sampleId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(toDetail(resultSet));
            }
        }
    }

    public List<TaskExecutionResult.Sample> listSummariesAfter(Path dbPath, long lastEventId, int limit) throws Exception {
        if (!Files.exists(dbPath)) {
            return List.of();
        }
        List<TaskExecutionResult.Sample> samples = new ArrayList<>();
        try (Connection connection = open(dbPath); PreparedStatement statement = connection.prepareStatement("""
                SELECT id, ts, label, code, success, elapsed, message, thread_name
                FROM samples
                WHERE id > ?
                ORDER BY id ASC
                LIMIT ?
                """)) {
            statement.setLong(1, lastEventId);
            statement.setInt(2, Math.max(1, limit));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    samples.add(toSummary(resultSet));
                }
            }
        }
        return samples;
    }

    private int count(Path dbPath, FilterSql filterSql) throws Exception {
        String sql = "SELECT COUNT(1) FROM samples " + filterSql.whereClause();
        try (Connection connection = open(dbPath); PreparedStatement statement = connection.prepareStatement(sql)) {
            bindFilter(statement, filterSql, 1);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    private TaskExecutionResult.Sample toSummary(ResultSet resultSet) throws Exception {
        return new TaskExecutionResult.Sample(
                (int) resultSet.getLong("id"),
                TIME_FORMATTER.format(Instant.ofEpochMilli(resultSet.getLong("ts"))),
                resultSet.getString("code"),
                resultSet.getInt("success") == 1,
                resultSet.getString("label"),
                resultSet.getLong("elapsed"),
                safe(resultSet.getString("message")),
                safe(resultSet.getString("thread_name")),
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                ""
        );
    }

    private TaskExecutionResult.Sample toDetail(ResultSet resultSet) throws Exception {
        String requestHeaders = safe(resultSet.getString("request_headers"));
        String requestBody = safe(resultSet.getString("request_body"));
        String responseHeaders = safe(resultSet.getString("response_headers"));
        String responseBody = safe(resultSet.getString("response_body"));
        String failureMessage = safe(resultSet.getString("failure_message"));
        String url = safe(resultSet.getString("url"));
        String requestLine = requestBody.isBlank() ? url : requestBody.lines().findFirst().orElse(url);
        return new TaskExecutionResult.Sample(
                (int) resultSet.getLong("id"),
                TIME_FORMATTER.format(Instant.ofEpochMilli(resultSet.getLong("ts"))),
                resultSet.getString("code"),
                resultSet.getInt("success") == 1,
                resultSet.getString("label"),
                resultSet.getLong("elapsed"),
                safe(resultSet.getString("message")),
                safe(resultSet.getString("thread_name")),
                formatRequest(requestLine, requestHeaders, requestBody),
                formatResponse(resultSet.getString("code"), resultSet.getString("message"), responseHeaders, responseBody, failureMessage),
                requestLine,
                requestHeaders,
                requestBody,
                responseHeaders,
                responseBody,
                failureMessage
        );
    }

    private String formatRequest(String requestLine, String requestHeaders, String requestBody) {
        List<String> sections = new ArrayList<>();
        if (!requestLine.isBlank()) {
            sections.add(requestLine);
        }
        if (!requestHeaders.isBlank()) {
            sections.add(requestHeaders);
        }
        if (!requestBody.isBlank()) {
            sections.add(requestBody);
        }
        return String.join("\n\n", sections);
    }

    private String formatResponse(
            String statusCode,
            String message,
            String responseHeaders,
            String responseBody,
            String failureMessage
    ) {
        List<String> sections = new ArrayList<>();
        if (statusCode != null && !statusCode.isBlank()) {
            String statusLine = "HTTP " + statusCode;
            if (message != null && !message.isBlank()) {
                statusLine += " " + message;
            }
            sections.add(statusLine);
        }
        if (!responseHeaders.isBlank()) {
            sections.add(responseHeaders);
        }
        if (!responseBody.isBlank()) {
            sections.add(responseBody);
        }
        if (!failureMessage.isBlank()) {
            sections.add("--- Failure Message ---\n" + failureMessage);
        }
        return String.join("\n\n", sections);
    }

    private FilterSql buildFilter(FailureSampleQuery query) {
        List<String> clauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (query.label() != null && !query.label().isBlank()) {
            clauses.add("label = ?");
            params.add(query.label());
        }
        if (query.code() != null && !query.code().isBlank()) {
            clauses.add("code = ?");
            params.add(query.code());
        }
        if (query.success() != null) {
            clauses.add("success = ?");
            params.add(query.success() ? 1 : 0);
        }
        if (clauses.isEmpty()) {
            return new FilterSql("", List.of());
        }
        return new FilterSql("WHERE " + String.join(" AND ", clauses), params);
    }

    private void bindFilter(PreparedStatement statement, FilterSql filterSql, int startIndex) throws Exception {
        int index = startIndex;
        for (Object param : filterSql.params()) {
            if (param instanceof String value) {
                statement.setString(index++, value);
            } else if (param instanceof Integer value) {
                statement.setInt(index++, value);
            }
        }
    }

    private Connection open(Path dbPath) throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
    }

    private TaskSamplePage emptyPage(int page, int pageSize) {
        return new TaskSamplePage(Math.max(1, page), Math.max(1, Math.min(100, pageSize)), 0, List.of());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record FilterSql(String whereClause, List<Object> params) {
        int paramCount() {
            return params.size();
        }
    }
}
