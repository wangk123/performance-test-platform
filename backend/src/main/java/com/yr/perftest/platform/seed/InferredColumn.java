package com.yr.perftest.platform.seed;

public record InferredColumn(
        String column,
        FieldRole role,
        Confidence confidence,
        String rationale,
        String refTable,
        String refColumn,
        String suggestedGenerator
) {
}
