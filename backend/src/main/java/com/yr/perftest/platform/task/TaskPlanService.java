package com.yr.perftest.platform.task;

import com.yr.perftest.platform.execution.ExecutionValidationException;
import com.yr.perftest.platform.project.PersistentProjectRepository;
import com.yr.perftest.platform.project.ProjectValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TaskPlanService {
    private final PersistentProjectRepository projectRepository;
    private final PersistentTaskPlanRepository planRepository;
    private final PersistentTaskScenarioRepository scenarioRepository;
    private final PersistentScenarioExecutionRepository executionRepository;
    private final TaskJsonSupport taskJson;

    public TaskPlanService(
            PersistentProjectRepository projectRepository,
            PersistentTaskPlanRepository planRepository,
            PersistentTaskScenarioRepository scenarioRepository,
            PersistentScenarioExecutionRepository executionRepository,
            TaskJsonSupport taskJson
    ) {
        this.projectRepository = projectRepository;
        this.planRepository = planRepository;
        this.scenarioRepository = scenarioRepository;
        this.executionRepository = executionRepository;
        this.taskJson = taskJson;
    }

    @Transactional
    public TaskPlan createPlan(
            long projectId,
            String name,
            String remark,
            Long defaultControllerNodeId,
            List<Long> defaultWorkerNodeIds,
            List<Long> defaultMonitorTargetIds,
            String createdBy
    ) {
        validateProject(projectId);
        validateName(name);
        PersistentTaskPlanRecord plan = planRepository.save(new PersistentTaskPlanRecord(projectId, name.trim(), remark, createdBy));
        plan.updateProfile(
                name,
                remark,
                defaultControllerNodeId,
                taskJson.writeLongList(defaultWorkerNodeIds),
                taskJson.writeLongList(defaultMonitorTargetIds)
        );
        return toPlan(plan);
    }

    @Transactional
    public TaskPlan updatePlan(
            long planId,
            String name,
            String remark,
            Long defaultControllerNodeId,
            List<Long> defaultWorkerNodeIds,
            List<Long> defaultMonitorTargetIds
    ) {
        PersistentTaskPlanRecord plan = planRepository.findById(planId)
                .orElseThrow(() -> new ExecutionValidationException("task plan does not exist"));
        plan.updateProfile(
                name,
                remark,
                defaultControllerNodeId,
                defaultWorkerNodeIds != null ? taskJson.writeLongList(defaultWorkerNodeIds) : plan.getDefaultWorkerNodeIdsJson(),
                defaultMonitorTargetIds != null ? taskJson.writeLongList(defaultMonitorTargetIds) : plan.getDefaultMonitorTargetIdsJson()
        );
        return toPlan(plan);
    }

    @Transactional(readOnly = true)
    public List<TaskPlan> listPlans(long projectId) {
        validateProject(projectId);
        return planRepository.findAllByProjectIdOrderByIdDesc(projectId).stream()
                .map(this::toPlan)
                .toList();
    }

    @Transactional(readOnly = true)
    public TaskPlan getPlan(long planId) {
        return toPlan(planRepository.findById(planId)
                .orElseThrow(() -> new ExecutionValidationException("task plan does not exist")));
    }

    @Transactional
    public void deletePlan(long planId) {
        PersistentTaskPlanRecord plan = planRepository.findById(planId)
                .orElseThrow(() -> new ExecutionValidationException("task plan does not exist"));
        scenarioRepository.findAllByPlanIdOrderBySortOrderAscIdAsc(plan.getId()).forEach(scenario ->
                executionRepository.deleteAllByScenarioId(scenario.getId()));
        scenarioRepository.deleteAllByPlanId(plan.getId());
        planRepository.delete(plan);
    }

    PersistentTaskPlanRecord requirePlan(long planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new ExecutionValidationException("task plan does not exist"));
    }

    private TaskPlan toPlan(PersistentTaskPlanRecord plan) {
        return new TaskPlan(
                plan.getId(),
                plan.getProjectId(),
                plan.getName(),
                plan.getRemark(),
                plan.getCreatedBy(),
                plan.getCreatedAt(),
                plan.getUpdatedAt(),
                plan.getDefaultControllerNodeId(),
                taskJson.readLongList(plan.getDefaultWorkerNodeIdsJson()),
                taskJson.readLongList(plan.getDefaultMonitorTargetIdsJson()),
                scenarioRepository.countByPlanId(plan.getId())
        );
    }

    private void validateProject(long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ProjectValidationException("project does not exist");
        }
    }

    private void validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new ExecutionValidationException("task plan name is required");
        }
    }
}
