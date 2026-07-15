package com.yr.perftest.platform.seed;

import java.util.List;

public record CreateSeedCaptureStrategyRequest(
        String name,
        Long datasourceId,
        List<String> includes,
        List<String> excludes,
        Integer threadCount,
        Integer batchRows
) {
}
