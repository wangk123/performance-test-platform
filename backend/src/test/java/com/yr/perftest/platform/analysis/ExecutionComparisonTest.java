package com.yr.perftest.platform.analysis;

import com.yr.perftest.platform.execution.TaskExecutionResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionComparisonTest {
    private static TaskExecutionResult.AggregateRow row(String label, long average, long p95, double errorRate, double throughput) {
        return new TaskExecutionResult.AggregateRow(
                label, "thread", 1000, average, average, p95, p95, p95, 10, p95 + 50, errorRate, throughput
        );
    }

    private static ExecutionComparison.ExecutionSide baseline() {
        return new ExecutionComparison.ExecutionSide(1L, 10L, 60_000L, List.of(
                row("checkout", 150, 200, 0.01, 50),
                row("search", 80, 100, 0, 100)
        ));
    }

    private static ExecutionComparison.ExecutionSide candidate() {
        return new ExecutionComparison.ExecutionSide(2L, 10L, 61_000L, List.of(
                row("checkout", 200, 300, 0.02, 45),
                row("search", 79, 98, 0, 102)
        ));
    }

    @Test
    void goldenPairProducesDeterministicDiff() {
        AnalysisFact first = new ExecutionComparison().compare(baseline(), candidate(), List.of("aggregate#1", "aggregate#2"));
        AnalysisFact second = new ExecutionComparison().compare(baseline(), candidate(), List.of("aggregate#1", "aggregate#2"));

        assertThat(first).isEqualTo(second);
        assertThat(first.algorithmId()).isEqualTo("execution-compare");
        assertThat(first.algorithmVersion()).isEqualTo("1");
        assertThat(first.data())
                .containsEntry("baselineExecutionId", 1L)
                .containsEntry("candidateExecutionId", 2L)
                .containsEntry("comparable", true)
                .containsEntry("overallVerdict", "REGRESSED");
        assertThat(first.data().get("reasons")).isEqualTo(List.of());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> labels = (List<Map<String, Object>>) first.data().get("labels");
        assertThat(labels).hasSize(2);
        assertThat(labels.get(0))
                .containsEntry("label", "checkout")
                .containsEntry("baselineP95", 200L)
                .containsEntry("candidateP95", 300L)
                .containsEntry("p95DeltaPct", 50.0)
                .containsEntry("avgRtDeltaPct", 33.3333)
                .containsEntry("throughputDeltaPct", -10.0)
                .containsEntry("errorRateDelta", 0.01)
                .containsEntry("verdict", "REGRESSED");
        assertThat(labels.get(1))
                .containsEntry("label", "search")
                .containsEntry("p95DeltaPct", -2.0)
                .containsEntry("verdict", "STABLE");
    }

    @Test
    void differentScenarioIsNotComparable() {
        ExecutionComparison.ExecutionSide other = new ExecutionComparison.ExecutionSide(
                3L, 99L, 60_000L, candidate().rows());

        AnalysisFact fact = new ExecutionComparison().compare(baseline(), other, List.of());

        assertThat(fact.data())
                .containsEntry("comparable", false)
                .containsEntry("overallVerdict", "NOT_COMPARABLE");
        assertThat(fact.data().get("reasons")).isEqualTo(List.of("different scenario"));
        assertThat(fact.data().get("labels")).isEqualTo(List.of());
    }

    @Test
    void durationBeyondToleranceIsNotComparable() {
        ExecutionComparison.ExecutionSide shortRun = new ExecutionComparison.ExecutionSide(
                3L, 10L, 30_000L, candidate().rows());

        AnalysisFact fact = new ExecutionComparison().compare(baseline(), shortRun, List.of());

        assertThat(fact.data())
                .containsEntry("comparable", false)
                .containsEntry("overallVerdict", "NOT_COMPARABLE");
        assertThat(fact.data().get("reasons")).isEqualTo(List.of("duration differs by more than 25%"));
    }

    @Test
    void differingLabelSetsAreNotComparable() {
        ExecutionComparison.ExecutionSide missing = new ExecutionComparison.ExecutionSide(
                3L, 10L, 60_000L, List.of(row("checkout", 200, 300, 0.02, 45)));

        AnalysisFact fact = new ExecutionComparison().compare(baseline(), missing, List.of());

        assertThat(fact.data()).containsEntry("comparable", false);
        assertThat(fact.data().get("reasons")).isEqualTo(List.of("label sets differ"));
    }
}
