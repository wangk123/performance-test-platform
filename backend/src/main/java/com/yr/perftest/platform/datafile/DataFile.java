package com.yr.perftest.platform.datafile;

import java.time.LocalDateTime;

public record DataFile(
        long id,
        long projectId,
        String name,
        String remark,
        String createdBy,
        LocalDateTime createdAt,
        DataFileVersion latestVersion
) {
}
