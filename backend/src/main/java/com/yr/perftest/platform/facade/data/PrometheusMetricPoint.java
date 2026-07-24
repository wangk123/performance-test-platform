package com.yr.perftest.platform.facade.data;

import java.util.Map;

public record PrometheusMetricPoint(
        String displayName,
        Map<String, String> labels,
        long timestamp,
        double value,
        int yAxisIndex
) {
}
