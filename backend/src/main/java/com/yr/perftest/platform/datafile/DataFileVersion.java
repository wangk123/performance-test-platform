package com.yr.perftest.platform.datafile;

import java.time.LocalDateTime;
import java.util.List;

public record DataFileVersion(
        long id,
        long dataFileId,
        int versionNo,
        String originalFilename,
        String storedPath,
        long sizeBytes,
        Long rowCount,
        List<String> headerColumns,
        String sha256,
        String uploadedBy,
        LocalDateTime uploadedAt,
        String remark
) {
}
