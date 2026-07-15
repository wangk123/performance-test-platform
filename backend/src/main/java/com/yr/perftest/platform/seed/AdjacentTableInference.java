package com.yr.perftest.platform.seed;

import java.util.List;
import java.util.Objects;

public record AdjacentTableInference(
        String table,
        Confidence confidence,
        String rationale,
        double coverage,
        boolean risky,
        List<AdjacentOperationInference> operations,
        List<String> risks
) {
    public AdjacentTableInference {
        table = Objects.requireNonNull(table, "table");
        confidence = Objects.requireNonNull(confidence, "confidence");
        rationale = Objects.requireNonNull(rationale, "rationale");
        operations = List.copyOf(Objects.requireNonNull(operations, "operations"));
        risks = List.copyOf(Objects.requireNonNull(risks, "risks"));
        if (coverage < 0 || coverage > 1 || Double.isNaN(coverage)) {
            throw new IllegalArgumentException("coverage must be between zero and one");
        }
    }
}
