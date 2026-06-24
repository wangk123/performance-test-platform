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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
public class TestExecutionService {
    private final PersistentProjectRepository projectRepository;
    private final PersistentScriptVersionRepository scriptVersionRepository;
    private final PersistentTestTaskRepository taskRepository;
    private final PersistentTaskExecutionRepository executionRepository;
    private final DistributedJmeterExecutionRunner distributedJmeterExecutionRunner;
    private final JmeterResultParser jmeterResultParser;
    private final InfluxdbMonitoringClient monitoringClient;
    private final ObjectMapper objectMapper;
    private final String grafanaPanelUrl;
    private final String influxdbMeasurement;

    public TestExecutionService(
            PersistentProjectRepository projectRepository,
            PersistentScriptVersionRepository scriptVersionRepository,
            PersistentTestTaskRepository taskRepository,
            PersistentTaskExecutionRepository executionRepository,
            DistributedJmeterExecutionRunner distributedJmeterExecutionRunner,
            JmeterResultParser jmeterResultParser,
            InfluxdbMonitoringClient monitoringClient,
            ObjectMapper objectMapper,
            @Value("${platform.distributed.grafana-panel-url:http://127.0.0.1:3000/d/jmeter-5496/jmeter-load-test?orgId=1&refresh=5s}") String grafanaPanelUrl,
            @Value("${platform.distributed.influxdb-measurement:jmeter_runtime}") String influxdbMeasurement
    ) {
        this.projectRepository = projectRepository;
        this.scriptVersionRepository = scriptVersionRepository;
        this.taskRepository = taskRepository;
        this.executionRepository = executionRepository;
        this.distributedJmeterExecutionRunner = distributedJmeterExecutionRunner;
        this.jmeterResultParser = jmeterResultParser;
        this.monitoringClient = monitoringClient;
        this.objectMapper = objectMapper;
        this.grafanaPanelUrl = grafanaPanelUrl;
        this.influxdbMeasurement = influxdbMeasurement;
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
                distributedJmeterExecutionRunner.submit(execution.getId());
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
        return monitoringClient.aggregate(execution.getId(), executionSeconds(execution));
    }

    @Transactional(readOnly = true)
    public TaskSamplePage getTaskSamples(long taskId, int page, int pageSize) {
        PersistentTestTaskRecord task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ExecutionValidationException("task does not exist"));
        PersistentTaskExecutionRecord execution = executionRepository.findFirstByTaskIdOrderByIdDesc(task.getId())
                .orElseThrow(() -> new ExecutionValidationException("execution does not exist"));
        if (execution.getResultFilePath() == null) {
            return new TaskSamplePage(Math.max(1, page), Math.max(1, Math.min(100, pageSize)), 0, List.of());
        }
        return jmeterResultParser.parseSamplePage(Path.of(execution.getResultFilePath()).resolveSibling("failure-result.jtl"), page, pageSize);
    }

    private double executionSeconds(PersistentTaskExecutionRecord execution) {
        if (execution.getDurationMs() != null && execution.getDurationMs() > 0) {
            return Math.max(1, execution.getDurationMs() / 1000.0);
        }
        if (execution.getStartTime() == null) {
            return 1;
        }
        long seconds = java.time.Duration.between(execution.getStartTime(), java.time.Instant.now()).getSeconds();
        return Math.max(1, seconds);
    }

    @Transactional(readOnly = true)
    public TaskMonitoringResult getTaskMonitoring(long taskId) {
        PersistentTestTaskRecord task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ExecutionValidationException("task does not exist"));
        PersistentTaskExecutionRecord execution = executionRepository.findFirstByTaskIdOrderByIdDesc(task.getId())
                .orElseThrow(() -> new ExecutionValidationException("execution does not exist"));
        return monitoringClient.query(execution.getId());
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
                grafanaUrl(execution, config)
        );
    }

    private ExecutionConfig normalizeConfig(ExecutionConfig config) {
        ExecutionConfig source = config == null
                ? new ExecutionConfig(1, 0, 0, 1, Map.of(), ExecutionMode.DISTRIBUTED, null, List.of())
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
        if (source.controllerNodeId() == null) {
            throw new ExecutionValidationException("controller node is required");
        }
        List<Long> workerNodeIds = source.workerNodeIds().isEmpty()
                ? List.of(source.controllerNodeId())
                : source.workerNodeIds();
        return new ExecutionConfig(
                source.threads(),
                source.rampUp(),
                source.duration(),
                source.loops(),
                source.jmeterProperties(),
                ExecutionMode.DISTRIBUTED,
                source.controllerNodeId(),
                workerNodeIds
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

    private String grafanaUrl(PersistentTaskExecutionRecord execution, ExecutionConfig config) {
        String timeRange = "";
        if (execution.getStartTime() != null && execution.getEndTime() != null) {
            long from = execution.getStartTime().minusSeconds(30).toEpochMilli();
            long to = execution.getEndTime().plusSeconds(30).toEpochMilli();
            timeRange = "&from=" + from + "&to=" + to;
        }
        String separator = grafanaPanelUrl.contains("?") ? "&" : "?";
        return grafanaPanelUrl
                + separator
                + "var-data_source="
                + URLEncoder.encode("JMeter InfluxDB", StandardCharsets.UTF_8)
                + "&var-measurement_name="
                + URLEncoder.encode(influxdbMeasurement, StandardCharsets.UTF_8)
                + "&var-application=execution-"
                + execution.getId()
                + timeRange;
    }
}
