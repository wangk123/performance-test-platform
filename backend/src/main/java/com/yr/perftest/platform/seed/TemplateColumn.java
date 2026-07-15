package com.yr.perftest.platform.seed;

public record TemplateColumn(
        String name,
        FieldRole role,
        Confidence confidence,
        String rationale,
        String refTable,
        String refColumn,
        String generator,
        boolean lowAccepted
) {
}
