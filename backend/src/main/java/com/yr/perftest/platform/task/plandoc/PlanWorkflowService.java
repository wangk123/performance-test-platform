package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.project.ProjectAccessResolver;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRepository;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.PersistentTaskScenarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** 计划状态机流转与批注（设计 §4/§6）。报告/发布/模板/分享/预检分任务追加。 */
@Service
public class PlanWorkflowService {

    public record CommentView(long id, long planId, String author, String content, PlanCommentKind kind, Instant createdAt) {
    }

    private final PersistentTaskPlanRepository planRepository;
    private final PersistentTaskScenarioRepository scenarioRepository;
    private final PersistentScenarioExecutionRepository executionRepository;
    private final PersistentPlanCommentRepository commentRepository;
    private final ProjectAccessResolver accessResolver;

    public PlanWorkflowService(
            PersistentTaskPlanRepository planRepository,
            PersistentTaskScenarioRepository scenarioRepository,
            PersistentScenarioExecutionRepository executionRepository,
            PersistentPlanCommentRepository commentRepository,
            ProjectAccessResolver accessResolver
    ) {
        this.planRepository = planRepository;
        this.scenarioRepository = scenarioRepository;
        this.executionRepository = executionRepository;
        this.commentRepository = commentRepository;
        this.accessResolver = accessResolver;
    }

    @Transactional
    public void submit(long planId, HumanPrincipal actor, String comment) {
        PersistentTaskPlanRecord plan = requireActor(planId, actor, "SUBMIT");
        plan.transitionTo(PlanPhase.REVIEW, PlanStatus.PENDING);
        systemComment(planId, actor.username() + " 提交评审");
        if (comment != null && !comment.isBlank()) {
            commentRepository.save(new PersistentPlanCommentRecord(planId, actor.username(), comment.trim(), PlanCommentKind.REVIEW));
        }
    }

    @Transactional
    public void startReview(long planId, HumanPrincipal actor) {
        PersistentTaskPlanRecord plan = requireActor(planId, actor, "START_REVIEW");
        plan.transitionTo(PlanPhase.REVIEW, PlanStatus.IN_REVIEW);
        systemComment(planId, actor.username() + " 开始评审");
    }

    @Transactional
    public void approve(long planId, HumanPrincipal actor, String comment) {
        PersistentTaskPlanRecord plan = requireActor(planId, actor, "APPROVE");
        plan.transitionTo(PlanPhase.REVIEW, PlanStatus.APPROVED);
        systemComment(planId, "评审通过（审批人：" + actor.username() + "）");
        if (comment != null && !comment.isBlank()) {
            commentRepository.save(new PersistentPlanCommentRecord(planId, actor.username(), comment.trim(), PlanCommentKind.REVIEW));
        }
    }

    @Transactional
    public void reject(long planId, HumanPrincipal actor, String comment) {
        if (comment == null || comment.isBlank()) {
            throw new PlanValidationException("PLAN_INVALID：驳回必须附批注");
        }
        PersistentTaskPlanRecord plan = requireActor(planId, actor, "REJECT");
        plan.transitionTo(PlanPhase.DRAFT, PlanStatus.DRAFT);
        commentRepository.save(new PersistentPlanCommentRecord(planId, actor.username(), comment.trim(), PlanCommentKind.REVIEW));
        systemComment(planId, actor.username() + " 驳回，退回草稿");
    }

    @Transactional
    public void withdraw(long planId, HumanPrincipal actor) {
        PersistentTaskPlanRecord plan = requireActor(planId, actor, "WITHDRAW");
        plan.transitionTo(PlanPhase.DRAFT, PlanStatus.DRAFT);
        systemComment(planId, actor.username() + " 撤回评审，退回草稿");
    }

    @Transactional
    public void backToDraft(long planId, HumanPrincipal actor) {
        PersistentTaskPlanRecord plan = requireActor(planId, actor, "BACK_TO_DRAFT");
        if (hasAnyExecution(planId)) {
            throw new PlanStateException("PLAN_STATE：已产生执行，不可退回草稿（当前 "
                    + plan.getPhase() + "/" + plan.getStatus() + "）",
                    plan.getPhase(), plan.getStatus(), List.of("TO_REPORT", "GENERATE_REPORT"));
        }
        plan.transitionTo(PlanPhase.DRAFT, PlanStatus.DRAFT);
        systemComment(planId, actor.username() + " 退回草稿");
    }

    @Transactional
    public void startExecution(long planId, HumanPrincipal actor) {
        PersistentTaskPlanRecord plan = requireActor(planId, actor, "START_EXECUTION");
        plan.transitionTo(PlanPhase.EXECUTION, PlanStatus.PENDING);
        systemComment(planId, actor.username() + " 进入执行阶段");
    }

    @Transactional
    public void toReport(long planId, HumanPrincipal actor) {
        PersistentTaskPlanRecord plan = requireActor(planId, actor, "TO_REPORT");
        plan.transitionTo(PlanPhase.REPORT, PlanStatus.PENDING);
        systemComment(planId, actor.username() + " 进入报告阶段");
    }

    @Transactional(readOnly = true)
    public List<CommentView> listComments(long planId) {
        return commentRepository.findAllByPlanIdOrderByIdAsc(planId).stream()
                .map(c -> new CommentView(c.getId(), c.getPlanId(), c.getAuthor(), c.getContent(), c.getKind(), c.getCreatedAt()))
                .toList();
    }

    @Transactional
    public CommentView addComment(long planId, HumanPrincipal actor, String content) {
        if (content == null || content.isBlank()) {
            throw new PlanValidationException("PLAN_INVALID：批注内容不能为空");
        }
        PersistentTaskPlanRecord plan = requireActor(planId, actor, "COMMENT");
        PersistentPlanCommentRecord saved = commentRepository.save(
                new PersistentPlanCommentRecord(planId, actor.username(), content.trim(), PlanCommentKind.REVIEW));
        return new CommentView(saved.getId(), saved.getPlanId(), saved.getAuthor(), saved.getContent(), saved.getKind(), saved.getCreatedAt());
    }

    @Transactional
    public void deleteComment(long planId, long commentId, HumanPrincipal actor) {
        PersistentTaskPlanRecord plan = requirePlan(planId);
        PersistentPlanCommentRecord comment = commentRepository.findById(commentId)
                .filter(c -> c.getPlanId() == planId)
                .orElseThrow(() -> new PlanValidationException("PLAN_INVALID：批注不存在"));
        if (comment.getKind() == PlanCommentKind.SYSTEM) {
            throw new PlanValidationException("PLAN_INVALID：系统批注不可删除");
        }
        ProjectAccessResolver.PlanActorRole role = accessResolver.resolve(plan.getProjectId(), actor, plan.getCreatedBy());
        boolean ownerLike = role == ProjectAccessResolver.PlanActorRole.SYSTEM_ADMIN
                || role == ProjectAccessResolver.PlanActorRole.PROJECT_OWNER
                || role == ProjectAccessResolver.PlanActorRole.PLAN_OWNER;
        if (!ownerLike && !comment.getAuthor().equals(actor.username())) {
            throw new PlanAccessDeniedException("PLAN_ACCESS_DENIED：仅批注作者/负责人/项目 OWNER/系统管理员可删除批注");
        }
        commentRepository.delete(comment);
    }

    @Transactional
    public void systemComment(long planId, String content) {
        commentRepository.save(new PersistentPlanCommentRecord(planId, "system", content, PlanCommentKind.SYSTEM));
    }

    public boolean hasAnyExecution(long planId) {
        return scenarioRepository.findAllByPlanIdOrderBySortOrderAscIdAsc(planId).stream()
                .anyMatch(scenario -> executionRepository.existsByScenarioId(scenario.getId()));
    }

    public PersistentTaskPlanRecord requirePlan(long planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new PlanValidationException("PLAN_INVALID：task plan does not exist"));
    }

    /** 校验动作权限并返回计划记录；角色不足抛 403，角色可而状态不允许抛 409（附允许动作）。 */
    private PersistentTaskPlanRecord requireActor(long planId, HumanPrincipal actor, String action) {
        PersistentTaskPlanRecord plan = requirePlan(planId);
        if (actor == null) {
            throw new PlanAccessDeniedException("PLAN_ACCESS_DENIED：未登录");
        }
        ProjectAccessResolver.PlanActorRole role = accessResolver.resolve(plan.getProjectId(), actor, plan.getCreatedBy());
        java.util.Map<String, Boolean> permissions = PlanAccess.compute(role, plan.getPhase(), plan.getStatus(), hasAnyExecution(planId));
        if (!Boolean.TRUE.equals(permissions.get(action))) {
            if (role == ProjectAccessResolver.PlanActorRole.NONE) {
                throw new PlanAccessDeniedException("PLAN_ACCESS_DENIED：非项目成员");
            }
            // 同状态下高权限角色可执行 → 属角色不足（403）；否则属状态不允许（409），与 PlanDocumentService 口径一致
            java.util.Map<String, Boolean> privileged = PlanAccess.compute(
                    ProjectAccessResolver.PlanActorRole.PLAN_OWNER, plan.getPhase(), plan.getStatus(), hasAnyExecution(planId));
            if (Boolean.TRUE.equals(privileged.get(action))) {
                throw new PlanAccessDeniedException("PLAN_ACCESS_DENIED：当前角色无权执行「" + action + "」");
            }
            throw new PlanStateException("PLAN_STATE：当前状态不允许「" + action + "」（当前 "
                    + plan.getPhase() + "/" + plan.getStatus() + "，允许："
                    + allowedActions(permissions) + "）",
                    plan.getPhase(), plan.getStatus(), allowedActions(permissions));
        }
        return plan;
    }

    private List<String> allowedActions(java.util.Map<String, Boolean> permissions) {
        return permissions.entrySet().stream().filter(java.util.Map.Entry::getValue).map(java.util.Map.Entry::getKey).toList();
    }
}
