package com.yr.perftest.platform.evidence;

import java.time.Instant;
import java.util.List;

public record CorrelationKey(
        long executionId,
        Instant from,
        Instant to,
        List<String> targetInstances,
        String requestLabel,
        String traceId
) {
    public CorrelationKey {
        targetInstances = targetInstances == null ? List.of() : List.copyOf(targetInstances);
    }
}
