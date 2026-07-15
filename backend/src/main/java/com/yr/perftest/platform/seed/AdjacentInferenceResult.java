package com.yr.perftest.platform.seed;

import java.util.List;
import java.util.Objects;

public record AdjacentInferenceResult(
        SeedTemplateDraft templateDraft,
        List<AdjacentTableInference> tables,
        List<String> risks
) {
    public AdjacentInferenceResult {
        templateDraft = Objects.requireNonNull(templateDraft, "templateDraft");
        tables = List.copyOf(Objects.requireNonNull(tables, "tables"));
        risks = List.copyOf(Objects.requireNonNull(risks, "risks"));
    }
}
