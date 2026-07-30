package com.yr.perftest.platform.facade.data;

public record ExecutionStartResult(
        String schemaVersion,
        long executionId,
        String status,
        boolean replayed
) {
    public static final String SCHEMA_VERSION = "1";
}
