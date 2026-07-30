package com.yr.perftest.platform.analysis;

import com.yr.perftest.platform.execution.aggregate.MetricTick;
import com.yr.perftest.platform.facade.data.PrometheusMetricPoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceSaturationTest {
    private static List<PrometheusMetricPoint> goldenPoints() {
        double[] values = {0.5, 0.9, 0.95, 0.92, 0.4, 0.3};
        List<PrometheusMetricPoint> points = new ArrayList<>();
        for (int index = 0; index < values.length; index++) {
            points.add(new PrometheusMetricPoint(
                    "cpu-app-1",
                    Map.of("instance", "app-1"),
                    index + 1L,
                    values[index],
                    0
            ));
        }
        return points;
    }

    private static List<MetricTick> goldenLoadTicks() {
        double[] throughputs = {10, 8, 6, 6, 12, 14};
        List<MetricTick> ticks = new ArrayList<>();
        for (int index = 0; index < throughputs.length; index++) {
            ticks.add(new MetricTick(
                    1_000L * (index + 1),
                    List.of(),
                    new MetricTick.LabelMetric("__total__", 10, 0, throughputs[index], 100, 120)
            ));
        }
        return ticks;
    }

    @Test
    void goldenDatasetFindsSustainedWindowAndNegativeCorrelation() {
        AnalysisFact first = new ResourceSaturation()
                .analyze(goldenPoints(), 0.9, 2, goldenLoadTicks(), List.of("prometheus:SERVER_CPU?step=15"));
        AnalysisFact second = new ResourceSaturation()
                .analyze(goldenPoints(), 0.9, 2, goldenLoadTicks(), List.of("prometheus:SERVER_CPU?step=15"));

        assertThat(first).isEqualTo(second);
        assertThat(first.algorithmId()).isEqualTo("resource-saturation");
        assertThat(first.algorithmVersion()).isEqualTo("2");
        assertThat(first.data()).containsEntry("threshold", 0.9);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> windows = (List<Map<String, Object>>) first.data().get("windows");
        assertThat(windows).hasSize(1);
        assertThat(windows.get(0))
                .containsEntry("series", "cpu-app-1")
                .containsEntry("fromEpochSec", 2L)
                .containsEntry("toEpochSec", 4L)
                .containsEntry("points", 3)
                .containsEntry("maxValue", 0.95);
        @SuppressWarnings("unchecked")
        Map<String, Object> correlation = (Map<String, Object>) first.data().get("correlation");
        assertThat(correlation).containsEntry("alignedPairs", 6);
        assertThat((Double) correlation.get("pearson")).isBetween(-0.97, -0.96);
    }

    @Test
    void singlePointAboveThresholdIsNotSustained() {
        List<PrometheusMetricPoint> points = List.of(
                new PrometheusMetricPoint("cpu-app-1", Map.of(), 1L, 0.99, 0)
        );

        AnalysisFact fact = new ResourceSaturation().analyze(points, 0.9, 2, List.of(), List.of());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> windows = (List<Map<String, Object>>) fact.data().get("windows");
        assertThat(windows).isEmpty();
    }

    @Test
    void fewerThanThreeAlignedPairsYieldNullCorrelation() {
        List<PrometheusMetricPoint> points = List.of(
                new PrometheusMetricPoint("cpu-app-1", Map.of(), 1L, 0.5, 0),
                new PrometheusMetricPoint("cpu-app-1", Map.of(), 2L, 0.6, 0)
        );
        List<MetricTick> ticks = List.of(
                new MetricTick(1_000L, List.of(), new MetricTick.LabelMetric("__total__", 10, 0, 10, 100, 120)),
                new MetricTick(2_000L, List.of(), new MetricTick.LabelMetric("__total__", 10, 0, 12, 100, 120))
        );

        AnalysisFact fact = new ResourceSaturation().analyze(points, 0.9, 2, ticks, List.of());

        @SuppressWarnings("unchecked")
        Map<String, Object> correlation = (Map<String, Object>) fact.data().get("correlation");
        assertThat(correlation)
                .containsEntry("alignedPairs", 2)
                .containsEntry("pearson", null);
    }

    @Test
    void misalignedTickTimestampsAreResampledOntoTheResourceGrid() {
        List<PrometheusMetricPoint> points = List.of(
                new PrometheusMetricPoint("cpu-app-1", Map.of(), 100L, 0.5, 0),
                new PrometheusMetricPoint("cpu-app-1", Map.of(), 115L, 0.6, 0),
                new PrometheusMetricPoint("cpu-app-1", Map.of(), 130L, 0.7, 0),
                new PrometheusMetricPoint("cpu-app-1", Map.of(), 145L, 0.8, 0)
        );
        List<MetricTick> ticks = List.of(
                tick(103_999L, 10),
                tick(116_500L, 20),
                tick(131_250L, 30),
                tick(138_750L, 30),
                tick(149_900L, 40)
        );

        AnalysisFact fact = new ResourceSaturation().analyze(points, 0.9, 2, ticks, List.of());

        @SuppressWarnings("unchecked")
        Map<String, Object> correlation = (Map<String, Object>) fact.data().get("correlation");
        assertThat(correlation)
                .containsEntry("alignedPairs", 4)
                .containsEntry("pearson", 1.0);
    }

    private static MetricTick tick(long bucketTimeMs, double throughput) {
        return new MetricTick(
                bucketTimeMs,
                List.of(),
                new MetricTick.LabelMetric("__total__", 10, 0, throughput, 100, 120)
        );
    }
}
