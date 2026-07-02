package com.yr.perftest.platform.task;

import com.yr.perftest.platform.execution.ExecutionStatus;
import com.yr.perftest.platform.execution.ExecutionValidationException;
import com.yr.perftest.platform.execution.TaskExecutionResult;
import com.yr.perftest.platform.execution.aggregate.AggregateReportService;
import com.yr.perftest.platform.script.PersistentScriptVersionRecord;
import com.yr.perftest.platform.script.PersistentScriptVersionRepository;
import com.yr.perftest.platform.script.ScriptStepDefinition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class TaskScenarioService {
    private final PersistentTaskPlanRepository planRepository;
    private final PersistentTaskScenarioRepository scenarioRepository;
    private final PersistentScenarioExecutionRepository executionRepository;
    private final PersistentScriptVersionRepository scriptVersionRepository;
    private final TaskJsonSupport taskJson;
    private final ScenarioThreadGroupConfigSupport configSupport;
    private final AggregateReportService aggregateReportService;

    public TaskScenarioService(
            PersistentTaskPlanRepository planRepository,
            PersistentTaskScenarioRepository scenarioRepository,
            PersistentScenarioExecutionRepository executionRepository,
            PersistentScriptVersionRepository scriptVersionRepository,
            TaskJsonSupport taskJson,
            ScenarioThreadGroupConfigSupport configSupport,
            AggregateReportService aggregateReportService
    ) {
        this.planRepository = planRepository;
        this.scenarioRepository = scenarioRepository;
        this.executionRepository = executionRepository;
        this.scriptVersionRepository = scriptVersionRepository;
        this.taskJson = taskJson;
        this.configSupport = configSupport;
        this.aggregateReportService = aggregateReportService;
    }

    @Transactional
    public TaskScenario createScenario(
            long planId,
            long scriptVersionId,
            String name,
            Map<String, String> jmeterProperties,
            List<ScenarioThreadGroupConfig> threadGroupConfigs,
            Long controllerNodeId,
            List<Long> workerNodeIds,
            List<Long> monitorTargetIds
    ) {
        PersistentTaskPlanRecord plan = requirePlan(planId);
        PersistentScriptVersionRecord script = validateScript(plan.getProjectId(), scriptVersionId);
        validateName(name);
        int sortOrder = (int) scenarioRepository.countByPlanId(planId);
        PersistentTaskScenarioRecord scenario = scenarioRepository.save(new PersistentTaskScenarioRecord(
                planId,
                scriptVersionId,
                name.trim(),
                sortOrder
        ));
        applyScenarioProfile(
                plan.getProjectId(),
                scenario,
                name,
                scriptVersionId,
                jmeterProperties,
                threadGroupConfigs,
                controllerNodeId,
                workerNodeIds,
                monitorTargetIds
        );
        return toScenario(scenario);
    }

    @Transactional
    public TaskScenario updateScenario(
            long scenarioId,
            String name,
            Long scriptVersionId,
            Map<String, String> jmeterProperties,
            List<ScenarioThreadGroupConfig> threadGroupConfigs,
            Long controllerNodeId,
            List<Long> workerNodeIds,
            List<Long> monitorTargetIds,
            boolean overridePlanDefaults
    ) {
        PersistentTaskScenarioRecord scenario = requireScenario(scenarioId);
        PersistentTaskPlanRecord plan = requirePlan(scenario.getPlanId());
        long resolvedScriptVersionId = scriptVersionId != null ? scriptVersionId : scenario.getScriptVersionId();
        if (scriptVersionId != null) {
            validateScript(plan.getProjectId(), scriptVersionId);
        }
        Long resolvedController = overridePlanDefaults ? controllerNodeId : null;
        String resolvedWorkers = overridePlanDefaults && workerNodeIds != null
                ? taskJson.writeLongList(workerNodeIds) : null;
        String resolvedMonitors = overridePlanDefaults && monitorTargetIds != null
                ? taskJson.writeLongList(monitorTargetIds) : null;
        List<ScenarioThreadGroupConfig> resolvedConfigs = threadGroupConfigs;
        if (resolvedConfigs != null) {
            PersistentScriptVersionRecord script = scriptVersionRepository.findById(resolvedScriptVersionId).orElseThrow();
            resolvedConfigs = configSupport.normalize(Path.of(script.getStoredPath()), resolvedConfigs);
        }
        scenario.updateProfile(
                name,
                scriptVersionId,
                taskJson.writeStringMap(jmeterProperties),
                resolvedController,
                resolvedWorkers,
                resolvedMonitors,
                resolvedConfigs != null ? configSupport.writeStored(resolvedConfigs) : null
        );
        return toScenario(scenario);
    }

    @Transactional(readOnly = true)
    public List<TaskScenario> listScenarios(long planId) {
        requirePlan(planId);
        return scenarioRepository.findAllByPlanIdOrderBySortOrderAscIdAsc(planId).stream()
                .map(this::toScenario)
                .toList();
    }

    @Transactional(readOnly = true)
    public TaskScenario getScenario(long scenarioId) {
        return toScenario(requireScenario(scenarioId));
    }

    @Transactional
    public void deleteScenario(long scenarioId) {
        PersistentTaskScenarioRecord scenario = requireScenario(scenarioId);
        executionRepository.findAllByScenarioIdOrderByIdDesc(scenario.getId()).forEach(execution -> {
            if (execution.getStatus() == com.yr.perftest.platform.execution.ExecutionStatus.RUNNING
                    || execution.getStatus() == com.yr.perftest.platform.execution.ExecutionStatus.STOPPING
                    || execution.getStatus() == com.yr.perftest.platform.execution.ExecutionStatus.QUEUED) {
                throw new ExecutionValidationException("running scenario cannot be deleted");
            }
        });
        executionRepository.deleteAllByScenarioId(scenario.getId());
        scenarioRepository.delete(scenario);
    }

    PersistentTaskScenarioRecord requireScenario(long scenarioId) {
        return scenarioRepository.findById(scenarioId)
                .orElseThrow(() -> new ExecutionValidationException("scenario does not exist"));
    }

    private void applyScenarioProfile(
            long projectId,
            PersistentTaskScenarioRecord scenario,
            String name,
            Long scriptVersionId,
            Map<String, String> jmeterProperties,
            List<ScenarioThreadGroupConfig> threadGroupConfigs,
            Long controllerNodeId,
            List<Long> workerNodeIds,
            List<Long> monitorTargetIds
    ) {
        long resolvedScriptVersionId = scriptVersionId != null ? scriptVersionId : scenario.getScriptVersionId();
        PersistentScriptVersionRecord script = validateScript(projectId, resolvedScriptVersionId);
        List<ScenarioThreadGroupConfig> normalized = configSupport.normalize(
                Path.of(script.getStoredPath()),
                threadGroupConfigs == null ? List.of() : threadGroupConfigs
        );
        scenario.updateProfile(
                name,
                scriptVersionId,
                taskJson.writeStringMap(jmeterProperties),
                controllerNodeId,
                workerNodeIds != null ? taskJson.writeLongList(workerNodeIds) : null,
                monitorTargetIds != null ? taskJson.writeLongList(monitorTargetIds) : null,
                configSupport.writeStored(normalized)
        );
    }

    private TaskScenario toScenario(PersistentTaskScenarioRecord scenario) {
        var latest = executionRepository.findFirstByScenarioIdOrderByIdDesc(scenario.getId()).orElse(null);
        List<ScenarioThreadGroupConfig> configs = withLatestSummaries(
                scenario,
                configSupport.readStored(scenario.getThreadGroupConfigsJson())
        );
        return new TaskScenario(
                scenario.getId(),
                scenario.getPlanId(),
                scenario.getScriptVersionId(),
                scenario.getName(),
                scenario.getSortOrder(),
                scenario.getThreads(),
                scenario.getRampUp(),
                scenario.getDuration(),
                scenario.getLoops(),
                taskJson.readStringMap(scenario.getJmeterPropertiesJson()),
                scenario.getControllerNodeId(),
                scenario.getWorkerNodeIdsJson() != null ? taskJson.readLongList(scenario.getWorkerNodeIdsJson()) : null,
                scenario.getMonitorTargetIdsJson() != null ? taskJson.readLongList(scenario.getMonitorTargetIdsJson()) : null,
                configs,
                latest != null ? latest.getStatus() : null,
                latest != null ? (latest.getStartTime() != null ? latest.getStartTime() : latest.getCreatedAt()) : null,
                scenario.getCreatedAt(),
                scenario.getUpdatedAt()
        );
    }

    private List<ScenarioThreadGroupConfig> withLatestSummaries(
            PersistentTaskScenarioRecord scenario,
            List<ScenarioThreadGroupConfig> configs
    ) {
        if (configs.isEmpty()) {
            return List.of();
        }
        List<ScriptStepDefinition> scriptSteps = List.of();
        try {
            PersistentScriptVersionRecord script = scriptVersionRepository.findById(scenario.getScriptVersionId()).orElse(null);
            if (script != null) {
                scriptSteps = configSupport.loadScriptSteps(Path.of(script.getStoredPath()));
            }
        } catch (ExecutionValidationException exception) {
            scriptSteps = List.of();
        }
        List<ScenarioThreadGroupConfig> enriched = new ArrayList<>(configs.size());
        for (ScenarioThreadGroupConfig config : configs) {
            enriched.add(new ScenarioThreadGroupConfig(
                    config.id(),
                    config.stepId(),
                    config.stepName(),
                    config.threads(),
                    config.rampUp(),
                    config.duration(),
                    config.sortOrder(),
                    loadLatestSummary(scenario.getId(), scriptSteps, configs, config)
            ));
        }
        return enriched;
    }

    private ThreadGroupConfigSummary loadLatestSummary(
            long scenarioId,
            List<ScriptStepDefinition> scriptSteps,
            List<ScenarioThreadGroupConfig> allConfigs,
            ScenarioThreadGroupConfig config
    ) {
        long presetSize = allConfigs.stream().filter(item -> item.sortOrder() == config.sortOrder()).count();
        for (PersistentScenarioExecutionRecord execution : executionRepository.findAllByScenarioIdOrderByIdDesc(scenarioId)) {
            if (!isFinished(execution.getStatus())) {
                continue;
            }
            if (!matchesThreadGroupConfig(execution.getConfigJson(), config)) {
                continue;
            }
            TaskExecutionResult result = aggregateReportService.loadPersisted(execution.getId())
                    .orElse(TaskExecutionResult.empty());
            if (result.summary() == null || result.summary().samples() <= 0) {
                continue;
            }
            if (presetSize > 1 && !scriptSteps.isEmpty()) {
                return configSupport.summarizeThreadGroupResult(scriptSteps, config, result);
            }
            TaskExecutionResult.Summary summary = result.summary();
            return new ThreadGroupConfigSummary(
                    summary.samples(),
                    summary.throughput(),
                    summary.avgRt(),
                    summary.errorRate()
            );
        }
        return null;
    }

    private boolean matchesThreadGroupConfig(String configJson, ScenarioThreadGroupConfig config) {
        try {
            var root = taskJson.objectMapper().readTree(configJson);
            var presetNode = root.get("threadGroupPresetSortOrder");
            if (presetNode != null && !presetNode.isNull()) {
                return presetNode.asInt() == config.sortOrder();
            }
            var configIdNode = root.get("threadGroupConfigId");
            if (configIdNode != null && !configIdNode.isNull()) {
                return configIdNode.asLong() == config.id();
            }
            return false;
        } catch (Exception exception) {
            return false;
        }
    }

    private boolean isFinished(ExecutionStatus status) {
        return status == ExecutionStatus.SUCCESS
                || status == ExecutionStatus.FAILED
                || status == ExecutionStatus.CANCELLED
                || status == ExecutionStatus.INTERRUPTED;
    }

    private PersistentTaskPlanRecord requirePlan(long planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new ExecutionValidationException("task plan does not exist"));
    }

    private PersistentScriptVersionRecord validateScript(long projectId, long scriptVersionId) {
        PersistentScriptVersionRecord script = scriptVersionRepository.findById(scriptVersionId)
                .orElseThrow(() -> new ExecutionValidationException("script version does not exist"));
        if (!script.getProjectId().equals(projectId)) {
            throw new ExecutionValidationException("script version does not exist");
        }
        return script;
    }

    private void validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new ExecutionValidationException("scenario name is required");
        }
    }
}
