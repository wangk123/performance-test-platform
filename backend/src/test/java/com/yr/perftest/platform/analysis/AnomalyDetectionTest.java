package com.yr.perftest.platform.analysis;

import com.yr.perftest.platform.execution.aggregate.MetricTick;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AnomalyDetectionTest {
    private static List<MetricTick> ticks(long... avgRts) {
        List<MetricTick> ticks = new ArrayList<>();
        for (int index = 0; index < avgRts.length; index++) {
            long avgRt = avgRts[index];
            ticks.add(new MetricTick(
                    1_000L * (index + 1),
                    List.of(),
                    new MetricTick.LabelMetric("__total__", 10, 0, 50.0, avgRt, avgRt + 20)
            ));
        }
        return ticks;
    }

    @Test
    void goldenSpikeProducesOneAnomalyIntervalDeterministically() {
        List<MetricTick> golden = ticks(100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 2000);

        AnalysisFact first = new AnomalyDetection().analyze(golden, List.of("metric-series#1000-11000"));
        AnalysisFact second = new AnomalyDetection().analyze(golden, List.of("metric-series#1000-11000"));

        assertThat(first).isEqualTo(second);
        assertThat(first.algorithmId()).isEqualTo("anomaly");
        assertThat(first.algorithmVersion()).isEqualTo("1");
        assertThat((Double) first.data().get("mean")).isCloseTo(272.7273, within(0.001));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> intervals = (List<Map<String, Object>>) first.data().get("intervals");
        assertThat(intervals).hasSize(1);
        assertThat(intervals.get(0))
                .containsEntry("fromMs", 11_000L)
                .containsEntry("toMs", 11_000L)
                .containsEntry("points", 1)
                .containsEntry("maxAvgRtMs", 2_000L);
    }

    @Test
    void goldenStepChangeProducesKneePointWithoutAnomaly() {
        List<MetricTick> golden = ticks(100, 100, 100, 100, 100, 100, 300, 300, 300, 300, 300, 300);

        AnalysisFact fact = new AnomalyDetection().analyze(golden, List.of());

        assertThat(fact.data().get("kneePointMs")).isEqualTo(7_000L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> intervals = (List<Map<String, Object>>) fact.data().get("intervals");
        assertThat(intervals).isEmpty();
    }

    @Test
    void shortSeriesHasNoKneePoint() {
        AnalysisFact fact = new AnomalyDetection().analyze(ticks(100, 200, 400), List.of());

        assertThat(fact.data().get("kneePointMs")).isNull();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> intervals = (List<Map<String, Object>>) fact.data().get("intervals");
        assertThat(intervals).isEmpty();
    }

    @Test
    void flatSeriesHasNoAnomalyAndNoKnee() {
        AnalysisFact fact = new AnomalyDetection().analyze(ticks(100, 100, 100, 100, 100, 100), List.of());

        assertThat(fact.data().get("kneePointMs")).isNull();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> intervals = (List<Map<String, Object>>) fact.data().get("intervals");
        assertThat(intervals).isEmpty();
    }
}
