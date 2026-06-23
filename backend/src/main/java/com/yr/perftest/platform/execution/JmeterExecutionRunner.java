package com.yr.perftest.platform.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class JmeterExecutionRunner {
    private final PersistentTestTaskRepository taskRepository;
    private final PersistentTaskExecutionRepository executionRepository;
    private final PersistentScriptVersionRepository scriptVersionRepository;
    private final JmeterCommandExecutor jmeterCommandExecutor;
    private final JmeterResultRetainer resultRetainer;
    private final JmeterScriptNormalizer scriptNormalizer;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final Path storageRoot;
    private final ExecutorService executorService;

    public JmeterExecutionRunner(
            PersistentTestTaskRepository taskRepository,
            PersistentTaskExecutionRepository executionRepository,
            PersistentScriptVersionRepository scriptVersionRepository,
            JmeterCommandExecutor jmeterCommandExecutor,
            JmeterResultRetainer resultRetainer,
            JmeterScriptNormalizer scriptNormalizer,
            TransactionTemplate transactionTemplate,
            ObjectMapper objectMapper,
            @Value("${platform.storage.root:./storage}") String storageRoot,
            @Value("${platform.execution.max-concurrent-tasks:1}") int maxConcurrentTasks
    ) {
        this.taskRepository = taskRepository;
        this.executionRepository = executionRepository;
        this.scriptVersionRepository = scriptVersionRepository;
        this.jmeterCommandExecutor = jmeterCommandExecutor;
        this.resultRetainer = resultRetainer;
        this.scriptNormalizer = scriptNormalizer;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
        this.storageRoot = Path.of(storageRoot);
        this.executorService = Executors.newFixedThreadPool(Math.max(1, maxConcurrentTasks), runnable -> {
            Thread thread = new Thread(runnable, "jmeter-execution-runner");
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
            ExecutionPreparation preparation = loadPreparation(executionId);
            if (preparation == null) {
                return;
            }
            Files.createDirectories(preparation.executionDirectory());
            scriptNormalizer.copyNormalized(preparation.sourcePath(), preparation.testPlanPath());
            Files.writeString(
                    preparation.logPath(),
                    "",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            markRunning(executionId, preparation.resultPath(), preparation.logPath());
            int exitCode = jmeterCommandExecutor.execute(
                    preparation.testPlanPath(),
                    preparation.resultPath(),
                    preparation.logPath(),
                    preparation.config()
            );
            resultRetainer.retainRecentFailures(preparation.resultPath(), preparation.failureResultPath());
            if (exitCode == 0) {
                markSuccess(executionId);
            } else {
                markFailed(
                        executionId,
                        exitCode,
                        ExecutionFailureSummarizer.summarize("JMeter exited with code " + exitCode, preparation.logPath())
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            markFailed(executionId, null, "JMeter execution interrupted");
        } catch (Exception exception) {
            markFailed(executionId, null, exception.getMessage());
        }
    }

    private ExecutionPreparation loadPreparation(long executionId) {
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
            Path executionDirectory = storageRoot
                    .resolve("executions")
                    .resolve(String.valueOf(task.getProjectId()))
                    .resolve(String.valueOf(task.getId()))
                    .resolve(String.valueOf(execution.getId()));
            Path testPlanPath = executionDirectory.resolve(sanitizeFilename(script.getOriginalFilename()));
            return new ExecutionPreparation(
                    task.getId(),
                    Path.of(script.getStoredPath()),
                    executionDirectory,
                    testPlanPath,
                    executionDirectory.resolve("result.jtl"),
                    executionDirectory.resolve("failure-result.jtl"),
                    executionDirectory.resolve("jmeter.log"),
                    config
            );
        });
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

    private String normalizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "JMeter execution failed";
        }
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }

    private String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private record ExecutionPreparation(
            long taskId,
            Path sourcePath,
            Path executionDirectory,
            Path testPlanPath,
            Path resultPath,
            Path failureResultPath,
            Path logPath,
            ExecutionConfig config
    ) {
    }
}
