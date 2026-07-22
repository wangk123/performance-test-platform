package com.yr.perftest.platform.monitoring;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TargetMetricsServiceFilterTest {
    @Test
    void filtersSnapshotSeriesByTargetIdsAndItemId() {
        MetricSeries keep = new MetricSeries(
                "server-a",
                Map.of("target_id", "8", "item_id", "jvm-1"),
                List.of(new MetricSeriesPoint(1L, 0.1)),
                0
        );
        MetricSeries dropTarget = new MetricSeries(
                "server-b",
                Map.of("target_id", "9", "item_id", "jvm-1"),
                List.of(new MetricSeriesPoint(1L, 0.2)),
                0
        );
        MetricSeries dropItem = new MetricSeries(
                "server-a-other",
                Map.of("target_id", "8", "item_id", "jvm-2"),
                List.of(new MetricSeriesPoint(1L, 0.3)),
                0
        );
        TargetMetricsQueryResult snapshot = new TargetMetricsQueryResult(
                MetricKind.JVM_HEAP_PCT,
                MetricKind.JVM_HEAP_PCT.unit(),
                List.of(keep, dropTarget, dropItem)
        );

        TargetMetricsQueryResult filtered = TargetMetricsService.applySeriesFilters(
                snapshot,
                List.of(8L),
                "jvm-1"
        );

        assertEquals(1, filtered.series().size());
        assertEquals("server-a", filtered.series().get(0).displayName());
    }

    @Test
    void keepsAllSeriesWhenFiltersAbsent() {
        MetricSeries a = new MetricSeries("a", Map.of("target_id", "8"), List.of(), 0);
        MetricSeries b = new MetricSeries("b", Map.of("target_id", "9"), List.of(), 0);
        TargetMetricsQueryResult snapshot = new TargetMetricsQueryResult(
                MetricKind.SERVER_CPU,
                MetricKind.SERVER_CPU.unit(),
                List.of(a, b)
        );

        TargetMetricsQueryResult filtered = TargetMetricsService.applySeriesFilters(snapshot, null, null);

        assertEquals(2, filtered.series().size());
    }
}
