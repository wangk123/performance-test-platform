package com.yr.perftest.platform.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.execution.ExecutionValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 报告对比（模块 06 增强）：基线 vs 目标计划报告，标签级指标差异 + 总体差异。
 * 比较算法为纯函数（{@link #buildComparison}），可独立单测。
 */
@Service
public class ReportCompareService {
    private final ReportDataService reportDataService;
    private final PersistentReportCompareRepository compareRepository;
    private final ObjectMapper objectMapper;

    public ReportCompareService(
            ReportDataService reportDataService,
            PersistentReportCompareRepository compareRepository,
            ObjectMapper objectMapper
    ) {
        this.reportDataService = reportDataService;
        this.compareRepository = compareRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ComparisonView compare(long basePlanId, long targetPlanId, String createdBy) {
        PlanReportResponse base = reportDataService.aggregateByPlan(basePlanId);
        PlanReportResponse target = reportDataService.aggregateByPlan(targetPlanId);
        ComparisonData data = buildComparison(base, target);
        PersistentReportCompareRecord record = compareRepository.save(new PersistentReportCompareRecord(
                basePlanId,
                targetPlanId,
                toJson(data),
                createdBy
        ));
        return view(record, data);
    }

    @Transactional(readOnly = true)
    public ComparisonView get(long compareId) {
        PersistentReportCompareRecord record = compareRepository.findById(compareId)
                .orElseThrow(() -> new ExecutionValidationException(
                        "report compare " + compareId + " does not exist"));
        return view(record, parse(record.getSummaryJson()));
    }

    static ComparisonData buildComparison(PlanReportResponse base, PlanReportResponse target) {
        Map<String, PlanReportResponse.AggregateRow> baseRows = flatten(base);
        Map<String, PlanReportResponse.AggregateRow> targetRows = flatten(target);
        List<RowDelta> rows = new ArrayList<>();
        for (Map.Entry<String, PlanReportResponse.AggregateRow> entry : baseRows.entrySet()) {
            PlanReportResponse.AggregateRow targetRow = targetRows.get(entry.getKey());
            if (targetRow == null) {
                continue;
            }
            PlanReportResponse.AggregateRow baseRow = entry.getValue();
            rows.add(new RowDelta(
                    entry.getKey(),
                    baseRow.p95(),
                    targetRow.p95(),
                    deltaPct(baseRow.p95(), targetRow.p95()),
                    deltaPct(baseRow.average(), targetRow.average()),
                    deltaPct(baseRow.throughput(), targetRow.throughput()),
                    round4(targetRow.errorRate() - baseRow.errorRate())
            ));
        }
        OverallDelta overall = overallDelta(baseRows, targetRows);
        return new ComparisonData(overall, List.copyOf(rows));
    }

    private static OverallDelta overallDelta(
            Map<String, PlanReportResponse.AggregateRow> base,
            Map<String, PlanReportResponse.AggregateRow> target
    ) {
        long baseSamples = base.values().stream().mapToLong(PlanReportResponse.AggregateRow::samples).sum();
        long targetSamples = target.values().stream().mapToLong(PlanReportResponse.AggregateRow::samples).sum();
        double baseP95 = weighted(base.values(), PlanReportResponse.AggregateRow::p95);
        double targetP95 = weighted(target.values(), PlanReportResponse.AggregateRow::p95);
        double baseAvg = weighted(base.values(), PlanReportResponse.AggregateRow::average);
        double targetAvg = weighted(target.values(), PlanReportResponse.AggregateRow::average);
        double baseTps = base.values().stream().mapToDouble(PlanReportResponse.AggregateRow::throughput).sum();
        double targetTps = target.values().stream().mapToDouble(PlanReportResponse.AggregateRow::throughput).sum();
        double baseError = baseSamples == 0 ? 0
                : base.values().stream().mapToDouble(row -> row.errorRate() * row.samples()).sum() / baseSamples;
        double targetError = targetSamples == 0 ? 0
                : target.values().stream().mapToDouble(row -> row.errorRate() * row.samples()).sum() / targetSamples;
        return new OverallDelta(
                baseSamples,
                targetSamples,
                deltaPct(baseAvg, targetAvg),
                deltaPct(baseP95, targetP95),
                deltaPct(baseTps, targetTps),
                round4(targetError - baseError)
        );
    }

    private static Map<String, PlanReportResponse.AggregateRow> flatten(PlanReportResponse report) {
        Map<String, PlanReportResponse.AggregateRow> rows = new TreeMap<>();
        if (report.scenarios() == null) {
            return rows;
        }
        for (PlanReportResponse.ScenarioReport scenario : report.scenarios()) {
            if (scenario.presets() == null) {
                continue;
            }
            for (PlanReportResponse.PresetReport preset : scenario.presets()) {
                if (preset.aggregateRows() == null) {
                    continue;
                }
                for (PlanReportResponse.AggregateRow row : preset.aggregateRows()) {
                    rows.putIfAbsent(row.label(), row);
                }
            }
        }
        return rows;
    }

    private static double weighted(
            Iterable<PlanReportResponse.AggregateRow> rows,
            java.util.function.ToLongFunction<PlanReportResponse.AggregateRow> metric
    ) {
        long samples = 0;
        double total = 0;
        for (PlanReportResponse.AggregateRow row : rows) {
            samples += row.samples();
            total += metric.applyAsLong(row) * row.samples();
        }
        return samples == 0 ? 0 : total / samples;
    }

    private static Double deltaPct(double first, double second) {
        if (first == 0) {
            return second == 0 ? 0.0 : null;
        }
        return round4((second - first) / first * 100.0);
    }

    private static double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private String toJson(ComparisonData data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception exception) {
            throw new IllegalStateException("cannot serialize report compare", exception);
        }
    }

    private ComparisonData parse(String json) {
        try {
            return objectMapper.readValue(json, ComparisonData.class);
        } catch (Exception exception) {
            throw new IllegalStateException("cannot read report compare", exception);
        }
    }

    private ComparisonView view(PersistentReportCompareRecord record, ComparisonData data) {
        return new ComparisonView(
                record.getId(),
                record.getBasePlanId(),
                record.getTargetPlanId(),
                data.overall(),
                data.rows(),
                record.getCreatedBy(),
                record.getCreatedAt()
        );
    }

    public record RowDelta(
            String label,
            long baseP95,
            long targetP95,
            Double p95DeltaPct,
            Double avgRtDeltaPct,
            Double throughputDeltaPct,
            Double errorRateDelta
    ) {
    }

    public record OverallDelta(
            long baseSamples,
            long targetSamples,
            Double avgRtDeltaPct,
            Double p95DeltaPct,
            Double throughputDeltaPct,
            Double errorRateDelta
    ) {
    }

    public record ComparisonData(OverallDelta overall, List<RowDelta> rows) {
    }

    public record ComparisonView(
            long compareId,
            long basePlanId,
            long targetPlanId,
            OverallDelta overall,
            List<RowDelta> rows,
            String createdBy,
            Instant createdAt
    ) {
    }
}
