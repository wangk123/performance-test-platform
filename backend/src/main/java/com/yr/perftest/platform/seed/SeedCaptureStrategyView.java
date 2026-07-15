package com.yr.perftest.platform.seed;

import java.time.Instant;
import java.util.List;

public record SeedCaptureStrategyView(
        Long id,
        Long projectId,
        String name,
        Long datasourceId,
        List<String> includes,
        List<String> excludes,
        Integer threadCount,
        Integer batchRows,
        Integer configVersion,
        Instant createdAt,
        Instant updatedAt
) {
}
