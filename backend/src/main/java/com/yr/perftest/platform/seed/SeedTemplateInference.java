package com.yr.perftest.platform.seed;

final class SeedTemplateInference {
    private SeedTemplateInference() {
    }

    static TemplateColumn toColumn(InferredColumn inferred) {
        return new TemplateColumn(
                inferred.column(),
                inferred.role(),
                inferred.confidence(),
                inferred.rationale(),
                inferred.refTable(),
                inferred.refColumn(),
                inferred.suggestedGenerator(),
                false
        );
    }

}
