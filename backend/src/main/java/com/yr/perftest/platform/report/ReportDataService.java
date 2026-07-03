package com.yr.perftest.platform.report;

import com.yr.perftest.platform.execution.TaskExecutionResult;
import com.yr.perftest.platform.execution.TaskMetricSeries;
import com.yr.perftest.platform.execution.TaskSamplePage;
import com.yr.perftest.platform.execution.aggregate.MetricTick;
import com.yr.perftest.platform.script.PersistentScriptVersionRecord;
import com.yr.perftest.platform.script.PersistentScriptVersionRepository;
import com.yr.perftest.platform.script.ScriptDefinition;
import com.yr.perftest.platform.script.ScriptService;
import com.yr.perftest.platform.script.ScriptStepDefinition;
import com.yr.perftest.platform.task.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class ReportDataService {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;
    private static final int MAX_FAILURE_SAMPLES = 100;
    private static final PlanReportResponse.AggregateSummary EMPTY_SUMMARY =
            new PlanReportResponse.AggregateSummary(0, 0, 0, 0, 0, "none");

    private final TaskPlanService planService;
    private final ScenarioExecutionService executionService;
    private final ScriptService scriptService;
    private final PersistentTaskScenarioRepository scenarioRepository;
    private final PersistentScenarioExecutionRepository executionRepository;
    private final PersistentScriptVersionRepository scriptVersionRepository;
    private final ScenarioThreadGroupConfigSupport configSupport;

    public ReportDataService(
            TaskPlanService planService,
            ScenarioExecutionService executionService,
            ScriptService scriptService,
            PersistentTaskScenarioRepository scenarioRepository,
            PersistentScenarioExecutionRepository executionRepository,
            PersistentScriptVersionRepository scriptVersionRepository,
            ScenarioThreadGroupConfigSupport configSupport
    ) {
        this.planService = planService;
        this.executionService = executionService;
        this.scriptService = scriptService;
        this.scenarioRepository = scenarioRepository;
        this.executionRepository = executionRepository;
        this.scriptVersionRepository = scriptVersionRepository;
        this.configSupport = configSupport;
    }

    @Transactional(readOnly = true)
    public PlanReportResponse aggregateByPlan(long planId) {
        TaskPlan plan = planService.getPlan(planId);
        if (plan == null) {
            throw new com.yr.perftest.platform.execution.ExecutionValidationException("task plan does not exist");
        }

        List<PersistentTaskScenarioRecord> scenarios =
                scenarioRepository.findAllByPlanIdOrderBySortOrderAscIdAsc(planId);

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
            if (def != null) {
                scriptName = def.name();
            }
        } catch (Exception ignored) {
        }

        List<ScenarioThreadGroupConfig> storedConfigs =
                configSupport.readStored(scenario.getThreadGroupConfigsJson());
        if (storedConfigs.isEmpty()) {
            return new PlanReportResponse.ScenarioReport(
                    scenario.getId(),
                    scenario.getScriptVersionId(),
                    scenario.getName(),
                    scriptName,
                    List.of()
            );
        }

        List<ScriptStepDefinition> scriptSteps = loadScriptSteps(scenario.getScriptVersionId());
        List<PersistentScenarioExecutionRecord> executions =
                executionRepository.findAllByScenarioIdOrderByIdDesc(scenario.getId());

        List<PlanReportResponse.PresetReport> presets = new ArrayList<>();
        int presetIndex = 1;
        for (List<ScenarioThreadGroupConfig> presetRows : configSupport.groupPresetsBySortOrder(storedConfigs)) {
            presets.add(buildPresetReport(
                    presetIndex++,
                    presetRows,
                    scriptSteps,
                    executions
            ));
        }

        return new PlanReportResponse.ScenarioReport(
                scenario.getId(),
                scenario.getScriptVersionId(),
                scenario.getName(),
                scriptName,
                presets
        );
    }

    private PlanReportResponse.PresetReport buildPresetReport(
            int presetIndex,
            List<ScenarioThreadGroupConfig> presetRows,
            List<ScriptStepDefinition> scriptSteps,
            List<PersistentScenarioExecutionRecord> executions
    ) {
        int sortOrder = presetRows.get(0).sortOrder();
        Optional<PersistentScenarioExecutionRecord> latestExecution =
                configSupport.findLatestMatchingExecution(executions, presetRows);

        TaskExecutionResult result = TaskExecutionResult.empty();
        TaskMetricSeries monitoring = TaskMetricSeries.empty();
        if (latestExecution.isPresent()) {
            long executionId = latestExecution.get().getId();
            result = executionService.getResult(executionId);
            monitoring = executionService.getMonitoring(executionId);
        }

        boolean multiThreadGroup = presetRows.size() > 1;
        List<PlanReportResponse.ThreadGroupRowReport> rows = new ArrayList<>();
        List<ThreadGroupConfigSummary> rowSummaries = new ArrayList<>();
        for (ScenarioThreadGroupConfig config : presetRows) {
            PlanReportResponse.AggregateSummary rowSummary = buildRowSummary(
                    scriptSteps,
                    config,
                    presetRows.size(),
                    result
            );
            rows.add(new PlanReportResponse.ThreadGroupRowReport(
                    config.id(),
                    config.stepId(),
                    config.stepName(),
                    config.threads(),
                    config.rampUp(),
                    config.duration(),
                    rowSummary
            ));
            if (rowSummary != null && rowSummary.samples() > 0) {
                rowSummaries.add(new ThreadGroupConfigSummary(
                        rowSummary.samples(),
                        rowSummary.throughput(),
                        rowSummary.avgRt(),
                        rowSummary.errorRate()
                ));
            }
        }

        PlanReportResponse.AggregateSummary presetSummary = null;
        if (multiThreadGroup) {
            ThreadGroupConfigSummary aggregated = configSupport.buildPresetSummary(rowSummaries);
            if (aggregated != null) {
                presetSummary = toAggregateSummary(aggregated, result.summary() != null ? result.summary().accuracy() : "none");
            }
        }

        Set<String> presetLabels = configSupport.collectPresetSamplerLabels(scriptSteps, presetRows);
        List<TaskExecutionResult.AggregateRow> scopedRows =
                configSupport.filterAggregateRows(result.aggregateRows(), presetLabels);
        PlanReportResponse.MetricSeriesData metricSeries = buildMetricSeries(monitoring, presetLabels);

        PlanReportResponse.FailureSummary failures = latestExecution
                .map(execution -> buildFailures(execution.getId(), presetLabels))
                .orElse(new PlanReportResponse.FailureSummary(0, false, List.of()));

        PersistentScenarioExecutionRecord execution = latestExecution.orElse(null);
        return new PlanReportResponse.PresetReport(
                sortOrder,
                "配置 " + presetIndex,
                presetRows.size(),
                execution != null ? execution.getId() : null,
                execution != null ? execution.getExecutionName() : null,
                execution != null ? execution.getStatus().name() : null,
                execution != null ? formatInstant(execution.getStartTime()) : null,
                execution != null ? formatInstant(execution.getEndTime()) : null,
                execution != null ? execution.getDurationMs() : null,
                rows,
                presetSummary,
                buildAggregateRows(scopedRows),
                metricSeries,
                failures
        );
    }

    private PlanReportResponse.AggregateSummary buildRowSummary(
            List<ScriptStepDefinition> scriptSteps,
            ScenarioThreadGroupConfig config,
            int presetSize,
            TaskExecutionResult result
    ) {
        if (result == null || result.summary() == null || result.summary().samples() <= 0) {
            return null;
        }
        String accuracy = result.summary().accuracy();
        if (presetSize > 1 && !scriptSteps.isEmpty()) {
            ThreadGroupConfigSummary scoped = configSupport.summarizeThreadGroupResult(scriptSteps, config, result);
            if (scoped != null) {
                return toAggregateSummary(scoped, accuracy);
            }
        }
        return buildSummary(result);
    }

    private PlanReportResponse.AggregateSummary buildSummary(TaskExecutionResult result) {
        if (result == null || result.summary() == null) {
            return EMPTY_SUMMARY;
        }
        var summary = result.summary();
        return new PlanReportResponse.AggregateSummary(
                summary.samples(),
                summary.throughput(),
                summary.avgRt(),
                summary.p95(),
                summary.errorRate(),
                summary.accuracy()
        );
    }

    private PlanReportResponse.AggregateSummary toAggregateSummary(
            ThreadGroupConfigSummary summary,
            String accuracy
    ) {
        return new PlanReportResponse.AggregateSummary(
                summary.samples(),
                summary.throughput(),
                summary.avgRt(),
                0,
                summary.errorRate(),
                accuracy != null ? accuracy : "none"
        );
    }

    private List<PlanReportResponse.AggregateRow> buildAggregateRows(List<TaskExecutionResult.AggregateRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(row -> new PlanReportResponse.AggregateRow(
                        row.label(),
                        row.threadName(),
                        row.samples(),
                        row.average(),
                        row.median(),
                        row.p90(),
                        row.p95(),
                        row.p99(),
                        row.min(),
                        row.max(),
                        row.errorRate(),
                        row.throughput()))
                .toList();
    }

    private PlanReportResponse.MetricSeriesData buildMetricSeries(
            TaskMetricSeries monitoring,
            Set<String> labels
    ) {
        if (monitoring == null || monitoring.ticks() == null || monitoring.ticks().isEmpty() || labels.isEmpty()) {
            return new PlanReportResponse.MetricSeriesData(List.of());
        }
        List<MetricTick> scopedTicks = configSupport.filterMetricTicks(monitoring.ticks(), labels);
        List<PlanReportResponse.MetricTick> ticks = scopedTicks.stream()
                .map(tick -> new PlanReportResponse.MetricTick(
                        tick.bucketTimeMs(),
                        tick.labels().stream()
                                .map(label -> new PlanReportResponse.LabelMetric(
                                        label.label(),
                                        label.samples(),
                                        label.errorSamples(),
                                        label.throughput(),
                                        label.avgRtMs(),
                                        label.p95RtMs()))
                                .toList()))
                .toList();
        return new PlanReportResponse.MetricSeriesData(ticks);
    }

    private PlanReportResponse.FailureSummary buildFailures(long executionId, Set<String> labels) {
        TaskSamplePage page = executionService.getSamples(executionId, 1, MAX_FAILURE_SAMPLES, null, null, false);
        if (page == null || page.samples() == null || page.samples().isEmpty()) {
            return new PlanReportResponse.FailureSummary(0, false, List.of());
        }
        List<PlanReportResponse.FailureSample> samples = page.samples().stream()
                .filter(sample -> labels.isEmpty() || labels.contains(sample.label()))
                .map(sample -> new PlanReportResponse.FailureSample(
                        sample.id(),
                        sample.time(),
                        sample.statusCode(),
                        sample.success(),
                        sample.label(),
                        sample.elapsed(),
                        sample.message(),
                        sample.threadName()))
                .toList();
        return new PlanReportResponse.FailureSummary(
                samples.size(),
                page.total() > MAX_FAILURE_SAMPLES,
                samples
        );
    }

    private List<ScriptStepDefinition> loadScriptSteps(long scriptVersionId) {
        try {
            PersistentScriptVersionRecord script = scriptVersionRepository.findById(scriptVersionId).orElse(null);
            if (script == null) {
                return List.of();
            }
            return configSupport.loadScriptSteps(Path.of(script.getStoredPath()));
        } catch (Exception exception) {
            return List.of();
        }
    }

    private static String formatInstant(Instant instant) {
        return instant == null ? null : ISO_FORMATTER.format(instant);
    }
}
