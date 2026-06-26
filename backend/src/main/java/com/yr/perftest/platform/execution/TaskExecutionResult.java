package com.yr.perftest.platform.execution;

import java.util.List;

public record TaskExecutionResult(
        Summary summary,
        List<AggregateRow> aggregateRows,
        List<Sample> samples
) {
    public static TaskExecutionResult empty() {
        return new TaskExecutionResult(
                new Summary(0, 0, 0, 0, 0, null),
                List.of(),
                List.of()
        );
    }

    public TaskExecutionResult withAccuracy(String accuracy) {
        if (summary == null || accuracy == null || accuracy.equals(summary.accuracy())) {
            return this;
        }
        return new TaskExecutionResult(
                new Summary(summary.samples(), summary.throughput(), summary.avgRt(), summary.p95(), summary.errorRate(), accuracy),
                aggregateRows,
                samples
        );
    }

    public record Summary(
            int samples,
            double throughput,
            long avgRt,
            long p95,
            double errorRate,
            String accuracy
    ) {
    }

    public record AggregateRow(
            String label,
            String threadName,
            int samples,
            long average,
            long median,
            long p90,
            long p95,
            long p99,
            long min,
            long max,
            double errorRate,
            double throughput
    ) {
    }

    public record Sample(
            int id,
            String time,
            String statusCode,
            boolean success,
            String label,
            long elapsed,
            String message,
            String threadName,
            String requestLine,
            String requestHeaders,
            String requestBody,
            String responseHeaders,
            String responseBody,
            String failureMessage
    ) {
    }
}
