package com.yr.perftest.platform.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.execution.ExecutionConfig;
import com.yr.perftest.platform.execution.ExecutionMode;
import com.yr.perftest.platform.execution.ExecutionStatus;
import com.yr.perftest.platform.execution.ExecutionValidationException;
import com.yr.perftest.platform.execution.aggregate.AggregateReportService;
import com.yr.perftest.platform.execution.distributed.DistributedJmeterExecutionRunner;
import com.yr.perftest.platform.execution.failure.FailureSamplePaths;
import com.yr.perftest.platform.monitoring.ExecutionMonitorBindingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 执行写模型：触发、停止、删除。查询与实时流见 {@link ExecutionQueryService}；
 * 对外控制语义（幂等/审计/冲突）见 {@link ExecutionControlService}。
 */
@Service
public class ScenarioExecutionService {
    private final PersistentTaskPlanRepository planRepository;
    private final PersistentTaskScenarioRepository scenarioRepository;
    private final PersistentScenarioExecutionRepository executionRepository;
    private final com.yr.perftest.platform.auxscript.AuxScriptLifecycle auxScriptLifecycle;
    private final ExecutionConfigMerger configMerger;
    private final ExecutionMonitorBindingService monitorBindingService;
    private final DistributedJmeterExecutionRunner distributedJmeterExecutionRunner;
    private final ScenarioExecutionRuntime executionRuntime;
    private final AggregateReportService aggregateReportService;
    private final ObjectMapper objectMapper;

    public ScenarioExecutionService(
            PersistentTaskPlanRepository planRepository,
            PersistentTaskScenarioRepository scenarioRepository,
            PersistentScenarioExecutionRepository executionRepository,
            com.yr.perftest.platform.auxscript.AuxScriptLifecycle auxScriptLifecycle,
            ExecutionConfigMerger configMerger,
            ExecutionMonitorBindingService monitorBindingService,
            DistributedJmeterExecutionRunner distributedJmeterExecutionRunner,
            ScenarioExecutionRuntime executionRuntime,
            AggregateReportService aggregateReportService,
            ObjectMapper objectMapper
    ) {
        this.planRepository = planRepository;
        this.scenarioRepository = scenarioRepository;
        this.executionRepository = executionRepository;
        this.auxScriptLifecycle = auxScriptLifecycle;
        this.configMerger = configMerger;
        this.monitorBindingService = monitorBindingService;
        this.distributedJmeterExecutionRunner = distributedJmeterExecutionRunner;
        this.executionRuntime = executionRuntime;
        this.aggregateReportService = aggregateReportService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public long triggerExecution(
            long scenarioId,
            String executionName,
            Long threadGroupConfigId,
            Integer threadGroupPresetSortOrder
    ) {
        PersistentTaskScenarioRecord scenario = scenarioRepository.findById(scenarioId)
                .orElseThrow(() -> new ExecutionValidationException("scenario does not exist"));
        PersistentTaskPlanRecord plan = planRepository.findById(scenario.getPlanId())
                .orElseThrow(() -> new ExecutionValidationException("task plan does not exist"));
        ExecutionConfig config = normalizeConfig(configMerger.merge(plan, scenario, threadGroupConfigId, threadGroupPresetSortOrder));
        PersistentScenarioExecutionRecord execution = new PersistentScenarioExecutionRecord(
                scenario.getId(),
                writeConfig(config)
        );
        if (executionName != null && !executionName.isBlank()) {
            execution.setExecutionName(executionName.trim());
        }
        final PersistentScenarioExecutionRecord saved = executionRepository.save(execution);
        monitorBindingService.bindTargets(plan.getProjectId(), saved.getId(), config.monitorTargetIds());
        executionRuntime.register(saved.getId());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                auxScriptLifecycle.afterExecutionStarted(saved.getId());
                distributedJmeterExecutionRunner.submit(saved.getId());
            }
        });
        return saved.getId();
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
        aggregateReportService.deleteByExecutionId(executionId);
        FailureSamplePaths.deleteArtifacts(
                execution.getLogFilePath() == null ? null : Path.of(execution.getLogFilePath())
        );
        executionRepository.delete(execution);
    }

    @Transactional
    public void deleteExecutions(List<Long> executionIds) {
        for (Long executionId : executionIds) {
            deleteExecution(executionId);
        }
    }

    private PersistentScenarioExecutionRecord requireExecution(long executionId) {
        return executionRepository.findById(executionId)
                .orElseThrow(() -> new ExecutionValidationException("execution does not exist"));
    }

    private ExecutionConfig normalizeConfig(ExecutionConfig config) {
        ExecutionConfig source = config == null
                ? new ExecutionConfig(0, 0, 0, 0, Map.of(), ExecutionMode.DISTRIBUTED, null, List.of(), List.of(), null, null, null, null)
                : config;
        if (source.threads() < 0 || source.rampUp() < 0 || source.duration() < 0 || source.loops() < 0) {
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
                source.monitorTargetIds(),
                source.threadGroupConfigId(),
                source.threadGroupPresetSortOrder(),
                source.stepId(),
                source.stepName()
        );
    }

    private String writeConfig(ExecutionConfig config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (Exception exception) {
            throw new ExecutionValidationException("execution config is invalid");
        }
    }
}
