package com.yr.perftest.platform.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.execution.ExecutionValidationException;
import com.yr.perftest.platform.project.PersistentProjectRepository;
import com.yr.perftest.platform.project.ProjectValidationException;
import com.yr.perftest.platform.task.plandoc.PersistentPlanTemplateRecord;
import com.yr.perftest.platform.task.plandoc.PersistentPlanTemplateRepository;
import com.yr.perftest.platform.task.plandoc.PlanMarkdownSupport;
import com.yr.perftest.platform.task.plandoc.PlanValidationException;
import com.yr.perftest.platform.task.plandoc.PrecheckSettings;
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
    private final PersistentPlanTemplateRepository templateRepository;
    private final ObjectMapper objectMapper;

    public TaskPlanService(
            PersistentProjectRepository projectRepository,
            PersistentTaskPlanRepository planRepository,
            PersistentTaskScenarioRepository scenarioRepository,
            PersistentScenarioExecutionRepository executionRepository,
            TaskJsonSupport taskJson,
            PersistentPlanTemplateRepository templateRepository,
            ObjectMapper objectMapper
    ) {
        this.projectRepository = projectRepository;
        this.planRepository = planRepository;
        this.scenarioRepository = scenarioRepository;
        this.executionRepository = executionRepository;
        this.taskJson = taskJson;
        this.templateRepository = templateRepository;
        this.objectMapper = objectMapper;
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
        return createPlan(projectId, name, remark, defaultControllerNodeId, defaultWorkerNodeIds,
                defaultMonitorTargetIds, createdBy, null);
    }

    @Transactional
    public TaskPlan createPlan(
            long projectId, String name, String remark,
            Long defaultControllerNodeId, List<Long> defaultWorkerNodeIds, List<Long> defaultMonitorTargetIds,
            String createdBy, Long templateId
    ) {
        validateProject(projectId);
        validateName(name);
        PersistentTaskPlanRecord plan = planRepository.save(new PersistentTaskPlanRecord(projectId, name.trim(), remark, createdBy));
        plan.updateProfile(name, remark, defaultControllerNodeId,
                taskJson.writeLongList(defaultWorkerNodeIds), taskJson.writeLongList(defaultMonitorTargetIds));
        String body = renderInitialBody(projectId, templateId, name.trim());
        if (body != null) {
            plan.initializeBody(body); // 创建语境初始化：不 bump revision（首版 revision=1）
            plan.initializePrecheck(defaultPrecheckJson(body));
        }
        return toPlan(planRepository.save(plan));
    }

    /** 模板渲染初始正文；模板缺失或非本项目模板（内置除外）→ null（存量计划兼容；模板项目归属见设计 §7.1）。 */
    private String renderInitialBody(Long projectId, Long templateId, String planName) {
        PersistentPlanTemplateRecord template = templateId != null
                ? templateRepository.findById(templateId).orElse(null)
                : templateRepository.findFirstByBuiltinTrueOrderByIdAsc().orElse(null);
        if (template == null) {
            return null;
        }
        if (template.getProjectId() != null && !template.getProjectId().equals(projectId)) {
            return null; // 他项目私有模板视同不存在
        }
        return PlanMarkdownSupport.renderTemplate(template.getContent(), planName);
    }

    /** 从渲染后的正文解析入口准则条目 → 默认 precheck 设置（disabled；设计 §10.2 只取入口准则）。 */
    private String defaultPrecheckJson(String body) {
        List<String> items = PlanMarkdownSupport.parseChecklistItems(entryCriteriaRegion(
                PlanMarkdownSupport.extractSection(body == null ? "" : body, "五、测试约束")));
        List<String> effective = items.isEmpty() ? PrecheckSettings.DEFAULT_ITEMS : items;
        try {
            return objectMapper.writeValueAsString(new PrecheckSettings(false, effective));
        } catch (Exception exception) {
            throw new PlanValidationException("PLAN_INVALID：precheck 设置序列化失败");
        }
    }

    /** 五、测试约束章节内的 ### 入口准则 小节（到下一 ### 小节或章节末尾）；缺失返回 null。 */
    private String entryCriteriaRegion(String sectionContent) {
        if (sectionContent == null) {
            return null;
        }
        String[] lines = sectionContent.split("\n", -1);
        int start = -1;
        int end = lines.length;
        for (int i = 0; i < lines.length; i++) {
            if (start < 0) {
                if (lines[i].startsWith("### 入口准则")) {
                    start = i;
                }
            } else if (lines[i].startsWith("### ")) {
                end = i;
                break;
            }
        }
        return start < 0 ? null : String.join("\n", java.util.Arrays.copyOfRange(lines, start, end));
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
                scenarioRepository.countByPlanId(plan.getId()),
                plan.getPhase(),
                plan.getStatus(),
                plan.getBody(),
                plan.getRevision(),
                plan.getPublishedAt(),
                plan.getPrecheckJson(),
                plan.getPrecheckExecutedAt()
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
