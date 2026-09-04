package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.execution.ExecutionStatus;
import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.project.ProjectAccessResolver;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRepository;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskScenarioRepository;
import com.yr.perftest.platform.task.TaskPlan;
import com.yr.perftest.platform.task.TaskPlanService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 文档原文读写（唯一数据源）、revision 乐观并发、系统回填、执行态惰性纠偏。 */
@Service
public class PlanDocumentService {
    private final PersistentTaskPlanRepository planRepository;
    private final PersistentTaskScenarioRepository scenarioRepository;
    private final PersistentScenarioExecutionRepository executionRepository;
    private final TaskPlanService planService;
    private final ProjectAccessResolver accessResolver;

    public PlanDocumentService(
            PersistentTaskPlanRepository planRepository,
            PersistentTaskScenarioRepository scenarioRepository,
            PersistentScenarioExecutionRepository executionRepository,
            TaskPlanService planService,
            ProjectAccessResolver accessResolver
    ) {
        this.planRepository = planRepository;
        this.scenarioRepository = scenarioRepository;
        this.executionRepository = executionRepository;
        this.planService = planService;
        this.accessResolver = accessResolver;
    }

    @Transactional
    public TaskPlan getDocument(long planId) {
        correctExecutionState(planId);
        return planService.getPlan(planId);
    }

    @Transactional
    public TaskPlan updateMarkdown(long planId, long baseRevision, String markdown, HumanPrincipal actor) {
        PersistentTaskPlanRecord plan = requirePlan(planId);
        requireEditAllowed(plan, actor);
        if (plan.getRevision() != baseRevision) {
            throw new PlanRevisionConflictException(
                    "PLAN_REVISION_CONFLICT：计划文档已被修改（当前 revision=" + plan.getRevision()
                            + "，提交基于 revision=" + baseRevision + "）",
                    plan.getRevision(),
                    plan.getBody());
        }
        plan.updateBody(markdown == null ? "" : markdown);
        return planService.getPlan(planId);
    }

    /** 系统回填：不校验 baseRevision、不受阶段限制，但 revision+1（设计 §8.2）。 */
    @Transactional
    public void backfillExecutionRecord(long planId, String scenarioName, long executionId, String entryLine) {
        PersistentTaskPlanRecord plan = requirePlan(planId);
        String base = plan.getBody() == null ? "" : plan.getBody();
        String updated = PlanMarkdownSupport.appendExecutionRecord(base, scenarioName, executionId, entryLine);
        if (updated.equals(base)) {
            return; // 幂等：标记已存在，不动 revision
        }
        plan.updateBody(updated);
    }

    @Transactional
    public void correctExecutionState(long planId) {
        PersistentTaskPlanRecord plan = requirePlan(planId);
        if (plan.getPhase() != PlanPhase.EXECUTION) {
            return;
        }
        boolean active = hasActiveExecution(planId);
        if (plan.getStatus() == PlanStatus.RUNNING && !active) {
            plan.transitionTo(PlanPhase.EXECUTION, PlanStatus.DONE);
        } else if ((plan.getStatus() == PlanStatus.PENDING || plan.getStatus() == PlanStatus.DONE) && active) {
            plan.transitionTo(PlanPhase.EXECUTION, PlanStatus.RUNNING);
        }
    }

    public boolean hasActiveExecution(long planId) {
        List<Long> scenarioIds = scenarioRepository.findAllByPlanIdOrderBySortOrderAscIdAsc(planId).stream()
                .map(com.yr.perftest.platform.task.PersistentTaskScenarioRecord::getId)
                .toList();
        if (scenarioIds.isEmpty()) {
            return false;
        }
        return executionRepository.countByScenarioIdInAndStatusIn(scenarioIds,
                List.of(ExecutionStatus.QUEUED, ExecutionStatus.RUNNING, ExecutionStatus.STOPPING)) > 0;
    }

    public PersistentTaskPlanRecord requirePlan(long planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new PlanValidationException("PLAN_INVALID：task plan does not exist"));
    }

    private void requireEditAllowed(PersistentTaskPlanRecord plan, HumanPrincipal actor) {
        ProjectAccessResolver.PlanActorRole role = accessResolver.resolve(plan.getProjectId(), actor, plan.getCreatedBy());
        if (!PlanAccess.compute(role, plan.getPhase(), plan.getStatus(), true).get("EDIT")) {
            if (plan.getPhase() != PlanPhase.DRAFT) {
                throw new PlanStateException("PLAN_STATE：文档仅草稿阶段可编辑（当前 "
                        + plan.getPhase() + "/" + plan.getStatus() + "）",
                        plan.getPhase(), plan.getStatus(), List.of("WITHDRAW", "BACK_TO_DRAFT"));
            }
            throw new PlanAccessDeniedException("PLAN_ACCESS_DENIED：仅负责人/项目 OWNER/系统管理员可编辑文档");
        }
    }
}
