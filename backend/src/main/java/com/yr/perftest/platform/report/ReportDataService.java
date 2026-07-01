package com.yr.perftest.platform.report;

import com.yr.perftest.platform.execution.ExecutionStatus;
import com.yr.perftest.platform.execution.TaskExecutionResult;
import com.yr.perftest.platform.execution.TaskMetricSeries;
import com.yr.perftest.platform.execution.TaskSamplePage;
import com.yr.perftest.platform.execution.aggregate.PersistentExecutionMetricSeriesRecord;
import com.yr.perftest.platform.script.ScriptDefinition;
import com.yr.perftest.platform.script.ScriptService;
import com.yr.perftest.platform.task.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务计划维度的报告数据聚合服务。
 */
@Service
public class ReportDataService {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;
    private static final int MAX_FAILURE_SAMPLES = 100;

    private final TaskPlanService planService;
    private final ScenarioExecutionService executionService;
    private final ScriptService scriptService;
    private final PersistentTaskScenarioRepository scenarioRepository;
    private final PersistentScenarioExecutionRepository executionRepository;

    public ReportDataService(
            TaskPlanService planService,
            ScenarioExecutionService executionService,
            ScriptService scriptService,
            PersistentTaskScenarioRepository scenarioRepository,
            PersistentScenarioExecutionRepository executionRepository
    ) {
        this.planService = planService;
        this.executionService = executionService;
        this.scriptService = scriptService;
        this.scenarioRepository = scenarioRepository;
        this.executionRepository = executionRepository;
    }

    @Transactional(readOnly = true)
    public PlanReportResponse aggregateByPlan(long planId) {
        TaskPlan plan = planService.getPlan(planId);
        if (plan == null) {
            throw new com.yr.perftest.platform.execution.ExecutionValidationException("task plan does not exist");
        }

        List<PersistentTaskScenarioRecord> scenarios = scenarioRepository.findAllByPlanIdOrderBySortOrderAscIdAsc(planId);

        List<PlanReportResponse.ScenarioReport> scenarioReports = new ArrayList<>();
        for (PersistentTaskScenarioRecord scenario : scenarios) {
            scenarioReports.add(buildScenarioReport(plan.projectId(), scenario));
        }

        return new PlanReportResponse(
                new PlanReportResponse.PlanInfo(plan.id(), plan.projectId(), plan.name(), plan.remark()),
                scenarioReports
        );
    }

    private PlanReportResponse.ScenarioReport buildScenarioReport(
            long projectId, PersistentTaskScenarioRecord scenario) {

        String scriptName = "";
        try {
            ScriptDefinition def = scriptService.getScriptDefinition(projectId, scenario.getScriptVersionId());
            if (def != null) scriptName = def.name();
        } catch (Exception ignored) {}

        List<PersistentScenarioExecutionRecord> executions =
                executionRepository.findAllByScenarioIdOrderByIdDesc(scenario.getId());

        List<PlanReportResponse.RoundReport> rounds = new ArrayList<>();
        for (PersistentScenarioExecutionRecord exec : executions) {
            if (!isFinished(exec.getStatus())) continue;
            rounds.add(buildRoundReport(exec));
        }

        return new PlanReportResponse.ScenarioReport(
                scenario.getId(), scenario.getScriptVersionId(),
                scenario.getName(), scriptName, rounds
        );
    }

    private PlanReportResponse.RoundReport buildRoundReport(PersistentScenarioExecutionRecord exec) {
        long executionId = exec.getId();
        TaskExecutionResult result = executionService.getResult(executionId);
        TaskMetricSeries monitoring = executionService.getMonitoring(executionId);

        // 从 config JSON 或 scenario 获取配置
        int threads = 0, rampUp = 0, duration = 0, loops = 0;
        try {
            ScenarioExecution se = executionService.getExecution(executionId);
            if (se.config() != null) {
                threads = se.config().threads();
                rampUp = se.config().rampUp();
                duration = se.config().duration();
                loops = se.config().loops();
            }
        } catch (Exception ignored) {}

        PlanReportResponse.AggregateSummary summary = buildSummary(result);
        List<PlanReportResponse.AggregateRow> rows = buildAggregateRows(result);
        PlanReportResponse.MetricSeriesData series = buildMetricSeries(monitoring);
        PlanReportResponse.FailureSummary failures = buildFailures(executionId);

        return new PlanReportResponse.RoundReport(
                executionId,
                exec.getExecutionName(),
                exec.getStatus().name(),
                formatInstant(exec.getStartTime()),
                formatInstant(exec.getEndTime()),
                exec.getDurationMs(),
                threads, rampUp, duration, loops,
                summary, rows, series, failures
        );
    }

    // ---- aggregate ----

    private PlanReportResponse.AggregateSummary buildSummary(TaskExecutionResult result) {
        if (result == null || result.summary() == null) {
            return new PlanReportResponse.AggregateSummary(0, 0, 0, 0, 0, "none");
        }
        var s = result.summary();
        return new PlanReportResponse.AggregateSummary(
                s.samples(), s.throughput(), s.avgRt(), s.p95(), s.errorRate(), s.accuracy());
    }

    private List<PlanReportResponse.AggregateRow> buildAggregateRows(TaskExecutionResult result) {
        if (result == null || result.aggregateRows() == null) return List.of();
        return result.aggregateRows().stream()
                .map(r -> new PlanReportResponse.AggregateRow(
                        r.label(), r.threadName(), r.samples(),
                        r.average(), r.median(), r.p90(), r.p95(), r.p99(),
                        r.min(), r.max(), r.errorRate(), r.throughput()))
                .toList();
    }

    // ---- metric series ----

    private PlanReportResponse.MetricSeriesData buildMetricSeries(TaskMetricSeries monitoring) {
        if (monitoring == null || monitoring.ticks() == null || monitoring.ticks().isEmpty()) {
            return new PlanReportResponse.MetricSeriesData(List.of());
        }
        List<PlanReportResponse.MetricTick> ticks = monitoring.ticks().stream()
                .filter(t -> t.overall() != null)
                .map(t -> new PlanReportResponse.MetricTick(
                        t.bucketTimeMs(),
                        new PlanReportResponse.LabelMetric(
                                t.overall().label(), t.overall().samples(),
                                t.overall().errorSamples(), t.overall().throughput(),
                                t.overall().avgRtMs(), t.overall().p95RtMs())))
                .toList();
        return new PlanReportResponse.MetricSeriesData(ticks);
    }

    // ---- failures ----

    private PlanReportResponse.FailureSummary buildFailures(long executionId) {
        TaskSamplePage page = executionService.getSamples(executionId, 1, MAX_FAILURE_SAMPLES, null, null, false);
        if (page == null || page.samples() == null || page.samples().isEmpty()) {
            return new PlanReportResponse.FailureSummary(0, false, List.of());
        }
        List<PlanReportResponse.FailureSample> samples = page.samples().stream()
                .map(s -> new PlanReportResponse.FailureSample(
                        s.id(), s.time(), s.statusCode(), s.success(),
                        s.label(), s.elapsed(), s.message(), s.threadName()))
                .toList();
        return new PlanReportResponse.FailureSummary(page.total(), page.total() > MAX_FAILURE_SAMPLES, samples);
    }

    // ---- helpers ----

    private boolean isFinished(ExecutionStatus status) {
        return status == ExecutionStatus.SUCCESS
                || status == ExecutionStatus.FAILED
                || status == ExecutionStatus.CANCELLED
                || status == ExecutionStatus.INTERRUPTED;
    }

    private static String formatInstant(Instant instant) {
        return instant == null ? null : ISO_FORMATTER.format(instant);
    }
}
