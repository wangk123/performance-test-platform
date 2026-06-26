package com.yr.perftest.platform.execution.aggregate;

import java.util.List;

public record MetricTick(
        long bucketTimeMs,
        List<LabelMetric> labels,
        LabelMetric overall
) {
    public record LabelMetric(
            String label,
            long samples,
            long errorSamples,
            double throughput,
            long avgRtMs,
            long p95RtMs
    ) {
    }
}
