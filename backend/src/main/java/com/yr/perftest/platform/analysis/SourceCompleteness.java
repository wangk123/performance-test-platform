package com.yr.perftest.platform.analysis;

public record SourceCompleteness(
        String sourceType,
        boolean present,
        boolean truncated,
        String missingReason
) {
}
