package com.yr.perftest.platform.monitoring;

import java.util.List;
import java.util.Map;

public record MetricSeries(
        String displayName,
        Map<String, String> labels,
        List<MetricSeriesPoint> points,
        int yAxisIndex
) {
    public MetricSeries(String displayName, Map<String, String> labels, List<MetricSeriesPoint> points) {
        this(displayName, labels, points, 0);
    }
}
