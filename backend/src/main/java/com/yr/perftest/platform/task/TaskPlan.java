package com.yr.perftest.platform.task;

import java.time.Instant;
import java.util.List;

public record TaskPlan(
        long id,
        long projectId,
        String name,
        String remark,
        String createdBy,
        Instant createdAt,
        Instant updatedAt,
        Long defaultControllerNodeId,
        List<Long> defaultWorkerNodeIds,
        List<Long> defaultMonitorTargetIds,
        long scenarioCount
) {
}
