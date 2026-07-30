package com.yr.perftest.platform.facade.data;

import java.util.List;

public record ExecutionPrecheckView(
        String schemaVersion,
        boolean valid,
        List<String> errors,
        List<String> warnings,
        Integer threads,
        Integer durationSeconds,
        Integer workerCount,
        Integer monitorTargetCount,
        Long queueAhead,
        List<NodeView> nodes
) {
    public static final String SCHEMA_VERSION = "1";

    public record NodeView(long nodeId, String name, String role, String status, String message) {
    }
}
