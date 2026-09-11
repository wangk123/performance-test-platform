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
        requirePlan(planId); // 不存在 → PLAN_INVALID（原 correctExecutionState 前置存在性校验保留，执行态纠偏已删除）
        return planService.getPlan(planId);
    }

    @Transactional
    public TaskPlan updateMarkdown(long planId, long baseRevision, String markdown, HumanPrincipal actor) {
        // 悲观行锁持有至事务提交：并发同 baseRevision 的编辑串行化，后到者读到已 bump 的 revision 得 409（设计 §5.2）。
        PersistentTaskPlanRecord plan = planRepository.findWithLockingById(planId)
                .orElseThrow(() -> new PlanValidationException("PLAN_INVALID：task plan does not exist"));
        requireEditAllowed(plan, actor);
        if (plan.getRevision() != baseRevision) {
            throw new PlanRevisionConflictException(
                    "PLAN_REVISION_CONFLICT：计划文档已被修改（当前 revision=" + plan.getRevision()
                            + "，提交基于 revision=" + baseRevision + "）",
                    plan.getRevision(),
                    plan.getBody());
        }
        // 解析即校验（spec §3.1）：格式非法 400，不落库；合法/无指标均放行。
        PlanAcceptanceParser.parse(markdown == null ? "" : markdown);
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

    /** 活跃执行数（QUEUED/RUNNING/STOPPING）：执行完成/发布前的二次确认告警数据源（spec §4.3）。 */
    @Transactional(readOnly = true)
    public int countActiveExecutions(long planId) {
        List<Long> scenarioIds = scenarioRepository.findAllByPlanIdOrderBySortOrderAscIdAsc(planId).stream()
                .map(com.yr.perftest.platform.task.PersistentTaskScenarioRecord::getId)
                .toList();
        int count = 0;
        for (Long scenarioId : scenarioIds) {
            if (executionRepository.existsByScenarioIdAndStatusIn(scenarioId, List.of(
                    com.yr.perftest.platform.execution.ExecutionStatus.QUEUED,
                    com.yr.perftest.platform.execution.ExecutionStatus.RUNNING,
                    com.yr.perftest.platform.execution.ExecutionStatus.STOPPING))) {
                count++;
            }
        }
        return count;
    }

    private void requireEditAllowed(PersistentTaskPlanRecord plan, HumanPrincipal actor) {
        ProjectAccessResolver.PlanActorRole role = accessResolver.resolve(plan.getProjectId(), actor, plan.getCreatedBy());
        // 无角色维度（spec §10）：门槛=登录且为项目成员；EDIT 任意状态开放，revision 冲突保护兜底
        if (actor == null || role == ProjectAccessResolver.PlanActorRole.NONE) {
            throw new PlanAccessDeniedException("PLAN_ACCESS_DENIED：非项目成员");
        }
    }
}
