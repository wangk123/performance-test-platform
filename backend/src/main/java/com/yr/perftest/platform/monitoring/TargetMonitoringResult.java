package com.yr.perftest.platform.monitoring;

import java.time.Instant;
import java.util.List;

public record TargetMonitoringResult(
        long taskId,
        long executionId,
        Instant startTime,
        Instant endTime,
        List<ServerSelectable> serverTargets,
        List<JvmInstanceSelectable> jvmInstances,
        List<MonitorTarget> targets
) {
}
