package com.yr.perftest.platform.task;

import com.yr.perftest.platform.execution.ExecutionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record TaskScenario(
        long id,
        long planId,
        long scriptVersionId,
        String name,
        int sortOrder,
        int threads,
        int rampUp,
        int duration,
        int loops,
        Map<String, String> jmeterProperties,
        Long controllerNodeId,
        List<Long> workerNodeIds,
        List<Long> monitorTargetIds,
        List<ScenarioThreadGroupConfig> threadGroupConfigs,
        ExecutionStatus latestExecutionStatus,
        Instant latestExecutionAt,
        Instant createdAt,
        Instant updatedAt
) {
}
