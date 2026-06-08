package com.yr.perftest.platform.script;

import java.time.Instant;
import java.util.List;

public record ScriptDefinition(
        long id,
        long projectId,
        String name,
        String sourceFile,
        int latestVersion,
        String parseStatus,
        String remark,
        Instant updatedAt,
        List<ScriptStepDefinition> steps,
        List<ScriptVersion> versions
) {
}
