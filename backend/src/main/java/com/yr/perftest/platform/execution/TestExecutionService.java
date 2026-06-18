package com.yr.perftest.platform.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.execution.distributed.DistributedJmeterExecutionRunner;
import com.yr.perftest.platform.project.PersistentProjectRepository;
import com.yr.perftest.platform.project.ProjectValidationException;
import com.yr.perftest.platform.script.PersistentScriptVersionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Service
public class TestExecutionService {
    private final PersistentProjectRepository projectRepository;
    private final PersistentScriptVersionRepository scriptVersionRepository;
    private final PersistentTestTaskRepository taskRepository;
    private final PersistentTaskExecutionRepository executionRepository;
    private final JmeterExecutionRunner jmeterExecutionRunner;
    private final DistributedJmeterExecutionRunner distributedJmeterExecutionRunner;
    private final JmeterResultParser jmeterResultParser;
    private final ObjectMapper objectMapper;
    private final String grafanaPanelUrl;

    public TestExecutionService(
            PersistentProjectRepository projectRepository,
            PersistentScriptVersionRepository scriptVersionRepository,
            PersistentTestTaskRepository taskRepository,
            PersistentTaskExecutionRepository executionRepository,
            JmeterExecutionRunner jmeterExecutionRunner,
            DistributedJmeterExecutionRunner distributedJmeterExecutionRunner,
            JmeterResultParser jmeterResultParser,
            ObjectMapper objectMapper,
            @Value("${platform.distributed.grafana-panel-url:http://127.0.0.1:3000/d/jmeter-5496/jmeter-load-test?orgId=1&refresh=5s}") String grafanaPanelUrl
    ) {
        this.projectRepository = projectRepository;
        this.scriptVersionRepository = scriptVersionRepository;
        this.taskRepository = taskRepository;
        this.executionRepository = executionRepository;
        this.jmeterExecutionRunner = jmeterExecutionRunner;
        this.distributedJmeterExecutionRunner = distributedJmeterExecutionRunner;
        this.jmeterResultParser = jmeterResultParser;
        this.objectMapper = objectMapper;
        this.grafanaPanelUrl = grafanaPanelUrl;
    }

    @Transactional
    public TestTask submitTask(
            long projectId,
            long scriptVersionId,
            String name,
            ExecutionConfig config,
            String remark,
            String createdBy
    ) {
        if (!projectRepository.existsById(projectId)) {
            throw new ProjectValidationException("project does not exist");
        }
        if (!scriptVersionRepository.existsByIdAndProjectId(scriptVersionId, projectId)) {
            throw new ExecutionValidationException("script version does not exist");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new ExecutionValidationException("task name is required");
        }
        ExecutionConfig normalizedConfig = normalizeConfig(config);
        PersistentTestTaskRecord task = taskRepository.save(new PersistentTestTaskRecord(
                projectId,
                scriptVersionId,
                name.trim(),
                remark,
                createdBy
        ));
        PersistentTaskExecutionRecord execution = executionRepository.save(new PersistentTaskExecutionRecord(
                task.getId(),
                writeConfig(normalizedConfig)
        ));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (normalizedConfig.mode() == ExecutionMode.DISTRIBUTED) {
                    distributedJmeterExecutionRunner.submit(execution.getId());
                } else {
                    jmeterExecutionRunner.submit(execution.getId());
                }
            }
        });
        return toTestTask(task, execution);
    }

    @Transactional(readOnly = true)
    public List<TestTask> listTasks(long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ProjectValidationException("project does not exist");
        }
        return taskRepository.findAllByProjectIdOrderByIdDesc(projectId).stream()
                .map(this::toTestTask)
                .toList();
    }

    @Transactional(readOnly = true)
    public TestTask getTask(long taskId) {
        return toTestTask(taskRepository.findById(taskId)
                .orElseThrow(() -> new ExecutionValidationException("task does not exist")));
    }

    @Transactional
    public void deleteTask(long taskId) {
        PersistentTestTaskRecord task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ExecutionValidationException("task does not exist"));
        if (task.getStatus() == ExecutionStatus.RUNNING) {
            throw new ExecutionValidationException("running task cannot be deleted");
        }
        executionRepository.deleteAllByTaskId(task.getId());
        taskRepository.delete(task);
    }

    @Transactional(readOnly = true)
    public String getTaskLogs(long taskId) {
        PersistentTestTaskRecord task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ExecutionValidationException("task does not exist"));
        PersistentTaskExecutionRecord execution = executionRepository.findFirstByTaskIdOrderByIdDesc(task.getId())
                .orElseThrow(() -> new ExecutionValidationException("execution does not exist"));
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
    public TaskExecutionResult getTaskResult(long taskId) {
        PersistentTestTaskRecord task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ExecutionValidationException("task does not exist"));
        PersistentTaskExecutionRecord execution = executionRepository.findFirstByTaskIdOrderByIdDesc(task.getId())
                .orElseThrow(() -> new ExecutionValidationException("execution does not exist"));
        if (execution.getResultFilePath() == null) {
            return TaskExecutionResult.empty();
        }
        return jmeterResultParser.parse(Path.of(execution.getResultFilePath()));
    }

    private TestTask toTestTask(PersistentTestTaskRecord task) {
        PersistentTaskExecutionRecord execution = executionRepository.findFirstByTaskIdOrderByIdDesc(task.getId())
                .orElseThrow(() -> new ExecutionValidationException("execution does not exist"));
        return toTestTask(task, execution);
    }

    private TestTask toTestTask(PersistentTestTaskRecord task, PersistentTaskExecutionRecord execution) {
        ExecutionConfig config = readConfig(execution.getConfigJson());
        return new TestTask(
                task.getId(),
                execution.getId(),
                task.getProjectId(),
                task.getScriptVersionId(),
                task.getName(),
                task.getStatus(),
                config,
                task.getRemark(),
                task.getCreatedBy(),
                task.getCreatedAt(),
                execution.getStartTime(),
                execution.getEndTime(),
                execution.getDurationMs(),
                execution.getResultFilePath(),
                execution.getLogFilePath(),
                execution.getErrorMessage(),
                grafanaUrl(execution.getId(), config)
        );
    }

    private ExecutionConfig normalizeConfig(ExecutionConfig config) {
        ExecutionConfig source = config == null
                ? new ExecutionConfig(1, 0, 0, 1, "SIT", Map.of(), ExecutionMode.LOCAL, null, List.of())
                : config;
        if (source.threads() <= 0) {
            throw new ExecutionValidationException("threads must be greater than 0");
        }
        if (source.rampUp() < 0 || source.duration() < 0 || source.loops() < 0) {
            throw new ExecutionValidationException("execution config cannot be negative");
        }
        source.jmeterProperties().keySet().forEach(key -> {
            if (key == null || key.isBlank()) {
                throw new ExecutionValidationException("jmeter property key is required");
            }
        });
        String environment = source.environment() == null || source.environment().isBlank()
                ? "SIT"
                : source.environment().trim();
        return new ExecutionConfig(
                source.threads(),
                source.rampUp(),
                source.duration(),
                source.loops(),
                environment,
                source.jmeterProperties(),
                source.mode(),
                source.controllerNodeId(),
                source.workerNodeIds()
        );
    }

    private String writeConfig(ExecutionConfig config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (Exception exception) {
            throw new ExecutionValidationException("execution config is invalid");
        }
    }

    private ExecutionConfig readConfig(String configJson) {
        try {
            return objectMapper.readValue(configJson, ExecutionConfig.class);
        } catch (Exception exception) {
            throw new ExecutionValidationException("execution config is invalid");
        }
    }

    private String grafanaUrl(long executionId, ExecutionConfig config) {
        if (config.mode() != ExecutionMode.DISTRIBUTED) {
            return null;
        }
        String separator = grafanaPanelUrl.contains("?") ? "&" : "?";
        return grafanaPanelUrl + separator + "var-application=execution-" + executionId;
    }
}
