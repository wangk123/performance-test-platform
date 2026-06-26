package com.yr.perftest.platform.task;

import com.yr.perftest.platform.execution.ExecutionConfig;
import com.yr.perftest.platform.execution.ExecutionStatus;

import java.time.Instant;

public record ScenarioExecution(
        long id,
        long scenarioId,
        long planId,
        long projectId,
        long scriptVersionId,
        String scenarioName,
        ExecutionStatus status,
        ExecutionConfig config,
        Instant createdAt,
        Instant startedAt,
        Instant endedAt,
        Long durationMs,
        String resultFilePath,
        String logFilePath,
        String errorMessage
) {
}
