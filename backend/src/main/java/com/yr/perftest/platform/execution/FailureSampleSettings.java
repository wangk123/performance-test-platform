package com.yr.perftest.platform.execution;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FailureSampleSettings {
    private final int perLabelLimit;
    private final int globalLimit;
    private final int detailLimitPerLabel;

    public FailureSampleSettings(
            @Value("${platform.execution.failure-sample.per-label-limit:50}") int perLabelLimit,
            @Value("${platform.execution.failure-sample.global-limit:1000}") int globalLimit,
            @Value("${platform.execution.failure-sample.detail-limit-per-label:10}") int detailLimitPerLabel
    ) {
        this.perLabelLimit = perLabelLimit;
        this.globalLimit = globalLimit;
        this.detailLimitPerLabel = detailLimitPerLabel;
    }

    public int perLabelLimit() {
        return perLabelLimit;
    }

    public int globalLimit() {
        return globalLimit;
    }

    public int detailLimitPerLabel() {
        return detailLimitPerLabel;
    }
}
