package com.yr.perftest.platform.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.execution.ExecutionConfig;
import com.yr.perftest.platform.execution.ExecutionEventBroadcaster;
import com.yr.perftest.platform.execution.ExecutionStatus;
import com.yr.perftest.platform.execution.ExecutionValidationException;
import com.yr.perftest.platform.execution.TaskExecutionResult;
import com.yr.perftest.platform.execution.TaskMetricSeries;
import com.yr.perftest.platform.execution.TaskSamplePage;
import com.yr.perftest.platform.execution.aggregate.AggregateReportService;
import com.yr.perftest.platform.execution.aggregate.ExecutionMetricSeriesService;
import com.yr.perftest.platform.execution.aggregate.MetricTick;
import com.yr.perftest.platform.execution.aggregate.PersistentExecutionMetricSeriesRecord;
import com.yr.perftest.platform.execution.failure.FailureSampleIngestor;
import com.yr.perftest.platform.execution.failure.FailureSamplePaths;
import com.yr.perftest.platform.execution.failure.FailureSampleQuery;
import com.yr.perftest.platform.execution.failure.FailureSampleSseHub;
import com.yr.perftest.platform.execution.failure.FailureSampleStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 执行读模型：结果、采样、失败样本、指标与实时流的唯一查询入口。
 * 写侧（触发/停止/删除）见 {@link ScenarioExecutionService} 与 {@link ExecutionControlService}。
 */
@Service
public class ExecutionQueryService {
    private final PersistentTaskPlanRepository planRepository;
    private final PersistentTaskScenarioRepository scenarioRepository;
    private final PersistentScenarioExecutionRepository executionRepository;
    private final FailureSampleStore failureSampleStore;
    private final FailureSampleIngestor failureSampleIngestor;
    private final FailureSampleSseHub failureSampleSseHub;
    private final AggregateReportService aggregateReportService;
    private final ExecutionMetricSeriesService metricSeriesService;
    private final ExecutionEventBroadcaster executionEventBroadcaster;
    private final ObjectMapper objectMapper;

    public ExecutionQueryService(
            PersistentTaskPlanRepository planRepository,
            PersistentTaskScenarioRepository scenarioRepository,
            PersistentScenarioExecutionRepository executionRepository,
            FailureSampleStore failureSampleStore,
            FailureSampleIngestor failureSampleIngestor,
            FailureSampleSseHub failureSampleSseHub,
            AggregateReportService aggregateReportService,
            ExecutionMetricSeriesService metricSeriesService,
            ExecutionEventBroadcaster executionEventBroadcaster,
            ObjectMapper objectMapper
    ) {
        this.planRepository = planRepository;
        this.scenarioRepository = scenarioRepository;
        this.executionRepository = executionRepository;
        this.failureSampleStore = failureSampleStore;
        this.failureSampleIngestor = failureSampleIngestor;
        this.failureSampleSseHub = failureSampleSseHub;
        this.aggregateReportService = aggregateReportService;
        this.metricSeriesService = metricSeriesService;
        this.executionEventBroadcaster = executionEventBroadcaster;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ScenarioExecution> listExecutions(long scenarioId) {
        requireScenario(scenarioId);
        PersistentTaskScenarioRecord scenario = scenarioRepository.findById(scenarioId).orElseThrow();
        PersistentTaskPlanRecord plan = planRepository.findById(scenario.getPlanId()).orElseThrow();
        return executionRepository.findAllByScenarioIdOrderByIdDesc(scenarioId).stream()
                .map(execution -> toExecution(plan, scenario, execution))
                .toList();
    }

    @Transactional(readOnly = true)
    public ScenarioExecution getExecution(long executionId) {
        PersistentScenarioExecutionRecord execution = requireExecution(executionId);
        PersistentTaskScenarioRecord scenario = scenarioRepository.findById(execution.getScenarioId()).orElseThrow();
        PersistentTaskPlanRecord plan = planRepository.findById(scenario.getPlanId()).orElseThrow();
        return toExecution(plan, scenario, execution);
    }

    @Transactional(readOnly = true)
    public SseEmitter streamExecution(long executionId) {
        requireExecution(executionId);
        return executionEventBroadcaster.connect(executionId);
    }

    @Transactional(readOnly = true)
    public String getLogs(long executionId) {
        PersistentScenarioExecutionRecord execution = requireExecution(executionId);
        if (execution.getLogFilePath() == null) {
            return "";
        }
        try {
            Path logPath = Path.of(execution.getLogFilePath());
            return Files.exists(logPath) ? Files.readString(logPath) : "";
        } catch (Exception exception) {
            return "";
        }
    }

    @Transactional(readOnly = true)
    public TaskExecutionResult getResult(long executionId) {
        PersistentScenarioExecutionRecord execution = requireExecution(executionId);
        double durationSeconds = executionSeconds(execution);
        if (isFinished(execution.getStatus())) {
            return aggregateReportService.loadPersisted(executionId)
                    .or(() -> aggregateReportService.loadLive(executionId, durationSeconds))
                    .orElse(TaskExecutionResult.empty());
        }
        return aggregateReportService.loadLive(executionId, durationSeconds)
                .orElse(TaskExecutionResult.empty());
    }

    @Transactional(readOnly = true)
    public TaskSamplePage getSamples(
            long executionId,
            int page,
            int pageSize,
            String label,
            String code,
            Boolean success
    ) {
        PersistentScenarioExecutionRecord execution = requireExecution(executionId);
        Path dbPath = resolveDbPath(execution);
        if (dbPath == null) {
            return new TaskSamplePage(Math.max(1, page), Math.max(1, Math.min(100, pageSize)), 0, List.of());
        }
        try {
            return failureSampleStore.querySummaries(
                    dbPath,
                    new FailureSampleQuery(label, code, success),
                    page,
                    pageSize
            );
        } catch (Exception exception) {
            return new TaskSamplePage(Math.max(1, page), Math.max(1, Math.min(100, pageSize)), 0, List.of());
        }
    }

    @Transactional(readOnly = true)
    public FailureSampleSlice listFailureSamplesAfter(long executionId, long lastId, int limit) throws Exception {
        PersistentScenarioExecutionRecord execution = requireExecution(executionId);
        Path dbPath = resolveDbPath(execution);
        if (dbPath == null || !Files.exists(dbPath)) {
            return new FailureSampleSlice(List.of(), FailureSamplePaths.SQLITE_FILE);
        }
        return new FailureSampleSlice(
                failureSampleStore.listSummariesAfter(dbPath, lastId, limit),
                dbPath.getFileName().toString()
        );
    }

    @Transactional(readOnly = true)
    public TaskExecutionResult.Sample getSampleDetail(long executionId, long sampleId) {
        PersistentScenarioExecutionRecord execution = requireExecution(executionId);
        Path dbPath = resolveDbPath(execution);
        if (dbPath == null) {
            throw new ExecutionValidationException("sample does not exist");
        }
        try {
            return failureSampleStore.findDetail(dbPath, sampleId)
                    .orElseThrow(() -> new ExecutionValidationException("sample does not exist"));
        } catch (ExecutionValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ExecutionValidationException("sample does not exist");
        }
    }

    @Transactional(readOnly = true)
    public SseEmitter streamSamples(long executionId, String lastEventId) {
        PersistentScenarioExecutionRecord execution = requireExecution(executionId);
        Path dbPath = resolveDbPath(execution);
        if (dbPath == null) {
            SseEmitter emitter = new SseEmitter(0L);
            emitter.complete();
            return emitter;
        }
        long replayFrom = parseLastEventId(lastEventId);
        return failureSampleSseHub.connect(
                executionId,
                new FailureSampleSseHub.PathContext(
                        FailureSamplePaths.jsonl(FailureSamplePaths.executionDirectory(Path.of(execution.getLogFilePath()))),
                        dbPath
                ),
                replayFrom
        );
    }

    @Transactional(readOnly = true)
    public TaskMetricSeries getMonitoring(long executionId) {
        PersistentScenarioExecutionRecord execution = requireExecution(executionId);
        if (isFinished(execution.getStatus())) {
            return loadPersistedMetricSeries(executionId);
        }
        return new TaskMetricSeries(metricSeriesService.latestLiveTicks(executionId, 1200));
    }

    private TaskMetricSeries loadPersistedMetricSeries(long executionId) {
        List<PersistentExecutionMetricSeriesRecord> records = metricSeriesService.loadPersisted(executionId);
        if (records.isEmpty()) {
            return TaskMetricSeries.empty();
        }
        Map<Long, BucketAccumulator> buckets = new LinkedHashMap<>();
        for (PersistentExecutionMetricSeriesRecord record : records) {
            BucketAccumulator bucket = buckets.computeIfAbsent(record.getBucketTimeMs(), BucketAccumulator::new);
            bucket.add(record);
        }
        List<MetricTick> ticks = new ArrayList<>(buckets.size());
        buckets.values().forEach(bucket -> ticks.add(bucket.toTick()));
        return new TaskMetricSeries(ticks);
    }

    private Path resolveDbPath(PersistentScenarioExecutionRecord execution) {
        if (execution.getLogFilePath() == null) {
            return null;
        }
        Path logPath = Path.of(execution.getLogFilePath());
        FailureSampleSseHub.PathContext active = failureSampleIngestor.pathContext(execution.getId());
        if (active != null) {
            return active.dbPath();
        }
        return failureSampleIngestor.resolveDbPath(logPath);
    }

    private long parseLastEventId(String lastEventId) {
        if (lastEventId == null || lastEventId.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(lastEventId);
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private PersistentTaskScenarioRecord requireScenario(long scenarioId) {
        return scenarioRepository.findById(scenarioId)
                .orElseThrow(() -> new ExecutionValidationException("scenario does not exist"));
    }

    private PersistentScenarioExecutionRecord requireExecution(long executionId) {
        return executionRepository.findById(executionId)
                .orElseThrow(() -> new ExecutionValidationException("execution does not exist"));
    }

    private ScenarioExecution toExecution(
            PersistentTaskPlanRecord plan,
            PersistentTaskScenarioRecord scenario,
            PersistentScenarioExecutionRecord execution
    ) {
        ExecutionConfig config = readConfig(execution.getConfigJson());
        return new ScenarioExecution(
                execution.getId(),
                scenario.getId(),
                plan.getId(),
                plan.getProjectId(),
                scenario.getScriptVersionId(),
                scenario.getName(),
                execution.getStatus(),
                config,
                execution.getCreatedAt(),
                execution.getStartTime(),
                execution.getEndTime(),
                execution.getDurationMs(),
                execution.getResultFilePath(),
                execution.getLogFilePath(),
                execution.getErrorMessage(),
                execution.getExecutionName()
        );
    }

    private ExecutionConfig readConfig(String configJson) {
        try {
            return objectMapper.readValue(configJson, ExecutionConfig.class);
        } catch (Exception exception) {
            throw new ExecutionValidationException("execution config is invalid");
        }
    }

    private double executionSeconds(PersistentScenarioExecutionRecord execution) {
        if (execution.getDurationMs() != null && execution.getDurationMs() > 0) {
            return Math.max(1, execution.getDurationMs() / 1000.0);
        }
        if (execution.getStartTime() == null) {
            return 1;
        }
        long seconds = java.time.Duration.between(execution.getStartTime(), Instant.now()).getSeconds();
        return Math.max(1, seconds);
    }

    private boolean isFinished(ExecutionStatus status) {
        return status == ExecutionStatus.SUCCESS
                || status == ExecutionStatus.FAILED
                || status == ExecutionStatus.CANCELLED
                || status == ExecutionStatus.INTERRUPTED;
    }

    private static final class BucketAccumulator {
        private final long bucketTimeMs;
        private final List<MetricTick.LabelMetric> labels = new ArrayList<>();
        private long totalSamples;
        private long totalErrors;
        private double totalThroughput;
        private long weightedAvgRtSum;
        private long weightedP95Sum;

        private BucketAccumulator(long bucketTimeMs) {
            this.bucketTimeMs = bucketTimeMs;
        }

        private void add(PersistentExecutionMetricSeriesRecord record) {
            labels.add(new MetricTick.LabelMetric(
                    record.getLabel(),
                    record.getSamples(),
                    record.getErrorSamples(),
                    record.getThroughput(),
                    record.getAvgRtMs(),
                    record.getP95RtMs()
            ));
            totalSamples += record.getSamples();
            totalErrors += record.getErrorSamples();
            totalThroughput += record.getThroughput();
            weightedAvgRtSum += record.getAvgRtMs() * record.getSamples();
            weightedP95Sum = Math.max(weightedP95Sum, record.getP95RtMs());
        }

        private MetricTick toTick() {
            long divisor = Math.max(1, totalSamples);
            MetricTick.LabelMetric overall = new MetricTick.LabelMetric(
                    "__total__",
                    totalSamples,
                    totalErrors,
                    Math.round(totalThroughput * 100.0) / 100.0,
                    weightedAvgRtSum / divisor,
                    weightedP95Sum
            );
            return new MetricTick(bucketTimeMs, labels, overall);
        }
    }

    public record FailureSampleSlice(List<TaskExecutionResult.Sample> samples, String sourceName) {
    }
}
