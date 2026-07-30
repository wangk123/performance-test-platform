package com.yr.perftest.platform.analysis;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AnalysisReport(
        String schemaVersion,
        long executionId,
        Instant from,
        Instant to,
        Map<String, String> algorithmVersions,
        List<AnalysisFact> facts,
        List<SourceCompleteness> completeness
) {
    public AnalysisReport {
        algorithmVersions = algorithmVersions == null ? Map.of() : Map.copyOf(algorithmVersions);
        facts = facts == null ? List.of() : List.copyOf(facts);
        completeness = completeness == null ? List.of() : List.copyOf(completeness);
    }
}
