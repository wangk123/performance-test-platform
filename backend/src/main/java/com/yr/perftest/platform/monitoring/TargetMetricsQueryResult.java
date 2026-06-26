package com.yr.perftest.platform.monitoring;

import java.util.List;

public record TargetMetricsQueryResult(
        MetricKind kind,
        String unit,
        List<MetricSeries> series
) {
}
