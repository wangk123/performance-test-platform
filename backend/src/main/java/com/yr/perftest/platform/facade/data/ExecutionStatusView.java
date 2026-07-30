package com.yr.perftest.platform.facade.data;

import java.time.Instant;

public record ExecutionStatusView(
        String schemaVersion,
        long executionId,
        String status,
        Instant createdAt,
        Instant startedAt,
        Instant endedAt,
        Long durationMs,
        String errorMessage
) {
    public static final String SCHEMA_VERSION = "1";
}
