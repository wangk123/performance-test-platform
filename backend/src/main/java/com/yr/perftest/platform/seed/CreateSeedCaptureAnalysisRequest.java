package com.yr.perftest.platform.seed;

import java.util.List;

public record CreateSeedCaptureAnalysisRequest(
        Long strategyId,
        List<Long> sampleIds,
        Boolean confirmIncomplete
) {
}
