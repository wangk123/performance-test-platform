package com.yr.perftest.platform.evidence;

import com.yr.perftest.platform.facade.query.Availability;

import java.util.Map;

public record EvidenceSummary(
        CorrelationKey key,
        String sourceType,
        Availability availability,
        Map<String, Object> summary,
        String sourceRef,
        String sourceClock
) {
    public EvidenceSummary {
        summary = summary == null ? Map.of() : Map.copyOf(summary);
    }
}
