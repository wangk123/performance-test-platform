package com.yr.perftest.platform.execution.distributed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.execution.ExecutionFailureSummarizer;
import com.yr.perftest.platform.execution.ExecutionConfig;
import com.yr.perftest.platform.execution.ExecutionStatus;
import com.yr.perftest.platform.execution.ExecutionValidationException;
import com.yr.perftest.platform.execution.FailureSampleSettings;
import com.yr.perftest.platform.execution.aggregate.AggregateReportService;
import com.yr.perftest.platform.execution.failure.FailureSampleIngestor;
import com.yr.perftest.platform.execution.failure.FailureSamplePaths;
import com.yr.perftest.platform.monitoring.ExecutionMonitorBindingService;
import com.yr.perftest.platform.monitoring.TargetMetricsSnapshotService;
import com.yr.perftest.platform.script.JmeterScriptNormalizer;
import com.yr.perftest.platform.script.JmeterScriptParser;
import com.yr.perftest.platform.script.JmeterScriptPatcher;
import com.yr.perftest.platform.script.PersistentScriptVersionRecord;
import com.yr.perftest.platform.script.ScriptStepDefinition;
import com.yr.perftest.platform.script.ThreadGroupStepPatcher;
import com.yr.perftest.platform.script.PersistentScriptVersionRepository;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRecord;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRepository;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.PersistentTaskScenarioRecord;
import com.yr.perftest.platform.task.PersistentTaskScenarioRepository;
import com.yr.perftest.platform.task.ScenarioExecutionRuntime;
import com.yr.perftest.platform.task.ScenarioThreadGroupConfig;
import com.yr.perftest.platform.task.ScenarioThreadGroupConfigSupport;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
public class DistributedJmeterExecutionRunner {
    private final PersistentTaskPlanRepository planRepository;
    private final PersistentTaskScenarioRepository scenarioRepository;
    private final PersistentScenarioExecutionRepository executionRepository;
    private final PersistentScriptVersionRepository scriptVersionRepository;
    private final PersistentExecutionNodeRepository nodeRepository;
    private final RemoteRunnerClient remoteRunnerClient;
    private final JmeterBackendListenerInjector backendListenerInjector;
    private final JmeterDependencyCollector dependencyCollector;
    private final FailureSampleIngestor failureSampleIngestor;
    private final AggregateReportService aggregateReportService;
    private final TargetMetricsSnapshotService targetMetricsSnapshotService;
    private final FailureSampleSettings failureSampleSettings;
    private final JmeterScriptNormalizer scriptNormalizer;
    private final JmeterScriptParser scriptParser;
    private final JmeterScriptPatcher scriptPatcher;
    private final ThreadGroupStepPatcher threadGroupStepPatcher;
    private final ScenarioThreadGroupConfigSupport threadGroupConfigSupport;
    private final ExecutionMonitorBindingService monitorBindingService;
    private final ScenarioExecutionRuntime executionRuntime;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final Path storageRoot;
    private final Duration idleTimeout;
    private final Duration pollInterval;
    private final Duration tailInterval;
    private final Duration fetchInterval;
    private final ExecutorService executorService;

    public DistributedJmeterExecutionRunner(
            PersistentTaskPlanRepository planRepository,
            PersistentTaskScenarioRepository scenarioRepository,
            PersistentScenarioExecutionRepository executionRepository,
            PersistentScriptVersionRepository scriptVersionRepository,
            PersistentExecutionNodeRepository nodeRepository,
            RemoteRunnerClient remoteRunnerClient,
            JmeterBackendListenerInjector backendListenerInjector,
            JmeterDependencyCollector dependencyCollector,
            FailureSampleIngestor failureSampleIngestor,
            AggregateReportService aggregateReportService,
            TargetMetricsSnapshotService targetMetricsSnapshotService,
            FailureSampleSettings failureSampleSettings,
            JmeterScriptNormalizer scriptNormalizer,
            JmeterScriptParser scriptParser,
            JmeterScriptPatcher scriptPatcher,
            ThreadGroupStepPatcher threadGroupStepPatcher,
            ScenarioThreadGroupConfigSupport threadGroupConfigSupport,
            ExecutionMonitorBindingService monitorBindingService,
            ScenarioExecutionRuntime executionRuntime,
            TransactionTemplate transactionTemplate,
            ObjectMapper objectMapper,
            @Value("${platform.storage.root:./storage}") String storageRoot,
            @Value("${platform.execution.max-concurrent-tasks:1}") int maxConcurrentTasks,
            @Value("${platform.distributed.runner.idle-timeout-seconds:300}") long idleTimeoutSeconds,
            @Value("${platform.distributed.runner.poll-interval-seconds:10}") long pollIntervalSeconds,
            @Value("${platform.execution.aggregate.fetch-interval-ms:3000}") long fetchIntervalMs
    ) {
        this.planRepository = planRepository;
        this.scenarioRepository = scenarioRepository;
        this.executionRepository = executionRepository;
        this.scriptVersionRepository = scriptVersionRepository;
        this.nodeRepository = nodeRepository;
        this.remoteRunnerClient = remoteRunnerClient;
        this.backendListenerInjector = backendListenerInjector;
        this.dependencyCollector = dependencyCollector;
        this.failureSampleIngestor = failureSampleIngestor;
        this.aggregateReportService = aggregateReportService;
        this.targetMetricsSnapshotService = targetMetricsSnapshotService;
        this.failureSampleSettings = failureSampleSettings;
        this.scriptNormalizer = scriptNormalizer;
        this.scriptParser = scriptParser;
        this.scriptPatcher = scriptPatcher;
        this.threadGroupStepPatcher = threadGroupStepPatcher;
        this.threadGroupConfigSupport = threadGroupConfigSupport;
        this.monitorBindingService = monitorBindingService;
        this.executionRuntime = executionRuntime;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
        this.storageRoot = Path.of(storageRoot);
        this.idleTimeout = Duration.ofSeconds(idleTimeoutSeconds);
        this.pollInterval = Duration.ofSeconds(pollIntervalSeconds);
        this.tailInterval = Duration.ofMillis(failureSampleSettings.tailIntervalMs());
        this.fetchInterval = Duration.ofMillis(fetchIntervalMs);
        this.executorService = Executors.newFixedThreadPool(Math.max(1, maxConcurrentTasks), runnable -> {
            Thread thread = new Thread(runnable, "distributed-jmeter-execution-runner");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void submit(long executionId) {
        executorService.submit(() -> run(executionId));
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdownNow();
    }

    private void run(long executionId) {
        Map<String, Object> payload = null;
        DistributedExecutionPreparation preparation = null;
        try {
            if (executionRuntime.isStopRequested(executionId)) {
                markInterrupted(executionId, null, "execution stopped before start");
                return;
            }
            preparation = loadPreparation(executionId);
            if (preparation == null) {
                return;
            }
            if (executionRuntime.isStopRequested(executionId)) {
                markInterrupted(executionId, null, "execution stopped before start");
                return;
            }
            Files.createDirectories(preparation.executionDirectory());
            Path hdrHistogramJar = ensureHdrHistogramJar(preparation.executionDirectory());
            scriptNormalizer.copyNormalized(preparation.sourcePath(), preparation.originalTestPlanPath());
            applyThreadGroupOverride(executionId, preparation.originalTestPlanPath());
            backendListenerInjector.inject(
                    preparation.originalTestPlanPath(),
                    preparation.distributedTestPlanPath(),
                    Path.of("/test/aggregate-snapshot.bin")
            );
            Files.writeString(
                    preparation.logPath(),
                    "",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            failureSampleIngestor.begin(executionId, preparation.executionDirectory());
            markRunning(executionId, preparation.discardPath(), preparation.logPath());
            payload = payload(preparation);
            executionRuntime.storePayload(executionId, payload);
            RemoteRunnerResult finishResult = null;
            try {
                RemoteRunnerResult launchResult = remoteRunnerClient.startRun(payload);
                appendLog(preparation.logPath(), launchResult.log());
                if (!launchResult.ok()) {
                    collectArtifactsQuietly(executionId, payload, preparation.logPath());
                    markFailed(
                            executionId,
                            launchResult.exitCode(),
                            ExecutionFailureSummarizer.summarize(launchResult.message(), preparation.logPath())
                    );
                    return;
                }
                finishResult = waitForCompletion(executionId, payload, preparation.logPath());
                if (executionRuntime.isStopRequested(executionId)) {
                    collectArtifactsQuietly(executionId, payload, preparation.logPath());
                    markInterrupted(executionId, finishResult != null ? finishResult.exitCode() : null, "execution stopped by user");
                    return;
                }
                if (!finishResult.ok()) {
                    collectArtifactsQuietly(executionId, payload, preparation.logPath());
                    markFailed(
                            executionId,
                            finishResult.exitCode(),
                            ExecutionFailureSummarizer.summarize(failureHint(finishResult), preparation.logPath())
                    );
                    return;
                }
                RemoteRunnerResult collectResult = remoteRunnerClient.collectRun(payload);
                appendLog(preparation.logPath(), collectResult.log());
                failureSampleIngestor.ingestLocalFile(executionId);
                if (finishResult.exitCode() == 0) {
                    markSuccess(executionId);
                } else {
                    markFailed(
                            executionId,
                            finishResult.exitCode(),
                            ExecutionFailureSummarizer.summarize(failureHint(finishResult), preparation.logPath())
                    );
                }
            } finally {
                if (payload != null) {
                    boolean partial = isPartialFinish(executionId);
                    fetchAggregateSnapshotQuietly(executionId, payload);
                    persistAggregateReport(executionId, partial);
                    captureTargetMetricsQuietly(executionId);
                    RemoteRunnerResult cleanup = remoteRunnerClient.stopRun(payload);
                    if (preparation != null) {
                        appendLog(preparation.logPath(), cleanup.log());
                    }
                }
                failureSampleIngestor.finish(executionId);
                aggregateReportService.clearLive(executionId);
                executionRuntime.clear(executionId);
            }
        } catch (Exception exception) {
            markFailed(executionId, null, exception.getMessage());
            failureSampleIngestor.finish(executionId);
            executionRuntime.clear(executionId);
        }
    }

    private RemoteRunnerResult waitForCompletion(long executionId, Map<String, Object> payload, Path logPath) throws InterruptedException {
        Instant lastResponseAt = Instant.now();
        Instant lastSampleTailAt = Instant.now();
        Instant lastAggregateFetchAt = Instant.now();
        while (true) {
            if (executionRuntime.isStopRequested(executionId)) {
                remoteRunnerClient.stopRun(payload);
                return RemoteRunnerResult.failed("execution stopped by user");
            }
            RemoteRunnerResult poll = remoteRunnerClient.pollRun(payload);
            if (poll.ok()) {
                lastResponseAt = Instant.now();
                if ("running".equals(poll.message())) {
                    Instant now = Instant.now();
                    if (Duration.between(lastSampleTailAt, now).compareTo(tailInterval) >= 0) {
                        failureSampleIngestor.tailRemote(executionId, payload);
                        lastSampleTailAt = now;
                    }
                    if (Duration.between(lastAggregateFetchAt, now).compareTo(fetchInterval) >= 0) {
                        fetchAggregateSnapshot(executionId, payload);
                        lastAggregateFetchAt = now;
                    }
                    TimeUnit.MILLISECONDS.sleep(Math.min(tailInterval.toMillis(), fetchInterval.toMillis()));
                    continue;
                }
                if ("finished".equals(poll.message())) {
                    failureSampleIngestor.tailRemote(executionId, payload);
                    fetchAggregateSnapshot(executionId, payload);
                    return poll;
                }
                return RemoteRunnerResult.failed(poll.message());
            }
            if (Duration.between(lastResponseAt, Instant.now()).compareTo(idleTimeout) >= 0) {
                return RemoteRunnerResult.failed("remote runner idle timeout: execution node not responding");
            }
            TimeUnit.MILLISECONDS.sleep(pollInterval.toMillis());
        }
    }

    private DistributedExecutionPreparation loadPreparation(long executionId) {
        return transactionTemplate.execute(status -> {
            PersistentScenarioExecutionRecord execution = executionRepository.findById(executionId)
                    .orElseThrow(() -> new ExecutionValidationException("execution does not exist"));
            if (execution.getStatus() != ExecutionStatus.QUEUED && execution.getStatus() != ExecutionStatus.STOPPING) {
                return null;
            }
            PersistentTaskScenarioRecord scenario = scenarioRepository.findById(execution.getScenarioId())
                    .orElseThrow(() -> new ExecutionValidationException("scenario does not exist"));
            PersistentTaskPlanRecord plan = planRepository.findById(scenario.getPlanId())
                    .orElseThrow(() -> new ExecutionValidationException("task plan does not exist"));
            PersistentScriptVersionRecord script = scriptVersionRepository.findById(scenario.getScriptVersionId())
                    .orElseThrow(() -> new ExecutionValidationException("script version does not exist"));
            ExecutionConfig config = readConfig(execution.getConfigJson());
            if (config.controllerNodeId() == null || config.workerNodeIds().isEmpty()) {
                throw new ExecutionValidationException("distributed execution nodes are required");
            }
            PersistentExecutionNodeRecord controller = nodeRepository.findById(config.controllerNodeId())
                    .orElseThrow(() -> new ExecutionValidationException("controller node does not exist"));
            List<PersistentExecutionNodeRecord> workers = config.workerNodeIds().stream()
                    .map(nodeId -> nodeRepository.findById(nodeId)
                            .orElseThrow(() -> new ExecutionValidationException("worker node does not exist")))
                    .toList();
            validateController(controller);
            workers.forEach(worker -> validateWorker(worker, controller));
            Path executionDirectory = storageRoot
                    .resolve("executions")
                    .resolve(String.valueOf(plan.getProjectId()))
                    .resolve(String.valueOf(plan.getId()))
                    .resolve(String.valueOf(scenario.getId()))
                    .resolve(String.valueOf(execution.getId()));
            String filename = sanitizeFilename(script.getOriginalFilename());
            Path hdrHistogramJar = executionDirectory.resolve("HdrHistogram-2.2.2.jar");
            return new DistributedExecutionPreparation(
                    "execution-" + execution.getId(),
                    Path.of(script.getStoredPath()),
                    executionDirectory,
                    executionDirectory.resolve(filename),
                    executionDirectory.resolve("distributed-" + filename),
                    executionDirectory.resolve("discard.jtl"),
                    FailureSamplePaths.jsonl(executionDirectory),
                    executionDirectory.resolve("jmeter.log"),
                    hdrHistogramJar,
                    controller,
                    workers
            );
        });
    }

    private void applyThreadGroupOverride(long executionId, Path testPlanPath) {
        PersistentScenarioExecutionRecord execution = transactionTemplate.execute(status ->
                executionRepository.findById(executionId)
                        .orElseThrow(() -> new ExecutionValidationException("execution does not exist")));
        ExecutionConfig config = readConfig(execution.getConfigJson());
        if (config == null) {
            return;
        }
        PersistentTaskScenarioRecord scenario = transactionTemplate.execute(status ->
                scenarioRepository.findById(execution.getScenarioId())
                        .orElseThrow(() -> new ExecutionValidationException("scenario does not exist")));
        List<ScenarioThreadGroupConfig> stored = threadGroupConfigSupport.readStored(scenario.getThreadGroupConfigsJson());
        List<ScenarioThreadGroupConfig> presetRows = List.of();
        if (config.threadGroupPresetSortOrder() != null) {
            presetRows = threadGroupConfigSupport.presetConfigsBySortOrder(stored, config.threadGroupPresetSortOrder());
        } else if (config.threadGroupConfigId() != null) {
            presetRows = threadGroupConfigSupport.presetConfigs(stored, config.threadGroupConfigId());
        } else if (config.stepId() != null && !config.stepId().isBlank() && config.threads() > 0) {
            presetRows = List.of(new ScenarioThreadGroupConfig(
                    config.threadGroupConfigId() != null ? config.threadGroupConfigId() : 0L,
                    config.stepId(),
                    config.stepName() != null ? config.stepName() : "",
                    config.threads(),
                    config.rampUp(),
                    config.duration(),
                    0,
                    null
            ));
        }
        if (presetRows.isEmpty()) {
            return;
        }
        try {
            String content = Files.readString(testPlanPath, StandardCharsets.UTF_8);
            List<ScriptStepDefinition> steps = scriptParser.parseSteps(content);
            List<ThreadGroupStepPatcher.ThreadGroupPatch> patches = threadGroupConfigSupport.buildPatches(steps, presetRows);
            if (patches.isEmpty()) {
                return;
            }
            List<ScriptStepDefinition> patched = threadGroupStepPatcher.patchAll(steps, patches);
            Files.writeString(testPlanPath, scriptPatcher.patch(content, patched), StandardCharsets.UTF_8);
        } catch (ExecutionValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ExecutionValidationException("failed to apply thread group override");
        }
    }

    private Map<String, Object> payload(DistributedExecutionPreparation preparation) {
        List<Map<String, String>> dependencies = new ArrayList<>();
        dependencyCollector.collect(preparation.distributedTestPlanPath()).forEach(file -> dependencies.add(Map.of(
                "sourcePath", file.sourcePath(),
                "targetPath", file.targetPath()
        )));
        dependencies.add(Map.of(
                "sourcePath", preparation.hdrHistogramJar().toString(),
                "targetPath", preparation.hdrHistogramJar().getFileName().toString()
        ));
        return Map.of(
                "runId", preparation.runId(),
                "scriptPath", preparation.distributedTestPlanPath().toString(),
                "discardPath", preparation.discardPath().toString(),
                "failureSamplesPath", preparation.failureSamplesPath().toString(),
                "logPath", preparation.logPath().toString(),
                "perLabelLimit", failureSampleSettings.perLabelLimit(),
                "globalLimit", failureSampleSettings.globalLimit(),
                "controller", nodePayload(preparation.controller()),
                "workers", preparation.workers().stream().map(this::nodePayload).toList(),
                "dependencies", dependencies
        );
    }

    private void validateController(PersistentExecutionNodeRecord node) {
        if (node.getStatus() != ExecutionNodeStatus.AVAILABLE) {
            throw new ExecutionValidationException("controller node is not available");
        }
        if (node.getRole() != ExecutionNodeRole.CONTROLLER && node.getRole() != ExecutionNodeRole.BOTH) {
            throw new ExecutionValidationException("controller node role is invalid");
        }
    }

    private void validateWorker(PersistentExecutionNodeRecord node, PersistentExecutionNodeRecord controller) {
        if (node.getStatus() != ExecutionNodeStatus.AVAILABLE) {
            throw new ExecutionValidationException("worker node is not available");
        }
        if (node.getId().equals(controller.getId())) {
            return;
        }
        if (node.getRole() != ExecutionNodeRole.WORKER && node.getRole() != ExecutionNodeRole.BOTH) {
            throw new ExecutionValidationException("worker node role is invalid");
        }
    }

    private Map<String, Object> nodePayload(PersistentExecutionNodeRecord node) {
        return Map.of(
                "id", node.getId(),
                "host", node.getHost(),
                "sshPort", node.getSshPort(),
                "sshUsername", node.getSshUsername(),
                "sshKeyPath", node.getSshKeyPath(),
                "remoteWorkDir", node.getRemoteWorkDir()
        );
    }

    private ExecutionConfig readConfig(String configJson) {
        try {
            return objectMapper.readValue(configJson, ExecutionConfig.class);
        } catch (Exception exception) {
            throw new ExecutionValidationException("execution config is invalid");
        }
    }

    private void markRunning(long executionId, Path resultPath, Path logPath) {
        transactionTemplate.executeWithoutResult(status -> {
            PersistentScenarioExecutionRecord execution = executionRepository.findById(executionId).orElseThrow();
            execution.markRunning(resultPath.toString(), logPath.toString());
            monitorBindingService.markStart(executionId, execution.getStartTime());
        });
    }

    private void markSuccess(long executionId) {
        transactionTemplate.executeWithoutResult(status -> {
            PersistentScenarioExecutionRecord execution = executionRepository.findById(executionId).orElseThrow();
            execution.markSuccess(0);
            monitorBindingService.markEnd(executionId, execution.getEndTime());
        });
    }

    private void markFailed(long executionId, Integer exitCode, String message) {
        transactionTemplate.executeWithoutResult(status -> {
            PersistentScenarioExecutionRecord execution = executionRepository.findById(executionId).orElse(null);
            if (execution == null) {
                return;
            }
            execution.markFailed(exitCode, normalizeMessage(message));
            monitorBindingService.markEnd(executionId, execution.getEndTime());
        });
    }

    private void markInterrupted(long executionId, Integer exitCode, String message) {
        transactionTemplate.executeWithoutResult(status -> {
            PersistentScenarioExecutionRecord execution = executionRepository.findById(executionId).orElse(null);
            if (execution == null) {
                return;
            }
            execution.markInterrupted(exitCode, normalizeMessage(message));
            monitorBindingService.markEnd(executionId, execution.getEndTime());
        });
    }

    private void appendLog(Path logPath, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        try {
            Files.writeString(logPath, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }

    private void collectArtifactsQuietly(long executionId, Map<String, Object> payload, Path logPath) {
        try {
            RemoteRunnerResult collectResult = remoteRunnerClient.collectRun(payload);
            appendLog(logPath, collectResult.log());
            failureSampleIngestor.ingestLocalFile(executionId);
        } catch (Exception ignored) {
        }
    }

    private String failureHint(RemoteRunnerResult result) {
        if (result.log() != null && !result.log().isBlank()) {
            return result.log();
        }
        return result.message();
    }

    private String normalizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "distributed JMeter execution failed";
        }
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }

    private void fetchAggregateSnapshot(long executionId, Map<String, Object> payload) {
        long lastMtime = aggregateReportService.lastSnapshotMtime(executionId);
        AggregateSnapshotPayload snapshot = remoteRunnerClient.fetchAggregateSnapshot(payload, lastMtime);
        if (snapshot.changed()) {
            aggregateReportService.cacheLive(executionId, snapshot.snapshots(), snapshot.mtime());
        }
    }

    private void fetchAggregateSnapshotQuietly(long executionId, Map<String, Object> payload) {
        try {
            fetchAggregateSnapshot(executionId, payload);
        } catch (Exception ignored) {
        }
    }

    private void captureTargetMetricsQuietly(long executionId) {
        try {
            targetMetricsSnapshotService.captureAll(executionId);
        } catch (Exception ignored) {
        }
    }

    private void persistAggregateReport(long executionId, boolean partial) {
        try {
            aggregateReportService.persistFinal(executionId, executionSeconds(executionId), partial);
        } catch (Exception ignored) {
        }
    }

    private boolean isPartialFinish(long executionId) {
        Boolean partial = transactionTemplate.execute(status -> {
            PersistentScenarioExecutionRecord execution = executionRepository.findById(executionId).orElse(null);
            if (execution == null) {
                return Boolean.TRUE;
            }
            ExecutionStatus current = execution.getStatus();
            return current == ExecutionStatus.STOPPING
                    || current == ExecutionStatus.INTERRUPTED
                    || current == ExecutionStatus.CANCELLED
                    || executionRuntime.isStopRequested(executionId);
        });
        return partial != null && partial;
    }

    private double executionSeconds(long executionId) {
        Double seconds = transactionTemplate.execute(status -> {
            PersistentScenarioExecutionRecord execution = executionRepository.findById(executionId).orElse(null);
            if (execution == null) {
                return 1.0;
            }
            if (execution.getDurationMs() != null && execution.getDurationMs() > 0) {
                return Math.max(1.0, execution.getDurationMs() / 1000.0);
            }
            if (execution.getStartTime() == null) {
                return 1.0;
            }
            long elapsed = Duration.between(execution.getStartTime(), Instant.now()).getSeconds();
            return Math.max(1.0, (double) elapsed);
        });
        return seconds == null ? 1.0 : seconds;
    }

    private Path ensureHdrHistogramJar(Path executionDirectory) throws Exception {
        Path target = executionDirectory.resolve("HdrHistogram-2.2.2.jar");
        if (Files.exists(target) && Files.size(target) > 0) {
            return target;
        }
        try (InputStream input = getClass().getResourceAsStream("/jmeter-runtime/HdrHistogram-2.2.2.jar")) {
            if (input == null) {
                throw new ExecutionValidationException("HdrHistogram runtime jar is missing");
            }
            Files.copy(input, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    private String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private record DistributedExecutionPreparation(
            String runId,
            Path sourcePath,
            Path executionDirectory,
            Path originalTestPlanPath,
            Path distributedTestPlanPath,
            Path discardPath,
            Path failureSamplesPath,
            Path logPath,
            Path hdrHistogramJar,
            PersistentExecutionNodeRecord controller,
            List<PersistentExecutionNodeRecord> workers
    ) {
    }
}
