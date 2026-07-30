package com.yr.perftest.platform.analysis;

import com.yr.perftest.platform.execution.TaskExecutionResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class ExecutionComparison {
    public static final String ALGORITHM_ID = "execution-compare";
    public static final String VERSION = "1";
    private static final double DURATION_TOLERANCE_LOW = 0.8;
    private static final double DURATION_TOLERANCE_HIGH = 1.25;

    public record ExecutionSide(
            long executionId,
            long scenarioId,
            Long durationMs,
            List<TaskExecutionResult.AggregateRow> rows
    ) {
    }

    public AnalysisFact compare(ExecutionSide baseline, ExecutionSide candidate, List<String> evidenceRefs) {
        List<String> reasons = new ArrayList<>();
        if (baseline.scenarioId() != candidate.scenarioId()) {
            reasons.add("different scenario");
        }
        Map<String, TaskExecutionResult.AggregateRow> baselineRows = byLabel(baseline.rows());
        Map<String, TaskExecutionResult.AggregateRow> candidateRows = byLabel(candidate.rows());
        if (!baselineRows.keySet().equals(candidateRows.keySet())) {
            reasons.add("label sets differ");
        }
        if (baseline.durationMs() != null && candidate.durationMs() != null
                && baseline.durationMs() > 0 && candidate.durationMs() > 0) {
            double ratio = (double) candidate.durationMs() / baseline.durationMs();
            if (ratio < DURATION_TOLERANCE_LOW || ratio > DURATION_TOLERANCE_HIGH) {
                reasons.add("duration differs by more than 25%");
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("baselineExecutionId", baseline.executionId());
        data.put("candidateExecutionId", candidate.executionId());
        boolean comparable = reasons.isEmpty();
        data.put("comparable", comparable);
        data.put("reasons", List.copyOf(reasons));
        if (!comparable) {
            data.put("labels", List.of());
            data.put("overallVerdict", "NOT_COMPARABLE");
            return new AnalysisFact(ALGORITHM_ID, VERSION, "execution-compare",
                    "baseline vs candidate per-label diff", data, evidenceRefs);
        }

        List<Map<String, Object>> labels = new ArrayList<>();
        boolean anyRegressed = false;
        boolean anyImproved = false;
        for (Map.Entry<String, TaskExecutionResult.AggregateRow> entry : baselineRows.entrySet()) {
            TaskExecutionResult.AggregateRow base = entry.getValue();
            TaskExecutionResult.AggregateRow cand = candidateRows.get(entry.getKey());
            Double p95DeltaPct = AnalysisMath.deltaPct(base.p95(), cand.p95());
            String verdict = verdict(p95DeltaPct);
            anyRegressed |= "REGRESSED".equals(verdict);
            anyImproved |= "IMPROVED".equals(verdict);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("label", entry.getKey());
            row.put("baselineP95", base.p95());
            row.put("candidateP95", cand.p95());
            row.put("p95DeltaPct", p95DeltaPct);
            row.put("avgRtDeltaPct", AnalysisMath.deltaPct(base.average(), cand.average()));
            row.put("throughputDeltaPct", AnalysisMath.deltaPct(base.throughput(), cand.throughput()));
            row.put("errorRateDelta", AnalysisMath.round4(cand.errorRate() - base.errorRate()));
            row.put("verdict", verdict);
            labels.add(row);
        }
        data.put("labels", List.copyOf(labels));
        data.put("overallVerdict", anyRegressed ? "REGRESSED" : anyImproved ? "IMPROVED" : "STABLE");
        return new AnalysisFact(ALGORITHM_ID, VERSION, "execution-compare",
                "baseline vs candidate per-label diff", data, evidenceRefs);
    }

    private String verdict(Double p95DeltaPct) {
        if (p95DeltaPct == null) {
            return "REGRESSED";
        }
        if (p95DeltaPct > 5.0) {
            return "REGRESSED";
        }
        if (p95DeltaPct < -5.0) {
            return "IMPROVED";
        }
        return "STABLE";
    }

    private Map<String, TaskExecutionResult.AggregateRow> byLabel(List<TaskExecutionResult.AggregateRow> rows) {
        Map<String, TaskExecutionResult.AggregateRow> byLabel = new TreeMap<>();
        if (rows != null) {
            for (TaskExecutionResult.AggregateRow row : rows) {
                if (row != null && row.label() != null) {
                    byLabel.put(row.label(), row);
                }
            }
        }
        return byLabel;
    }
}
