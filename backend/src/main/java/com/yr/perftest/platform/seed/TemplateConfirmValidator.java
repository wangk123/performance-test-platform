package com.yr.perftest.platform.seed;

public final class TemplateConfirmValidator {
    private TemplateConfirmValidator() {
    }

    public static void validate(SeedTemplateDraft draft) {
        if (draft == null || draft.operations() == null) {
            throw new SeedValidationException("template is empty");
        }
        for (TemplateOperation op : draft.operations()) {
            if (op.riskyNoPk()) {
                throw new SeedValidationException("table without primary key cannot be confirmed: " + op.table());
            }
            if (op.columns() == null) {
                continue;
            }
            for (TemplateColumn col : op.columns()) {
                if (col.role() == FieldRole.IGNORE) {
                    continue;
                }
                if (col.confidence() == Confidence.LOW && !col.lowAccepted()) {
                    throw new SeedValidationException("LOW confidence must be accepted or changed: " + op.table() + "." + col.name());
                }
                if ((col.role() == FieldRole.UNIQUE_REGEN || col.role() == FieldRole.FORMATTED_RAND)
                        && (col.generator() == null || col.generator().isBlank())) {
                    throw new SeedValidationException("generator is required for " + col.role() + ": " + op.table() + "." + col.name());
                }
            }
        }
    }
}
