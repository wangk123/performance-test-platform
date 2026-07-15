package com.yr.perftest.platform.seed;

import java.util.List;
import java.util.Objects;

public record AdjacentOperationInference(
        String type,
        Confidence confidence,
        String rationale,
        double coverage,
        boolean risky,
        List<InferredColumn> columns
) {
    public AdjacentOperationInference {
        type = Objects.requireNonNull(type, "type");
        confidence = Objects.requireNonNull(confidence, "confidence");
        rationale = Objects.requireNonNull(rationale, "rationale");
        columns = List.copyOf(Objects.requireNonNull(columns, "columns"));
        requireCoverage(coverage);
    }

    private static void requireCoverage(double coverage) {
        if (coverage < 0 || coverage > 1 || Double.isNaN(coverage)) {
            throw new IllegalArgumentException("coverage must be between zero and one");
        }
    }
}
