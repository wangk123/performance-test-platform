package com.yr.perftest.platform.script;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptBackfillCalculatorTest {
    private final Instant now = Instant.parse("2026-09-17T00:00:00Z");

    @Test
    void buildsOneShellPerLegacyRowWithStrippedName() {
        ScriptBackfillCalculator calculator = new ScriptBackfillCalculator();
        List<ScriptBackfillCalculator.BackfillPlan> plans = calculator.backfillPlans(List.of(
                new ScriptBackfillCalculator.LegacyVersionRow(11L, 1L, "loan-search.jmx", 3, "admin", now),
                new ScriptBackfillCalculator.LegacyVersionRow(12L, 1L, "Order-API.JMX", 7, "admin", now)
        ));
        assertThat(plans).extracting(ScriptBackfillCalculator.BackfillPlan::scriptName)
                .containsExactly("loan-search", "order-api");
        assertThat(plans).extracting(ScriptBackfillCalculator.BackfillPlan::latestVersionNo)
                .containsExactly(3, 7);
    }

    @Test
    void deduplicatesDuplicateNamesByVersionRowId() {
        ScriptBackfillCalculator calculator = new ScriptBackfillCalculator();
        List<ScriptBackfillCalculator.BackfillPlan> plans = calculator.backfillPlans(List.of(
                new ScriptBackfillCalculator.LegacyVersionRow(11L, 1L, "dup.jmx", 1, "admin", now),
                new ScriptBackfillCalculator.LegacyVersionRow(12L, 1L, "dup.jmx", 2, "admin", now)
        ));
        assertThat(plans).extracting(ScriptBackfillCalculator.BackfillPlan::scriptName)
                .containsExactly("dup", "dup-12");
    }

    @Test
    void nameIsTruncatedTo200CharsForColumnLimit() {
        ScriptBackfillCalculator calculator = new ScriptBackfillCalculator();
        String longName = "x".repeat(260);
        List<ScriptBackfillCalculator.BackfillPlan> plans = calculator.backfillPlans(List.of(
                new ScriptBackfillCalculator.LegacyVersionRow(11L, 1L, longName + ".jmx", 1, "admin", now)
        ));
        assertThat(plans.get(0).scriptName()).hasSize(200);
    }
}
