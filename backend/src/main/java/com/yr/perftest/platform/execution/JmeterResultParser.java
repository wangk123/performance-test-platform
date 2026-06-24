package com.yr.perftest.platform.execution;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class JmeterResultParser {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public TaskSamplePage parseSamplePage(Path samplePath, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(100, pageSize));
        if (samplePath == null || !Files.exists(samplePath)) {
            return new TaskSamplePage(safePage, safePageSize, 0, List.of());
        }
        try (BufferedReader reader = Files.newBufferedReader(samplePath)) {
            String headerRecord = readRecord(reader);
            if (headerRecord == null) {
                return new TaskSamplePage(safePage, safePageSize, 0, List.of());
            }
            Map<String, Integer> header = headerIndexes(parseCsvLine(headerRecord));
            List<TaskExecutionResult.Sample> samples = new ArrayList<>();
            int offset = (safePage - 1) * safePageSize;
            int total = 0;
            String record;
            while ((record = readRecord(reader)) != null) {
                if (record.isBlank()) {
                    continue;
                }
                if (total >= offset && samples.size() < safePageSize) {
                    samples.add(toSample(toRow(parseCsvLine(record), header)));
                }
                total++;
            }
            return new TaskSamplePage(safePage, safePageSize, total, samples);
        } catch (Exception exception) {
            return new TaskSamplePage(safePage, safePageSize, 0, List.of());
        }
    }

    private TaskExecutionResult.Sample toSample(Row row) {
        String request = joinSections(row.samplerData(), row.requestHeaders(), row.url().isBlank() ? row.label() : row.url());
        String response = joinSections(row.responseHeaders(), row.responseData(), row.failureMessage().isBlank() ? row.message() : row.failureMessage());
        return new TaskExecutionResult.Sample(
                row.id(),
                TIME_FORMATTER.format(Instant.ofEpochMilli(row.timestamp())),
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
                read(values, header, "URL"),
                read(values, header, "samplerData"),
                read(values, header, "requestHeaders"),
                read(values, header, "responseData"),
                read(values, header, "responseHeaders")
        );
    }

    private String joinSections(String first, String second, String fallback) {
        List<String> sections = new ArrayList<>();
        if (!first.isBlank()) {
            sections.add(first);
        }
        if (!second.isBlank()) {
            sections.add(second);
        }
        return sections.isEmpty() ? fallback : String.join("\n\n", sections);
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

    private String readRecord(Reader reader) throws Exception {
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        int raw;
        while ((raw = reader.read()) != -1) {
            char value = (char) raw;
            if (value == '"') {
                current.append(value);
                if (quoted) {
                    reader.mark(1);
                    int next = reader.read();
                    if (next == '"') {
                        current.append((char) next);
                    } else {
                        quoted = false;
                        if (next != -1) {
                            reader.reset();
                        }
                    }
                } else {
                    quoted = true;
                }
            } else if ((value == '\n' || value == '\r') && !quoted) {
                if (value == '\r') {
                    reader.mark(1);
                    int next = reader.read();
                    if (next != '\n' && next != -1) {
                        reader.reset();
                    }
                }
                if (!current.isEmpty()) {
                    return current.toString();
                }
            } else {
                current.append(value);
            }
        }
        return current.isEmpty() ? null : current.toString();
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
            String url,
            String samplerData,
            String requestHeaders,
            String responseData,
            String responseHeaders
    ) {
        int id() {
            return Math.abs((timestamp + ":" + label + ":" + threadName).hashCode());
        }
    }
}
