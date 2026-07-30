package com.yr.perftest.platform.analysis;

import com.yr.perftest.platform.execution.TaskExecutionResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ErrorClustering {
    public static final String ALGORITHM_ID = "error-cluster";
    public static final String VERSION = "1";
    private static final int PATTERN_MAX_LENGTH = 120;

    public AnalysisFact analyze(List<TaskExecutionResult.Sample> samples, List<String> evidenceRefs) {
        List<TaskExecutionResult.Sample> failures = (samples == null ? List.<TaskExecutionResult.Sample>of() : samples)
                .stream()
                .filter(sample -> sample != null && !sample.success())
                .toList();
        Map<String, Integer> clusterCounts = new HashMap<>();
        Map<String, Integer> labelCounts = new HashMap<>();
        for (TaskExecutionResult.Sample sample : failures) {
            String label = normalizeKeyPart(sample.label());
            String statusCode = normalizeKeyPart(sample.statusCode());
            String pattern = patternOf(sample);
            clusterCounts.merge(label + "\n" + statusCode + "\n" + pattern, 1, Integer::sum);
            labelCounts.merge(label, 1, Integer::sum);
        }
        int total = failures.size();

        List<Map<String, Object>> clusters = clusterCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(entry -> {
                    String[] parts = entry.getKey().split("\n", -1);
                    Map<String, Object> cluster = new LinkedHashMap<>();
                    cluster.put("label", parts[0]);
                    cluster.put("statusCode", parts[1]);
                    cluster.put("messagePattern", parts[2]);
                    cluster.put("count", entry.getValue());
                    cluster.put("sharePct", AnalysisMath.round4(entry.getValue() * 100.0 / total));
                    return cluster;
                })
                .toList();
        List<Map<String, Object>> labelContribution = labelCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(entry -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("label", entry.getKey());
                    row.put("count", entry.getValue());
                    row.put("sharePct", AnalysisMath.round4(entry.getValue() * 100.0 / total));
                    return row;
                })
                .toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalFailures", total);
        data.put("clusters", clusters);
        data.put("labelContribution", labelContribution);
        return new AnalysisFact(
                ALGORITHM_ID,
                VERSION,
                "error-cluster",
                "failure clusters by label / status code / normalized message with contribution",
                data,
                evidenceRefs
        );
    }

    private String normalizeKeyPart(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private String patternOf(TaskExecutionResult.Sample sample) {
        String message = sample.failureMessage() != null && !sample.failureMessage().isBlank()
                ? sample.failureMessage()
                : sample.message();
        if (message == null) {
            return "";
        }
        String normalized = message.replaceAll("\\s+", " ").trim().replaceAll("\\d+", "#");
        return normalized.length() > PATTERN_MAX_LENGTH
                ? normalized.substring(0, PATTERN_MAX_LENGTH)
                : normalized;
    }
}
