package com.yr.perftest.platform.seed;

import java.util.List;
import java.util.Objects;

public record DiffChainResult(
        List<SampleSnapshotView> orderedSamples,
        List<AdjacentDiff> intervals,
        List<DiffWarning> warnings
) {
    public DiffChainResult {
        orderedSamples = List.copyOf(Objects.requireNonNull(orderedSamples, "orderedSamples"));
        intervals = List.copyOf(Objects.requireNonNull(intervals, "intervals"));
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
    }

    public boolean hasWarning(DiffWarning.Code code) {
        return warnings.stream().anyMatch(warning -> warning.code() == code);
    }
}
