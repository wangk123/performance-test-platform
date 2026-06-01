package com.yr.perftest.platform.script;

import java.time.Instant;

public record ScriptVersion(
        long id,
        long projectId,
        int versionNo,
        String originalFilename,
        String storedPath,
        String uploadedBy,
        Instant uploadedAt
) {
}
