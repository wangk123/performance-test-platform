package com.yr.perftest.platform.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.execution.ExecutionConfig;
import com.yr.perftest.platform.execution.ExecutionMode;
import com.yr.perftest.platform.execution.ExecutionStatus;
import com.yr.perftest.platform.execution.ExecutionValidationException;
import com.yr.perftest.platform.execution.InfluxdbMonitoringClient;
import com.yr.perftest.platform.execution.JmeterResultParser;
import com.yr.perftest.platform.execution.TaskExecutionResult;
import com.yr.perftest.platform.execution.TaskMonitoringResult;
import com.yr.perftest.platform.execution.TaskSamplePage;
import com.yr.perftest.platform.execution.distributed.DistributedJmeterExecutionRunner;
import com.yr.perftest.platform.monitoring.ExecutionMonitorBindingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class ScenarioExecutionService {
    private final PersistentTaskPlanRepository planRepository;
    private final PersistentTaskScenarioRepository scenarioRepository;
    private final PersistentScenarioExecutionRepository executionRepository;
    private final ExecutionConfigMerger configMerger;
    private final ExecutionMonitorBindingService monitorBindingService;
    private final DistributedJmeterExecutionRunner distributedJmeterExecutionRunner;
    private final ScenarioExecutionRuntime executionRuntime;
    private final JmeterResultParser jmeterResultParser;
    private final InfluxdbMonitoringClient monitoringClient;
    private final ObjectMapper objectMapper;
    private final String grafanaPanelUrl;
    private final String influxdbMeasurement;

    public ScenarioExecutionService(
            PersistentTaskPlanRepository planRepository,
            PersistentTaskScenarioRepository scenarioRepository,
            PersistentScenarioExecutionRepository executionRepository,
            ExecutionConfigMerger configMerger,
            ExecutionMonitorBindingService monitorBindingService,
            DistributedJmeterExecutionRunner distributedJmeterExecutionRunner,
            ScenarioExecutionRuntime executionRuntime,
            JmeterResultParser jmeterResultParser,
            InfluxdbMonitoringClient monitoringClient,
            ObjectMapper objectMapper,
            @Value("${platform.distributed.grafana-panel-url:http://127.0.0.1:3000/d/jmeter-5496/jmeter-load-test?orgId=1&refresh=5s}") String grafanaPanelUrl,
            @Value("${platform.distributed.influxdb-measurement:jmeter_runtime}") String influxdbMeasurement
    ) {
        this.planRepository = planRepository;
        this.scenarioRepository = scenarioRepository;
        this.executionRepository = executionRepository;
        this.configMerger = configMerger;
        this.monitorBindingService = monitorBindingService;
        this.distributedJmeterExecutionRunner = distributedJmeterExecutionRunner;
        this.executionRuntime = executionRuntime;
        this.jmeterResultParser = jmeterResultParser;
        this.monitoringClient = monitoringClient;
        this.objectMapper = objectMapper;
        this.grafanaPanelUrl = grafanaPanelUrl;
        this.influxdbMeasurement = influxdbMeasurement;
    }

    @Transactional
    public ScenarioExecution triggerExecution(long scenarioId) {
        PersistentTaskScenarioRecord scenario = scenarioRepository.findById(scenarioId)
                .orElseThrow(() -> new ExecutionValidationException("scenario does not exist"));
        PersistentTaskPlanRecord plan = planRepository.findById(scenario.getPlanId())
                .orElseThrow(() -> new ExecutionValidationException("task plan does not exist"));
        ExecutionConfig config = normalizeConfig(configMerger.merge(plan, scenario));
        PersistentScenarioExecutionRecord execution = executionRepository.save(new PersistentScenarioExecutionRecord(
                scenario.getId(),
                writeConfig(config)
        ));
        monitorBindingService.bindTargets(plan.getProjectId(), execution.getId(), config.monitorTargetIds());
        executionRuntime.register(execution.getId());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                distributedJmeterExecutionRunner.submit(execution.getId());
            }
        });
        return toExecution(plan, scenario, execution);
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

    @Transactional
    public void stopExecution(long executionId) {
        PersistentScenarioExecutionRecord execution = requireExecution(executionId);
        if (execution.getStatus() != ExecutionStatus.RUNNING && execution.getStatus() != ExecutionStatus.QUEUED) {
            throw new ExecutionValidationException("execution cannot be stopped");
        }
        execution.markStopping();
        executionRuntime.requestStop(executionId);
    }

    @Transactional
    public void deleteExecution(long executionId) {
        PersistentScenarioExecutionRecord execution = requireExecution(executionId);
        if (execution.getStatus() == ExecutionStatus.RUNNING
                || execution.getStatus() == ExecutionStatus.STOPPING
                || execution.getStatus() == ExecutionStatus.QUEUED) {
            throw new ExecutionValidationException("running execution cannot be deleted");
        }
        monitorBindingService.deleteBindings(executionId);
        executionRepository.delete(execution);
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
        return monitoringClient.aggregate(execution.getId(), executionSeconds(execution));
    }

    @Transactional(readOnly = true)
    public TaskSamplePage getSamples(long executionId, int page, int pageSize) {
        PersistentScenarioExecutionRecord execution = requireExecution(executionId);
        if (execution.getResultFilePath() == null) {
            return new TaskSamplePage(Math.max(1, page), Math.max(1, Math.min(100, pageSize)), 0, List.of());
        }
        return jmeterResultParser.parseSamplePage(
                Path.of(execution.getResultFilePath()).resolveSibling("failure-result.jtl"),
                page,
                pageSize
        );
    }

    @Transactional(readOnly = true)
    public TaskMonitoringResult getMonitoring(long executionId) {
        requireExecution(executionId);
        return monitoringClient.query(executionId);
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
                grafanaUrl(execution, config)
        );
    }

    private ExecutionConfig normalizeConfig(ExecutionConfig config) {
        ExecutionConfig source = config == null
                ? new ExecutionConfig(1, 0, 0, 1, Map.of(), ExecutionMode.DISTRIBUTED, null, List.of(), List.of())
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
                workerNodeIds,
                source.monitorTargetIds()
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

    private String grafanaUrl(PersistentScenarioExecutionRecord execution, ExecutionConfig config) {
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
