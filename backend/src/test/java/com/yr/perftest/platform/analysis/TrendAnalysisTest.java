package com.yr.perftest.platform.analysis;

import com.yr.perftest.platform.execution.aggregate.MetricTick;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TrendAnalysisTest {
    private static List<MetricTick> goldenTicks() {
        List<MetricTick> ticks = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            long avgRt = index < 5 ? 100 : 200;
            ticks.add(new MetricTick(
                    1_000L * (index + 1),
                    List.of(),
                    new MetricTick.LabelMetric("__total__", 10, 0, 50.0, avgRt, avgRt + 20)
            ));
        }
        return ticks;
    }

    @Test
    void goldenDatasetProducesDeterministicTrend() {
        AnalysisFact first = new TrendAnalysis().analyze(goldenTicks(), List.of("metric-series#1000-10000"));
        AnalysisFact second = new TrendAnalysis().analyze(goldenTicks(), List.of("metric-series#1000-10000"));

        assertThat(first).isEqualTo(second);
        assertThat(first.algorithmId()).isEqualTo("trend");
        assertThat(first.algorithmVersion()).isEqualTo("1");
        assertThat(first.kind()).isEqualTo("trend");
        assertThat(first.evidenceRefs()).containsExactly("metric-series#1000-10000");
        assertThat(first.data()).containsEntry("tickCount", 10);
        @SuppressWarnings("unchecked")
        Map<String, Object> avgRt = (Map<String, Object>) first.data().get("avgRtMs");
        assertThat(avgRt)
                .containsEntry("first", 100.0)
                .containsEntry("second", 200.0)
                .containsEntry("deltaPct", 100.0)
                .containsEntry("direction", "INCREASING");
        @SuppressWarnings("unchecked")
        Map<String, Object> throughput = (Map<String, Object>) first.data().get("throughput");
        assertThat(throughput)
                .containsEntry("first", 50.0)
                .containsEntry("second", 50.0)
                .containsEntry("deltaPct", 0.0)
                .containsEntry("direction", "STABLE");
        @SuppressWarnings("unchecked")
        Map<String, Object> errorRate = (Map<String, Object>) first.data().get("errorRate");
        assertThat(errorRate).containsEntry("direction", "STABLE");
    }

    @Test
    void emptyInputProducesStableZeroTrend() {
        AnalysisFact fact = new TrendAnalysis().analyze(List.of(), List.of());

        assertThat(fact.data()).containsEntry("tickCount", 0);
        @SuppressWarnings("unchecked")
        Map<String, Object> avgRt = (Map<String, Object>) fact.data().get("avgRtMs");
        assertThat(avgRt)
                .containsEntry("first", 0.0)
                .containsEntry("second", 0.0)
                .containsEntry("deltaPct", 0.0)
                .containsEntry("direction", "STABLE");
    }

    @Test
    void unsortedInputIsSortedByBucketTime() {
        List<MetricTick> ticks = goldenTicks();
        List<MetricTick> shuffled = new ArrayList<>();
        for (int index = ticks.size() - 1; index >= 0; index--) {
            shuffled.add(ticks.get(index));
        }

        AnalysisFact fact = new TrendAnalysis().analyze(shuffled, List.of());

        @SuppressWarnings("unchecked")
        Map<String, Object> avgRt = (Map<String, Object>) fact.data().get("avgRtMs");
        assertThat(avgRt).containsEntry("direction", "INCREASING");
    }
}
