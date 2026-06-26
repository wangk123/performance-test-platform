package com.yr.perftest.platform.execution;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FailureSampleSettings {
    private final int perLabelLimit;
    private final int globalLimit;
    private final long tailIntervalMs;

    public FailureSampleSettings(
            @Value("${platform.execution.failure-sample.per-label-limit:50}") int perLabelLimit,
            @Value("${platform.execution.failure-sample.global-limit:1000}") int globalLimit,
            @Value("${platform.execution.failure-sample.tail-interval-ms:5000}") long tailIntervalMs
    ) {
        this.perLabelLimit = perLabelLimit;
        this.globalLimit = globalLimit;
        this.tailIntervalMs = tailIntervalMs;
    }

    public int perLabelLimit() {
        return perLabelLimit;
    }

    public int globalLimit() {
        return globalLimit;
    }

    public long tailIntervalMs() {
        return tailIntervalMs;
    }
}
