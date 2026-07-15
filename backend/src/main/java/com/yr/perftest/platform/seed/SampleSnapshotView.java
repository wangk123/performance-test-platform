package com.yr.perftest.platform.seed;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record SampleSnapshotView(
        long sampleId,
        long strategyId,
        int sampleSeq,
        Instant captureStartedAt,
        int configVersion,
        String status,
        boolean incomplete,
        Map<String, TableSnapshotView> tables
) {
    private static final Set<String> TERMINAL_STATUSES = Set.of(
            "SUCCEEDED",
            "FAILED",
            "CANCELED",
            "INTERRUPTED"
    );

    public SampleSnapshotView {
        if (sampleId < 0 || strategyId < 0 || sampleSeq < 0) {
            throw new IllegalArgumentException("sample identifiers must not be negative");
        }
        captureStartedAt = Objects.requireNonNull(captureStartedAt, "captureStartedAt");
        status = Objects.requireNonNull(status, "status");
        Map<String, TableSnapshotView> copy = new LinkedHashMap<>(
                Objects.requireNonNull(tables, "tables")
        );
        tables = Collections.unmodifiableMap(copy);
    }

    public boolean terminal() {
        return TERMINAL_STATUSES.contains(status);
    }
}
