package com.yr.perftest.platform.execution;

import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
public class JmeterResultRetainer {
    private static final int FAILURE_SAMPLE_LIMIT = 1000;

    public void retainRecentFailures(Path resultPath, Path failureResultPath) {
        if (resultPath == null || !Files.exists(resultPath)) {
            return;
        }
        try {
            List<String> records = parseCsvRecords(Files.readString(resultPath));
            if (records.size() < 2) {
                return;
            }
            List<String> header = parseCsvLine(records.get(0));
            Map<String, Integer> indexes = IntStream.range(0, header.size())
                    .boxed()
                    .collect(Collectors.toMap(header::get, index -> index, (left, right) -> left));
            Integer successIndex = indexes.get("success");
            if (successIndex == null) {
                return;
            }
            List<String> failures = records.stream()
                    .skip(1)
                    .filter(record -> isFailure(record, successIndex))
                    .toList();
            List<String> retained = failures.size() > FAILURE_SAMPLE_LIMIT
                    ? failures.subList(failures.size() - FAILURE_SAMPLE_LIMIT, failures.size())
                    : failures;
            List<String> output = new ArrayList<>();
            output.add(records.get(0));
            output.addAll(retained);
            Files.writeString(failureResultPath, String.join("\n", output) + "\n");
        } catch (Exception ignored) {
        }
    }

    private boolean isFailure(String record, int successIndex) {
        List<String> values = parseCsvLine(record);
        return successIndex < values.size() && "false".equalsIgnoreCase(values.get(successIndex));
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

    private List<String> parseCsvRecords(String content) {
        List<String> records = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < content.length(); i++) {
            char value = content.charAt(i);
            if (value == '"') {
                current.append(value);
                if (quoted && i + 1 < content.length() && content.charAt(i + 1) == '"') {
                    current.append(content.charAt(i + 1));
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if ((value == '\n' || value == '\r') && !quoted) {
                if (!current.isEmpty()) {
                    records.add(current.toString());
                    current.setLength(0);
                }
                if (value == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                    i++;
                }
            } else {
                current.append(value);
            }
        }
        if (!current.isEmpty()) {
            records.add(current.toString());
        }
        return records;
    }
}
