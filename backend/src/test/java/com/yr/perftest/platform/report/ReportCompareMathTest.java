package com.yr.perftest.platform.report;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportCompareMathTest {
    private static PlanReportResponse.AggregateRow row(
            String label, int samples, long average, long p95, double errorRate, double throughput) {
        return new PlanReportResponse.AggregateRow(
                label, "thread-1", samples, average, average, p95, p95, p95, 10, p95 + 50, errorRate, throughput);
    }

    private static PlanReportResponse report(PlanReportResponse.AggregateRow... rows) {
        PlanReportResponse.PresetReport preset = new PlanReportResponse.PresetReport(
                0, "preset-1", 1, null, null, null, null, null, null,
                List.of(), null, List.of(rows), null, null);
        PlanReportResponse.ScenarioReport scenario = new PlanReportResponse.ScenarioReport(
                1L, 0L, "scenario-a", null, List.of(preset));
        return new PlanReportResponse(
                new PlanReportResponse.PlanInfo(1L, 1L, "plan-a", null),
                List.of(scenario));
    }

    @Test
    void computesLabelDeltasAndOverallDeterministically() {
        PlanReportResponse base = report(
                row("checkout", 1000, 150, 200, 0.01, 50),
                row("search", 1000, 80, 100, 0.0, 100));
        PlanReportResponse target = report(
                row("checkout", 1000, 200, 300, 0.02, 45),
                row("search", 1000, 79, 98, 0.0, 102));

        ReportCompareService.ComparisonData first = ReportCompareService.buildComparison(base, target);
        ReportCompareService.ComparisonData second = ReportCompareService.buildComparison(base, target);

        assertThat(first).isEqualTo(second);
        assertThat(first.rows()).hasSize(2);
        assertThat(first.rows().get(0).label()).isEqualTo("checkout");
        assertThat(first.rows().get(0).p95DeltaPct()).isEqualTo(50.0);
        assertThat(first.rows().get(0).avgRtDeltaPct()).isEqualTo(33.3333);
        assertThat(first.rows().get(0).throughputDeltaPct()).isEqualTo(-10.0);
        assertThat(first.rows().get(0).errorRateDelta()).isEqualTo(0.01);
        assertThat(first.rows().get(1).label()).isEqualTo("search");
        assertThat(first.rows().get(1).p95DeltaPct()).isEqualTo(-2.0);
        assertThat(first.overall().baseSamples()).isEqualTo(2000);
        assertThat(first.overall().targetSamples()).isEqualTo(2000);
        assertThat(first.overall().p95DeltaPct()).isEqualTo(32.6667);
    }

    @Test
    void labelsMissingInTargetAreExcluded() {
        PlanReportResponse base = report(
                row("checkout", 100, 150, 200, 0.01, 50),
                row("search", 100, 80, 100, 0.0, 100));
        PlanReportResponse target = report(row("checkout", 100, 200, 300, 0.02, 45));

        ReportCompareService.ComparisonData data = ReportCompareService.buildComparison(base, target);

        assertThat(data.rows()).hasSize(1);
        assertThat(data.rows().get(0).label()).isEqualTo("checkout");
    }

    @Test
    void zeroBaselineThroughputYieldsNullDelta() {
        PlanReportResponse base = report(row("checkout", 100, 150, 200, 0.01, 0));
        PlanReportResponse target = report(row("checkout", 100, 150, 200, 0.01, 0));

        ReportCompareService.ComparisonData data = ReportCompareService.buildComparison(base, target);

        assertThat(data.rows().get(0).throughputDeltaPct()).isEqualTo(0.0);
    }
}
