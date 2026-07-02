package com.yr.perftest.platform.task;

public record ThreadGroupConfigSummary(
        int samples,
        double throughput,
        long avgRt,
        double errorRate
) {
}
