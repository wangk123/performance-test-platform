package com.yr.perftest.platform.execution;

import java.util.List;

public record TaskExecutionResult(
        Summary summary,
        List<MetricPoint> metrics,
        List<Sample> samples
) {
    public static TaskExecutionResult empty() {
        return new TaskExecutionResult(
                new Summary(0, 0, 0, 0, 0),
                List.of(),
                List.of()
        );
    }

    public record Summary(
            int samples,
            double throughput,
            long avgRt,
            long p95,
            double errorRate
    ) {
    }

    public record MetricPoint(
            String time,
            double tps,
            double targetTps,
            long avgRt,
            long p90,
            long p95
    ) {
    }

    public record Sample(
            int id,
            String statusCode,
            boolean success,
            String label,
            long elapsed,
            String message,
            String threadName,
            String request,
            String response
    ) {
    }
}
