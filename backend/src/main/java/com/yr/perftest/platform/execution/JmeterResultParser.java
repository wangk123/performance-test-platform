package com.yr.perftest.platform.execution;

import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class JmeterResultParser {
    private static final int SAMPLE_LIMIT = 1000;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public TaskExecutionResult parse(Path resultPath) {
        if (resultPath == null || !Files.exists(resultPath)) {
            return TaskExecutionResult.empty();
        }
        try {
            List<String> lines = Files.readAllLines(resultPath);
            if (lines.size() < 2) {
                return TaskExecutionResult.empty();
            }
            Map<String, Integer> header = headerIndexes(parseCsvLine(lines.get(0)));
            List<Row> rows = new ArrayList<>();
            for (int i = 1; i < lines.size(); i++) {
                List<String> values = parseCsvLine(lines.get(i));
                if (!values.isEmpty()) {
                    rows.add(toRow(values, header));
                }
            }
            return rows.isEmpty() ? TaskExecutionResult.empty() : toResult(rows);
        } catch (Exception exception) {
            return TaskExecutionResult.empty();
        }
    }

    private TaskExecutionResult toResult(List<Row> rows) {
        List<Long> elapsedValues = rows.stream().map(Row::elapsed).sorted().toList();
        long totalElapsed = elapsedValues.stream().mapToLong(Long::longValue).sum();
        long minStart = rows.stream().mapToLong(Row::timestamp).min().orElse(0);
        long maxEnd = rows.stream().mapToLong(row -> row.timestamp() + row.elapsed()).max().orElse(minStart);
        double durationSeconds = Math.max(1, (maxEnd - minStart) / 1000.0);
        long failed = rows.stream().filter(row -> !row.success()).count();
        TaskExecutionResult.Summary summary = new TaskExecutionResult.Summary(
                rows.size(),
                round(rows.size() / durationSeconds),
                Math.round((double) totalElapsed / rows.size()),
                percentile(elapsedValues, 0.95),
                round(failed * 100.0 / rows.size())
        );
        List<TaskExecutionResult.MetricPoint> metrics = buildMetrics(rows);
        List<TaskExecutionResult.Sample> samples = rows.stream()
                .limit(SAMPLE_LIMIT)
                .map(this::toSample)
                .toList();
        return new TaskExecutionResult(summary, metrics, samples);
    }

    private List<TaskExecutionResult.MetricPoint> buildMetrics(List<Row> rows) {
        Map<Long, List<Row>> buckets = new LinkedHashMap<>();
        rows.stream()
                .sorted(Comparator.comparingLong(Row::timestamp))
                .forEach(row -> buckets.computeIfAbsent(row.timestamp() / 1000, key -> new ArrayList<>()).add(row));
        return buckets.entrySet().stream()
                .map(entry -> {
                    List<Long> elapsedValues = entry.getValue().stream().map(Row::elapsed).sorted().toList();
                    long totalElapsed = elapsedValues.stream().mapToLong(Long::longValue).sum();
                    long avg = Math.round((double) totalElapsed / elapsedValues.size());
                    return new TaskExecutionResult.MetricPoint(
                            TIME_FORMATTER.format(Instant.ofEpochMilli(entry.getKey() * 1000)),
                            entry.getValue().size(),
                            0,
                            avg,
                            percentile(elapsedValues, 0.90),
                            percentile(elapsedValues, 0.95)
                    );
                })
                .toList();
    }

    private TaskExecutionResult.Sample toSample(Row row) {
        String request = row.url().isBlank() ? row.label() : row.url();
        String response = row.failureMessage().isBlank() ? row.message() : row.failureMessage();
        return new TaskExecutionResult.Sample(
                row.id(),
                row.statusCode(),
                row.success(),
                row.label(),
                row.elapsed(),
                row.message(),
                row.threadName(),
                request,
                response
        );
    }

    private Row toRow(List<String> values, Map<String, Integer> header) {
        return new Row(
                readLong(values, header, "timeStamp"),
                readLong(values, header, "elapsed"),
                read(values, header, "label"),
                read(values, header, "responseCode"),
                read(values, header, "responseMessage"),
                read(values, header, "threadName"),
                Boolean.parseBoolean(read(values, header, "success")),
                read(values, header, "failureMessage"),
                read(values, header, "URL")
        );
    }

    private Map<String, Integer> headerIndexes(List<String> header) {
        Map<String, Integer> indexes = new LinkedHashMap<>();
        for (int i = 0; i < header.size(); i++) {
            indexes.put(header.get(i), i);
        }
        return indexes;
    }

    private String read(List<String> values, Map<String, Integer> header, String name) {
        Integer index = header.get(name);
        return index == null || index >= values.size() ? "" : values.get(index);
    }

    private long readLong(List<String> values, Map<String, Integer> header, String name) {
        try {
            return Long.parseLong(read(values, header, name));
        } catch (Exception exception) {
            return 0;
        }
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char value = line.charAt(i);
            if (value == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (value == ',' && !quoted) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(value);
            }
        }
        values.add(current.toString());
        return values;
    }

    private long percentile(List<Long> sortedValues, double percentile) {
        if (sortedValues.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil(sortedValues.size() * percentile) - 1;
        return sortedValues.get(Math.max(0, Math.min(sortedValues.size() - 1, index)));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record Row(
            long timestamp,
            long elapsed,
            String label,
            String statusCode,
            String message,
            String threadName,
            boolean success,
            String failureMessage,
            String url
    ) {
        int id() {
            return Math.abs((timestamp + ":" + label + ":" + threadName).hashCode());
        }
    }
}
