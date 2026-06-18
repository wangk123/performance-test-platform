package com.yr.perftest.platform.execution;

import java.util.List;
import java.util.Map;

public record ExecutionConfig(
        int threads,
        int rampUp,
        int duration,
        int loops,
        String environment,
        Map<String, String> jmeterProperties,
        ExecutionMode mode,
        Long controllerNodeId,
        List<Long> workerNodeIds
) {
    public ExecutionConfig {
        jmeterProperties = jmeterProperties == null ? Map.of() : Map.copyOf(jmeterProperties);
        mode = mode == null ? ExecutionMode.LOCAL : mode;
        workerNodeIds = workerNodeIds == null ? List.of() : List.copyOf(workerNodeIds);
    }
}
