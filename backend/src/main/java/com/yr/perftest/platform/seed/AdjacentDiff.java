package com.yr.perftest.platform.seed;

import java.util.List;
import java.util.Objects;

public record AdjacentDiff(
        int intervalIndex,
        SampleSnapshotView before,
        SampleSnapshotView after,
        List<TableDiff> tableDiffs
) {
    public AdjacentDiff {
        if (intervalIndex < 0) {
            throw new IllegalArgumentException("interval index must not be negative");
        }
        before = Objects.requireNonNull(before, "before");
        after = Objects.requireNonNull(after, "after");
        tableDiffs = List.copyOf(Objects.requireNonNull(tableDiffs, "tableDiffs"));
    }

    public long beforeSampleId() {
        return before.sampleId();
    }

    public long afterSampleId() {
        return after.sampleId();
    }
}
