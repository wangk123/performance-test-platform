package com.yr.perftest.platform.report;

import java.util.List;

/**
 * 任务计划维度报告数据，包含所有场景及其多轮执行结果。
 */
public record PlanReportResponse(
        PlanInfo plan,
        List<ScenarioReport> scenarios
) {

    public record PlanInfo(
            long planId,
            long projectId,
            String planName,
            String remark
    ) {
    }

    public record ScenarioReport(
            long scenarioId,
            long scriptVersionId,
            String scenarioName,
            String scriptName,
            List<RoundReport> rounds
    ) {
    }

    public record RoundReport(
            long executionId,
            String executionName,
            String status,
            String startedAt,
            String endedAt,
            Long durationMs,
            int threads,
            int rampUp,
            int duration,
            int loops,
            AggregateSummary summary,
            List<AggregateRow> aggregateRows,
            MetricSeriesData metricSeries,
            FailureSummary failures
    ) {
    }

    public record AggregateSummary(
            int samples,
            double throughput,
            long avgRt,
            long p95,
            double errorRate,
            String accuracy
    ) {
    }

    public record AggregateRow(
            String label,
            String threadName,
            int samples,
            long average,
            long median,
            long p90,
            long p95,
            long p99,
            long min,
            long max,
            double errorRate,
            double throughput
    ) {
    }

    public record MetricSeriesData(
            List<MetricTick> ticks
    ) {
    }

    public record MetricTick(
            long bucketTimeMs,
            LabelMetric overall
    ) {
    }

    public record LabelMetric(
            String label,
            long samples,
            long errorSamples,
            double throughput,
            long avgRtMs,
            long p95RtMs
    ) {
    }

    public record FailureSummary(
            int errorCount,
            boolean truncated,
            List<FailureSample> samples
    ) {
    }

    public record FailureSample(
            int id,
            String time,
            String statusCode,
            boolean success,
            String label,
            long elapsed,
            String message,
            String threadName
    ) {
    }
}
