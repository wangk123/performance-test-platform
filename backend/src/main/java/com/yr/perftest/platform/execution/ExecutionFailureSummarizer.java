package com.yr.perftest.platform.execution;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ExecutionFailureSummarizer {
    private ExecutionFailureSummarizer() {
    }

    public static String summarize(String fallback, Path logPath) {
        String log = readLog(logPath);
        String combined = joinNonBlank(fallback, log);
        if (combined.isBlank()) {
            return "JMeter execution failed";
        }
        for (String line : List.of(
                "An error occurred:",
                "Error in NonGUIDriver",
                "Problem loading XML",
                "ExecutionValidationException",
                "ScriptValidationException"
        )) {
            String extracted = extractLineContaining(combined, line);
            if (extracted != null) {
                return trim(extracted);
            }
        }
        List<String> lines = meaningfulLines(combined);
        if (!lines.isEmpty()) {
            return trim(lines.get(lines.size() - 1));
        }
        return trim(combined);
    }

    private static String readLog(Path logPath) {
        if (logPath == null || !Files.exists(logPath)) {
            return "";
        }
        try {
            return Files.readString(logPath);
        } catch (Exception exception) {
            return "";
        }
    }

    private static String joinNonBlank(String left, String right) {
        if (left == null || left.isBlank()) {
            return right == null ? "" : right;
        }
        if (right == null || right.isBlank()) {
            return left;
        }
        return left + "\n" + right;
    }

    private static String extractLineContaining(String content, String marker) {
        for (String line : content.split("\\R")) {
            if (line.contains(marker)) {
                return line.trim();
            }
        }
        return null;
    }

    private static List<String> meaningfulLines(String content) {
        List<String> lines = new ArrayList<>();
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            if (trimmed.startsWith("jmeter args=") || trimmed.startsWith("jmeter ALL ARGS=")) {
                continue;
            }
            if (trimmed.startsWith("JVM_ARGS=") || trimmed.startsWith("START Running Jmeter")) {
                continue;
            }
            lines.add(trimmed);
        }
        return lines;
    }

    private static String trim(String value) {
        return value.length() > 2000 ? value.substring(0, 2000) : value;
    }
}
