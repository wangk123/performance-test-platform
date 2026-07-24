package com.yr.perftest.platform.facade.query;

import java.time.Instant;

public record Availability(
        boolean present,
        Instant from,
        Instant to,
        String granularity,
        boolean truncated,
        String sourceRef,
        MissingReason missingReason
) {
    public enum MissingReason {
        SOURCE_UNAVAILABLE,
        NO_DATA,
        DELETED
    }
}
