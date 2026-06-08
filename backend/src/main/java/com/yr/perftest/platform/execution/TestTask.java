package com.yr.perftest.platform.execution;

import java.time.Instant;

public record TestTask(
        long id,
        long executionId,
        long projectId,
        long scriptVersionId,
        String name,
        ExecutionStatus status,
        ExecutionConfig config,
        String remark,
        String createdBy,
        Instant createdAt,
        Instant startedAt,
        Instant endedAt,
        Long durationMs,
        String resultFilePath,
        String logFilePath,
        String errorMessage
) {
}
