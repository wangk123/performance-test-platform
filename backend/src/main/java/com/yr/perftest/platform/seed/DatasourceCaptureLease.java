package com.yr.perftest.platform.seed;

import java.time.Instant;

public record DatasourceCaptureLease(
        long datasourceId,
        long sampleId,
        Instant acquiredAt,
        Instant heartbeatAt
) {
}
