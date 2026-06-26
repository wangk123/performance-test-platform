package com.yr.perftest.platform.execution.distributed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.execution.ExecutionFailureSummarizer;
import com.yr.perftest.platform.execution.ExecutionConfig;
import com.yr.perftest.platform.execution.ExecutionStatus;
import com.yr.perftest.platform.execution.ExecutionValidationException;
import com.yr.perftest.platform.execution.FailureSampleSettings;
import com.yr.perftest.platform.execution.failure.FailureSampleIngestor;
import com.yr.perftest.platform.execution.failure.FailureSamplePaths;
import com.yr.perftest.platform.monitoring.ExecutionMonitorBindingService;
import com.yr.perftest.platform.script.JmeterScriptNormalizer;
import com.yr.perftest.platform.script.PersistentScriptVersionRecord;
import com.yr.perftest.platform.script.PersistentScriptVersionRepository;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRecord;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRepository;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.PersistentTaskScenarioRecord;
import com.yr.perftest.platform.task.PersistentTaskScenarioRepository;
import com.yr.perftest.platform.task.ScenarioExecutionRuntime;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
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
    private final FailureSampleSettings failureSampleSettings;
    private final JmeterScriptNormalizer scriptNormalizer;
    private final ExecutionMonitorBindingService monitorBindingService;
    private final ScenarioExecutionRuntime executionRuntime;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final Path storageRoot;
    private final String influxdbUrl;
    private final String influxdbMeasurement;
    private final Duration idleTimeout;
    private final Duration pollInterval;
    private final Duration tailInterval;
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
            FailureSampleSettings failureSampleSettings,
            JmeterScriptNormalizer scriptNormalizer,
            ExecutionMonitorBindingService monitorBindingService,
            ScenarioExecutionRuntime executionRuntime,
            TransactionTemplate transactionTemplate,
            ObjectMapper objectMapper,
            @Value("${platform.storage.root:./storage}") String storageRoot,
            @Value("${platform.distributed.influxdb-url:http://127.0.0.1:8086/write?db=jmeter}") String influxdbUrl,
            @Value("${platform.distributed.influxdb-measurement:jmeter_runtime}") String influxdbMeasurement,
            @Value("${platform.execution.max-concurrent-tasks:1}") int maxConcurrentTasks,
            @Value("${platform.distributed.runner.idle-timeout-seconds:300}") long idleTimeoutSeconds,
            @Value("${platform.distributed.runner.poll-interval-seconds:10}") long pollIntervalSeconds
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
        this.failureSampleSettings = failureSampleSettings;
        this.scriptNormalizer = scriptNormalizer;
        this.monitorBindingService = monitorBindingService;
        this.executionRuntime = executionRuntime;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
        this.storageRoot = Path.of(storageRoot);
        this.influxdbUrl = influxdbUrl;
        this.influxdbMeasurement = influxdbMeasurement;
        this.idleTimeout = Duration.ofSeconds(idleTimeoutSeconds);
        this.pollInterval = Duration.ofSeconds(pollIntervalSeconds);
        this.tailInterval = Duration.ofMillis(failureSampleSettings.tailIntervalMs());
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
            scriptNormalizer.copyNormalized(preparation.sourcePath(), preparation.originalTestPlanPath());
            backendListenerInjector.inject(
                    preparation.originalTestPlanPath(),
                    preparation.distributedTestPlanPath(),
                    preparation.runId(),
                    influxdbUrl,
                    influxdbMeasurement,
                    Path.of("/test/failure-samples.jsonl")
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
                    RemoteRunnerResult cleanup = remoteRunnerClient.stopRun(payload);
                    if (preparation != null) {
                        appendLog(preparation.logPath(), cleanup.log());
                    }
                }
                failureSampleIngestor.finish(executionId);
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
        while (true) {
            if (executionRuntime.isStopRequested(executionId)) {
                remoteRunnerClient.stopRun(payload);
                return RemoteRunnerResult.failed("execution stopped by user");
            }
            RemoteRunnerResult poll = remoteRunnerClient.pollRun(payload);
            if (poll.ok()) {
                lastResponseAt = Instant.now();
                if ("running".equals(poll.message())) {
                    if (Duration.between(lastSampleTailAt, Instant.now()).compareTo(tailInterval) >= 0) {
                        failureSampleIngestor.tailRemote(executionId, payload);
                        lastSampleTailAt = Instant.now();
                    }
                    TimeUnit.MILLISECONDS.sleep(tailInterval.toMillis());
                    continue;
                }
                if ("finished".equals(poll.message())) {
                    failureSampleIngestor.tailRemote(executionId, payload);
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
            return new DistributedExecutionPreparation(
                    "execution-" + execution.getId(),
                    Path.of(script.getStoredPath()),
                    executionDirectory,
                    executionDirectory.resolve(filename),
                    executionDirectory.resolve("distributed-" + filename),
                    executionDirectory.resolve("discard.jtl"),
                    FailureSamplePaths.jsonl(executionDirectory),
                    executionDirectory.resolve("jmeter.log"),
                    controller,
                    workers
            );
        });
    }

    private Map<String, Object> payload(DistributedExecutionPreparation preparation) {
        return Map.of(
                "runId", preparation.runId(),
                "scriptPath", preparation.distributedTestPlanPath().toString(),
                "discardPath", preparation.discardPath().toString(),
                "failureSamplesPath", preparation.failureSamplesPath().toString(),
                "logPath", preparation.logPath().toString(),
                "perLabelLimit", failureSampleSettings.perLabelLimit(),
                "controller", nodePayload(preparation.controller()),
                "workers", preparation.workers().stream().map(this::nodePayload).toList(),
                "dependencies", dependencyCollector.collect(preparation.sourcePath())
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
            PersistentExecutionNodeRecord controller,
            List<PersistentExecutionNodeRecord> workers
    ) {
    }
}
