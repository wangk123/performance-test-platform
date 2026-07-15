package com.yr.perftest.platform.seed;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateConfirmValidatorTest {

    @Test
    void rejectsUniqueRegenWithoutGenerator() {
        SeedTemplateDraft draft = new SeedTemplateDraft(List.of(
                new TemplateOperation(
                        "INSERT",
                        "app.users",
                        false,
                        List.of(new TemplateColumn("id", FieldRole.UNIQUE_REGEN, Confidence.HIGH, "uk", null, null, null, false))
                )
        ));
        assertThatThrownBy(() -> TemplateConfirmValidator.validate(draft))
                .isInstanceOf(SeedValidationException.class)
                .hasMessageContaining("generator");
    }

    @Test
    void rejectsUnacceptedLowConfidence() {
        SeedTemplateDraft draft = new SeedTemplateDraft(List.of(
                new TemplateOperation(
                        "INSERT",
                        "app.users",
                        false,
                        List.of(new TemplateColumn("note", FieldRole.LITERAL, Confidence.LOW, "weak", null, null, null, false))
                )
        ));
        assertThatThrownBy(() -> TemplateConfirmValidator.validate(draft))
                .isInstanceOf(SeedValidationException.class)
                .hasMessageContaining("LOW");
    }

    @Test
    void rejectsRiskyNoPk() {
        SeedTemplateDraft draft = new SeedTemplateDraft(List.of(
                new TemplateOperation("INSERT", "app.logs", true, List.of())
        ));
        assertThatThrownBy(() -> TemplateConfirmValidator.validate(draft))
                .isInstanceOf(SeedValidationException.class)
                .hasMessageContaining("primary key");
    }
}
