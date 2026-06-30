package com.yr.perftest.platform.task;

import com.yr.perftest.platform.execution.ExecutionValidationException;
import com.yr.perftest.platform.script.PersistentScriptVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class TaskScenarioService {
    private final PersistentTaskPlanRepository planRepository;
    private final PersistentTaskScenarioRepository scenarioRepository;
    private final PersistentScenarioExecutionRepository executionRepository;
    private final PersistentScriptVersionRepository scriptVersionRepository;
    private final TaskJsonSupport taskJson;

    public TaskScenarioService(
            PersistentTaskPlanRepository planRepository,
            PersistentTaskScenarioRepository scenarioRepository,
            PersistentScenarioExecutionRepository executionRepository,
            PersistentScriptVersionRepository scriptVersionRepository,
            TaskJsonSupport taskJson
    ) {
        this.planRepository = planRepository;
        this.scenarioRepository = scenarioRepository;
        this.executionRepository = executionRepository;
        this.scriptVersionRepository = scriptVersionRepository;
        this.taskJson = taskJson;
    }

    @Transactional
    public TaskScenario createScenario(
            long planId,
            long scriptVersionId,
            String name,
            Map<String, String> jmeterProperties,
            Long controllerNodeId,
            List<Long> workerNodeIds,
            List<Long> monitorTargetIds
    ) {
        PersistentTaskPlanRecord plan = requirePlan(planId);
        validateScript(plan.getProjectId(), scriptVersionId);
        validateName(name);
        int sortOrder = (int) scenarioRepository.countByPlanId(planId);
        PersistentTaskScenarioRecord scenario = scenarioRepository.save(new PersistentTaskScenarioRecord(
                planId,
                scriptVersionId,
                name.trim(),
                sortOrder
        ));
        applyScenarioProfile(scenario, name, scriptVersionId, jmeterProperties,
                controllerNodeId, workerNodeIds, monitorTargetIds);
        return toScenario(scenario);
    }

    @Transactional
    public TaskScenario updateScenario(
            long scenarioId,
            String name,
            Long scriptVersionId,
            Map<String, String> jmeterProperties,
            Long controllerNodeId,
            List<Long> workerNodeIds,
            List<Long> monitorTargetIds,
            boolean overridePlanDefaults
    ) {
        PersistentTaskScenarioRecord scenario = requireScenario(scenarioId);
        PersistentTaskPlanRecord plan = requirePlan(scenario.getPlanId());
        if (scriptVersionId != null) {
            validateScript(plan.getProjectId(), scriptVersionId);
        }
        Long resolvedController = overridePlanDefaults ? controllerNodeId : null;
        String resolvedWorkers = overridePlanDefaults && workerNodeIds != null
                ? taskJson.writeLongList(workerNodeIds) : null;
        String resolvedMonitors = overridePlanDefaults && monitorTargetIds != null
                ? taskJson.writeLongList(monitorTargetIds) : null;
        scenario.updateProfile(
                name,
                scriptVersionId,
                taskJson.writeStringMap(jmeterProperties),
                resolvedController,
                resolvedWorkers,
                resolvedMonitors
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
            PersistentTaskScenarioRecord scenario,
            String name,
            Long scriptVersionId,
            Map<String, String> jmeterProperties,
            Long controllerNodeId,
            List<Long> workerNodeIds,
            List<Long> monitorTargetIds
    ) {
        scenario.updateProfile(
                name,
                scriptVersionId,
                taskJson.writeStringMap(jmeterProperties),
                controllerNodeId,
                workerNodeIds != null ? taskJson.writeLongList(workerNodeIds) : null,
                monitorTargetIds != null ? taskJson.writeLongList(monitorTargetIds) : null
        );
    }

    private TaskScenario toScenario(PersistentTaskScenarioRecord scenario) {
        var latest = executionRepository.findFirstByScenarioIdOrderByIdDesc(scenario.getId()).orElse(null);
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
                latest != null ? latest.getStatus() : null,
                latest != null ? (latest.getStartTime() != null ? latest.getStartTime() : latest.getCreatedAt()) : null,
                scenario.getCreatedAt(),
                scenario.getUpdatedAt()
        );
    }

    private PersistentTaskPlanRecord requirePlan(long planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new ExecutionValidationException("task plan does not exist"));
    }

    private void validateScript(long projectId, long scriptVersionId) {
        if (!scriptVersionRepository.existsByIdAndProjectId(scriptVersionId, projectId)) {
            throw new ExecutionValidationException("script version does not exist");
        }
    }

    private void validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new ExecutionValidationException("scenario name is required");
        }
    }
}
