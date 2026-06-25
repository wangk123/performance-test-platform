package com.yr.perftest.platform.execution;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ExecutionConfig(
        int threads,
        int rampUp,
        int duration,
        int loops,
        Map<String, String> jmeterProperties,
        ExecutionMode mode,
        Long controllerNodeId,
        List<Long> workerNodeIds,
        List<Long> monitorTargetIds
) {
    public ExecutionConfig {
        jmeterProperties = jmeterProperties == null ? Map.of() : Map.copyOf(jmeterProperties);
        mode = mode == null ? ExecutionMode.LOCAL : mode;
        workerNodeIds = workerNodeIds == null ? List.of() : List.copyOf(workerNodeIds);
        monitorTargetIds = monitorTargetIds == null ? List.of() : List.copyOf(monitorTargetIds);
    }
}
