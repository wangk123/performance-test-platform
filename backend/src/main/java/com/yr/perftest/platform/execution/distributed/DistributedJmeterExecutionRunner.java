package com.yr.perftest.platform.execution.distributed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.execution.ExecutionFailureSummarizer;
import com.yr.perftest.platform.execution.ExecutionConfig;
import com.yr.perftest.platform.execution.ExecutionStatus;
import com.yr.perftest.platform.execution.ExecutionValidationException;
import com.yr.perftest.platform.execution.PersistentTaskExecutionRecord;
import com.yr.perftest.platform.execution.PersistentTaskExecutionRepository;
import com.yr.perftest.platform.execution.PersistentTestTaskRecord;
import com.yr.perftest.platform.execution.PersistentTestTaskRepository;
import com.yr.perftest.platform.script.JmeterScriptNormalizer;
import com.yr.perftest.platform.script.PersistentScriptVersionRecord;
import com.yr.perftest.platform.script.PersistentScriptVersionRepository;
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
    private final PersistentTestTaskRepository taskRepository;
    private final PersistentTaskExecutionRepository executionRepository;
    private final PersistentScriptVersionRepository scriptVersionRepository;
    private final PersistentExecutionNodeRepository nodeRepository;
    private final RemoteRunnerClient remoteRunnerClient;
    private final JmeterBackendListenerInjector backendListenerInjector;
    private final JmeterDependencyCollector dependencyCollector;
    private final JmeterScriptNormalizer scriptNormalizer;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final Path storageRoot;
    private final String influxdbUrl;
    private final String influxdbMeasurement;
    private final Duration idleTimeout;
    private final Duration pollInterval;
    private final ExecutorService executorService;

    public DistributedJmeterExecutionRunner(
            PersistentTestTaskRepository taskRepository,
            PersistentTaskExecutionRepository executionRepository,
            PersistentScriptVersionRepository scriptVersionRepository,
            PersistentExecutionNodeRepository nodeRepository,
            RemoteRunnerClient remoteRunnerClient,
            JmeterBackendListenerInjector backendListenerInjector,
            JmeterDependencyCollector dependencyCollector,
            JmeterScriptNormalizer scriptNormalizer,
            TransactionTemplate transactionTemplate,
            ObjectMapper objectMapper,
            @Value("${platform.storage.root:./storage}") String storageRoot,
            @Value("${platform.distributed.influxdb-url:http://127.0.0.1:8086/write?db=jmeter}") String influxdbUrl,
            @Value("${platform.distributed.influxdb-measurement:jmeter_runtime}") String influxdbMeasurement,
            @Value("${platform.execution.max-concurrent-tasks:1}") int maxConcurrentTasks,
            @Value("${platform.distributed.runner.idle-timeout-seconds:300}") long idleTimeoutSeconds,
            @Value("${platform.distributed.runner.poll-interval-seconds:10}") long pollIntervalSeconds
    ) {
        this.taskRepository = taskRepository;
        this.executionRepository = executionRepository;
        this.scriptVersionRepository = scriptVersionRepository;
        this.nodeRepository = nodeRepository;
        this.remoteRunnerClient = remoteRunnerClient;
        this.backendListenerInjector = backendListenerInjector;
        this.dependencyCollector = dependencyCollector;
        this.scriptNormalizer = scriptNormalizer;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
        this.storageRoot = Path.of(storageRoot);
        this.influxdbUrl = influxdbUrl;
        this.influxdbMeasurement = influxdbMeasurement;
        this.idleTimeout = Duration.ofSeconds(idleTimeoutSeconds);
        this.pollInterval = Duration.ofSeconds(pollIntervalSeconds);
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
        try {
            DistributedExecutionPreparation preparation = loadPreparation(executionId);
            if (preparation == null) {
                return;
            }
            Files.createDirectories(preparation.executionDirectory());
            scriptNormalizer.copyNormalized(preparation.sourcePath(), preparation.originalTestPlanPath());
            backendListenerInjector.inject(
                    preparation.originalTestPlanPath(),
                    preparation.distributedTestPlanPath(),
                    preparation.runId(),
                    influxdbUrl,
                    influxdbMeasurement
            );
            Files.writeString(
                    preparation.logPath(),
                    "",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            markRunning(executionId, preparation.resultPath(), preparation.logPath());
            Map<String, Object> payload = payload(preparation);
            RemoteRunnerResult launchResult = null;
            RemoteRunnerResult finishResult = null;
            try {
                launchResult = remoteRunnerClient.startRun(payload);
                appendLog(preparation.logPath(), launchResult.log());
                if (!launchResult.ok()) {
                    markFailed(
                            executionId,
                            launchResult.exitCode(),
                            ExecutionFailureSummarizer.summarize(launchResult.message(), preparation.logPath())
                    );
                    return;
                }
                finishResult = waitForCompletion(payload);
                if (!finishResult.ok()) {
                    markFailed(
                            executionId,
                            finishResult.exitCode(),
                            ExecutionFailureSummarizer.summarize(finishResult.message(), preparation.logPath())
                    );
                    return;
                }
                RemoteRunnerResult collectResult = remoteRunnerClient.collectRun(payload);
                appendLog(preparation.logPath(), collectResult.log());
                if (finishResult.exitCode() == 0) {
                    markSuccess(executionId);
                } else {
                    markFailed(
                            executionId,
                            finishResult.exitCode(),
                            ExecutionFailureSummarizer.summarize(finishResult.message(), preparation.logPath())
                    );
                }
            } finally {
                RemoteRunnerResult cleanup = remoteRunnerClient.stopRun(payload);
                appendLog(preparation.logPath(), cleanup.log());
            }
        } catch (Exception exception) {
            markFailed(executionId, null, exception.getMessage());
        }
    }

    private RemoteRunnerResult waitForCompletion(Map<String, Object> payload) throws InterruptedException {
        Instant lastResponseAt = Instant.now();
        while (true) {
            RemoteRunnerResult poll = remoteRunnerClient.pollRun(payload);
            if (poll.ok()) {
                lastResponseAt = Instant.now();
                if ("running".equals(poll.message())) {
                    TimeUnit.MILLISECONDS.sleep(pollInterval.toMillis());
                    continue;
                }
                if ("finished".equals(poll.message())) {
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
            PersistentTaskExecutionRecord execution = executionRepository.findById(executionId)
                    .orElseThrow(() -> new ExecutionValidationException("execution does not exist"));
            if (execution.getStatus() != ExecutionStatus.QUEUED) {
                return null;
            }
            PersistentTestTaskRecord task = taskRepository.findById(execution.getTaskId())
                    .orElseThrow(() -> new ExecutionValidationException("task does not exist"));
            PersistentScriptVersionRecord script = scriptVersionRepository.findById(task.getScriptVersionId())
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
                    .resolve(String.valueOf(task.getProjectId()))
                    .resolve(String.valueOf(task.getId()))
                    .resolve(String.valueOf(execution.getId()));
            String filename = sanitizeFilename(script.getOriginalFilename());
            return new DistributedExecutionPreparation(
                    "execution-" + execution.getId(),
                    Path.of(script.getStoredPath()),
                    executionDirectory,
                    executionDirectory.resolve(filename),
                    executionDirectory.resolve("distributed-" + filename),
                    executionDirectory.resolve("result.jtl"),
                    executionDirectory.resolve("failure-result.jtl"),
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
                "resultPath", preparation.resultPath().toString(),
                "failureResultPath", preparation.failureResultPath().toString(),
                "logPath", preparation.logPath().toString(),
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
            PersistentTaskExecutionRecord execution = executionRepository.findById(executionId).orElseThrow();
            PersistentTestTaskRecord task = taskRepository.findById(execution.getTaskId()).orElseThrow();
            execution.markRunning(resultPath.toString(), logPath.toString());
            task.changeStatus(ExecutionStatus.RUNNING);
        });
    }

    private void markSuccess(long executionId) {
        transactionTemplate.executeWithoutResult(status -> {
            PersistentTaskExecutionRecord execution = executionRepository.findById(executionId).orElseThrow();
            PersistentTestTaskRecord task = taskRepository.findById(execution.getTaskId()).orElseThrow();
            execution.markSuccess(0);
            task.changeStatus(ExecutionStatus.SUCCESS);
        });
    }

    private void markFailed(long executionId, Integer exitCode, String message) {
        transactionTemplate.executeWithoutResult(status -> {
            PersistentTaskExecutionRecord execution = executionRepository.findById(executionId).orElse(null);
            if (execution == null) {
                return;
            }
            PersistentTestTaskRecord task = taskRepository.findById(execution.getTaskId()).orElse(null);
            execution.markFailed(exitCode, normalizeMessage(message));
            if (task != null) {
                task.changeStatus(ExecutionStatus.FAILED);
            }
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
            Path resultPath,
            Path failureResultPath,
            Path logPath,
            PersistentExecutionNodeRecord controller,
            List<PersistentExecutionNodeRecord> workers
    ) {
    }
}
