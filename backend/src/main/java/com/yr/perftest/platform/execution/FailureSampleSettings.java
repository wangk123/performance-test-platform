package com.yr.perftest.platform.execution;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FailureSampleSettings {
    private final int perLabelLimit;
    private final long tailIntervalMs;

    public FailureSampleSettings(
            @Value("${platform.execution.failure-sample.per-label-limit:10}") int perLabelLimit,
            @Value("${platform.execution.failure-sample.tail-interval-ms:5000}") long tailIntervalMs
    ) {
        this.perLabelLimit = perLabelLimit;
        this.tailIntervalMs = tailIntervalMs;
    }

    public int perLabelLimit() {
        return perLabelLimit;
    }

    public long tailIntervalMs() {
        return tailIntervalMs;
    }
}
