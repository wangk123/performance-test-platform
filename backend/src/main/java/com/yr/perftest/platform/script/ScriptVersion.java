package com.yr.perftest.platform.script;

import java.time.Instant;

public record ScriptVersion(
        long id,
        long projectId,
        Long scriptId,
        int versionNo,
        String versionLabel,
        String originalFilename,
        String storedPath,
        String uploadedBy,
        Instant uploadedAt,
        String status,
        String remark
) {
}
