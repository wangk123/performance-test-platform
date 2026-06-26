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
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FailureSampleStore {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final Map<String, Connection> connections = new ConcurrentHashMap<>();

    public void initialize(Path dbPath) throws Exception {
        Files.createDirectories(dbPath.getParent());
        Connection connection = openSharedConnection(dbPath);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA synchronous = NORMAL");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS samples (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        external_id INTEGER NOT NULL,
                        host TEXT NOT NULL,
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
                        failure_message TEXT,
                        UNIQUE(host, external_id)
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_samples_label_code_ts ON samples(label, code, ts)");
        }
    }

    public Long insertReturningId(Path dbPath, FailureSampleRecord record) throws SQLException {
        Connection connection = openSharedConnection(dbPath);
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT OR IGNORE INTO samples (
                    external_id, host, ts, label, code, success, elapsed, message, thread_name, url,
                    request_headers, request_body, response_headers, response_body, failure_message
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, record.id());
            statement.setString(2, safe(record.host()));
            statement.setLong(3, record.ts());
            statement.setString(4, record.label());
            statement.setString(5, record.code());
            statement.setInt(6, record.success() ? 1 : 0);
            statement.setLong(7, record.elapsed());
            statement.setString(8, record.message());
            statement.setString(9, record.threadName());
            statement.setString(10, record.url());
            statement.setString(11, record.requestHeaders());
            statement.setString(12, record.requestBody());
            statement.setString(13, record.responseHeaders());
            statement.setString(14, record.responseBody());
            statement.setString(15, record.failureMessage());
            int affected = statement.executeUpdate();
            if (affected <= 0) {
                return null;
            }
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : null;
            }
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
        Connection connection = openSharedConnection(dbPath);
        int total = count(connection, filterSql);
        List<TaskExecutionResult.Sample> samples = new ArrayList<>();
        String sql = """
                SELECT id, ts, label, code, success, elapsed, message, thread_name
                FROM samples
                """ + filterSql.whereClause() + """
                ORDER BY id DESC
                LIMIT ? OFFSET ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
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
        Connection connection = openSharedConnection(dbPath);
        try (PreparedStatement statement = connection.prepareStatement("""
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
        Connection connection = openSharedConnection(dbPath);
        try (PreparedStatement statement = connection.prepareStatement("""
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

    public List<TaskExecutionResult.Sample> listDetailsAfter(Path dbPath, long lastEventId, int limit) throws Exception {
        if (!Files.exists(dbPath)) {
            return List.of();
        }
        List<TaskExecutionResult.Sample> samples = new ArrayList<>();
        Connection connection = openSharedConnection(dbPath);
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, ts, label, code, success, elapsed, message, thread_name, url,
                       request_headers, request_body, response_headers, response_body, failure_message
                FROM samples
                WHERE id > ?
                ORDER BY id ASC
                LIMIT ?
                """)) {
            statement.setLong(1, lastEventId);
            statement.setInt(2, Math.max(1, limit));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    samples.add(toDetail(resultSet));
                }
            }
        }
        return samples;
    }

    public void closeConnection(Path dbPath) {
        Connection connection = connections.remove(dbPath.toAbsolutePath().toString());
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
        }
    }

    private synchronized Connection openSharedConnection(Path dbPath) throws SQLException {
        String key = dbPath.toAbsolutePath().toString();
        Connection existing = connections.get(key);
        if (existing != null && !existing.isClosed()) {
            return existing;
        }
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + key);
        connection.setAutoCommit(true);
        connections.put(key, connection);
        return connection;
    }

    private int count(Connection connection, FilterSql filterSql) throws SQLException {
        String sql = "SELECT COUNT(1) FROM samples " + filterSql.whereClause();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindFilter(statement, filterSql, 1);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    private TaskExecutionResult.Sample toSummary(ResultSet resultSet) throws SQLException {
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
                ""
        );
    }

    private TaskExecutionResult.Sample toDetail(ResultSet resultSet) throws SQLException {
        String rawRequestBody = safe(resultSet.getString("request_body"));
        String requestLine = FailureSampleNormalizer.extractRequestLine(rawRequestBody, safe(resultSet.getString("url")));
        return new TaskExecutionResult.Sample(
                (int) resultSet.getLong("id"),
                TIME_FORMATTER.format(Instant.ofEpochMilli(resultSet.getLong("ts"))),
                resultSet.getString("code"),
                resultSet.getInt("success") == 1,
                resultSet.getString("label"),
                resultSet.getLong("elapsed"),
                safe(resultSet.getString("message")),
                safe(resultSet.getString("thread_name")),
                requestLine,
                safe(resultSet.getString("request_headers")),
                FailureSampleNormalizer.cleanRequestBody(rawRequestBody),
                FailureSampleNormalizer.cleanResponseHeaders(safe(resultSet.getString("response_headers"))),
                safe(resultSet.getString("response_body")),
                safe(resultSet.getString("failure_message"))
        );
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

    private void bindFilter(PreparedStatement statement, FilterSql filterSql, int startIndex) throws SQLException {
        int index = startIndex;
        for (Object param : filterSql.params()) {
            if (param instanceof String value) {
                statement.setString(index++, value);
            } else if (param instanceof Integer value) {
                statement.setInt(index++, value);
            }
        }
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
