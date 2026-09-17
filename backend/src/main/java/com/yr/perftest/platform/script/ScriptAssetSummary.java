package com.yr.perftest.platform.script;

import java.time.Instant;

public record ScriptAssetSummary(
        long id,
        long projectId,
        String name,
        int latestVersionNo,
        ScriptVersion latestPublished,
        boolean hasDraft,
        Instant draftUpdatedAt,
        int currentScenarioCount,
        int outdatedScenarioCount
) {
}
