package com.yr.perftest.platform.facade.data;

import com.yr.perftest.platform.execution.ExecutionStatus;

import java.time.Instant;

public record ExecutionSummary(
        String schemaVersion,
        long executionId,
        long scenarioId,
        long planId,
        long projectId,
        String scenarioName,
        String executionName,
        ExecutionStatus status,
        Instant createdAt,
        Instant startedAt,
        Instant endedAt,
        Long durationMs,
        int samples,
        double throughput,
        long avgRtMs,
        long p95RtMs,
        double errorRate
) {
    public static final String SCHEMA_VERSION = "1";
}
