package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.project.ProjectAccessResolver;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** 批注域服务（spec §6）：增删改查/线程/解决；流转服务只写流转记录。批注读写不触碰 plan.revision。 */
@Service
public class PlanCommentService {

    /** 锚点三元组：三字段须同现同缺（spec §6 校验规则）。 */
    public record CommentAnchor(Integer line, String text, String section) {
    }

    public record AddCommentCommand(String content, Long parentId, CommentAnchor anchor) {
    }

    public record CommentView(long id, long planId, String author, String content, PlanCommentKind kind,
                              Instant createdAt, Long parentId, Integer anchorLine, String anchorText,
                              String sectionTitle, Long bodyRevision, boolean resolved, String resolvedBy,
                              Instant resolvedAt, boolean canResolve, boolean canDelete, boolean canEdit) {
    }

    private final PersistentPlanCommentRepository commentRepository;
    private final PersistentTaskPlanRepository planRepository;
    private final ProjectAccessResolver accessResolver;

    public PlanCommentService(PersistentPlanCommentRepository commentRepository,
                              PersistentTaskPlanRepository planRepository,
                              ProjectAccessResolver accessResolver) {
        this.commentRepository = commentRepository;
        this.planRepository = planRepository;
        this.accessResolver = accessResolver;
    }

    @Transactional(readOnly = true)
    public List<CommentView> listComments(long planId, HumanPrincipal viewer) {
        PersistentTaskPlanRecord plan = requirePlan(planId);
        requireMember(plan, viewer);
        Viewer viewerInfo = viewerOf(plan, viewer);
        return commentRepository.findAllByPlanIdOrderByIdAsc(planId).stream()
                .map(c -> toView(c, viewerInfo))
                .toList();
    }

    @Transactional
    public CommentView addComment(long planId, HumanPrincipal actor, AddCommentCommand command) {
        PersistentTaskPlanRecord plan = requirePlan(planId);
        requireCommenter(plan, actor);
        if (command == null || command.content() == null || command.content().isBlank()) {
            throw new PlanValidationException("PLAN_INVALID：批注内容不能为空");
        }
        CommentAnchor anchor = normalizeAnchor(command.anchor());
        Long parentId = validateParent(planId, command.parentId());
        PersistentPlanCommentRecord saved = commentRepository.save(new PersistentPlanCommentRecord(
                planId, actor.username(), command.content().trim(), PlanCommentKind.REVIEW,
                parentId,
                anchor == null ? null : anchor.line(),
                anchor == null ? null : anchor.text(),
                anchor == null ? null : anchor.section(),
                (long) plan.getRevision()));
        return toView(saved, viewerOf(plan, actor));
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
        Viewer viewer = viewerOf(plan, actor);
        if (!viewer.ownerLike() && !comment.getAuthor().equals(actor.username())) {
            throw new PlanAccessDeniedException("PLAN_ACCESS_DENIED：仅批注作者/负责人/项目 OWNER/系统管理员可删除批注");
        }
        commentRepository.findAllByParentId(commentId).forEach(commentRepository::delete); // 删根级联删回复
        commentRepository.delete(comment);
    }

    /** 解决/重开（spec §3.5/§3.6）：仅根批注；评审域外（进入执行后）批注整体只读。 */
    @Transactional
    public void resolveComment(long planId, long commentId, HumanPrincipal actor, boolean resolved) {
        PersistentTaskPlanRecord plan = requirePlan(planId);
        requireCommenter(plan, actor);
        PersistentPlanCommentRecord comment = commentRepository.findById(commentId)
                .filter(c -> c.getPlanId() == planId)
                .orElseThrow(() -> new PlanValidationException("PLAN_INVALID：批注不存在"));
        if (comment.getParentId() != null) {
            throw new PlanValidationException("PLAN_COMMENT_NESTED：仅根批注可解决/重开");
        }
        Viewer viewer = viewerOf(plan, actor);
        if (!viewer.ownerLike() && !comment.getAuthor().equals(actor.username())) {
            throw new PlanAccessDeniedException("PLAN_ACCESS_DENIED：仅批注作者/负责人可解决批注");
        }
        comment.applyResolve(resolved, actor.username());
        commentRepository.save(comment);
    }

    /** 编辑批注内容（spec §6）：仅 REVIEW 批注、作者/负责人；评审域外只读。锚点与线程关系不变。 */
    @Transactional
    public CommentView editComment(long planId, long commentId, HumanPrincipal actor, String content) {
        PersistentTaskPlanRecord plan = requirePlan(planId);
        requireCommenter(plan, actor);
        if (content == null || content.isBlank()) {
            throw new PlanValidationException("PLAN_INVALID：批注内容不能为空");
        }
        PersistentPlanCommentRecord comment = commentRepository.findById(commentId)
                .filter(c -> c.getPlanId() == planId)
                .orElseThrow(() -> new PlanValidationException("PLAN_INVALID：批注不存在"));
        if (comment.getKind() != PlanCommentKind.REVIEW) {
            throw new PlanValidationException("PLAN_INVALID：系统批注不可编辑");
        }
        Viewer viewer = viewerOf(plan, actor);
        if (!viewer.ownerLike() && !comment.getAuthor().equals(actor.username())) {
            throw new PlanAccessDeniedException("PLAN_ACCESS_DENIED：仅批注作者/负责人/项目 OWNER/系统管理员可编辑批注");
        }
        comment.applyContent(content.trim());
        commentRepository.save(comment);
        return toView(comment, viewer);
    }

    @Transactional
    public void systemComment(long planId, String content) {
        commentRepository.save(new PersistentPlanCommentRecord(planId, "system", content, PlanCommentKind.SYSTEM));
    }

    /** 流转附言（提交/通过附言、驳回原因）：无锚点 REVIEW 批注，前端归「未锚定」分组。 */
    @Transactional
    public void appendReviewNote(long planId, String author, String content) {
        commentRepository.save(new PersistentPlanCommentRecord(planId, author, content, PlanCommentKind.REVIEW));
    }

    private CommentAnchor normalizeAnchor(CommentAnchor anchor) {
        if (anchor == null || (anchor.line() == null && anchor.text() == null && anchor.section() == null)) {
            return null; // 三字段全缺省 = 无锚点（流转附言形态）
        }
        if (anchor.line() == null || anchor.text() == null || anchor.section() == null) {
            throw new PlanValidationException("PLAN_INVALID：批注锚点不完整（line/text/section 须同时出现）");
        }
        if (anchor.line() < 0) {
            throw new PlanValidationException("PLAN_INVALID：批注锚点行号不合法");
        }
        if (!PlanMarkdownSupport.CANONICAL_HEADINGS.contains(anchor.section())) {
            throw new PlanValidationException("PLAN_INVALID：批注锚点章节不合法");
        }
        if (anchor.text().isBlank()) {
            throw new PlanValidationException("PLAN_INVALID：批注锚点文本不能为空");
        }
        String text = anchor.text().trim();
        return new CommentAnchor(anchor.line(), text.length() > 200 ? text.substring(0, 200) : text, anchor.section());
    }

    private Long validateParent(long planId, Long parentId) {
        if (parentId == null) {
            return null;
        }
        PersistentPlanCommentRecord parent = commentRepository.findById(parentId)
                .filter(c -> c.getPlanId() == planId && c.getKind() == PlanCommentKind.REVIEW)
                .orElseThrow(() -> new PlanValidationException("PLAN_INVALID：回复目标批注不存在"));
        if (parent.getParentId() != null) {
            throw new PlanValidationException("PLAN_COMMENT_NESTED：回复不支持嵌套");
        }
        return parentId;
    }

    private CommentView toView(PersistentPlanCommentRecord c, Viewer viewer) {
        boolean authorOrOwner = viewer.ownerLike() || c.getAuthor().equals(viewer.username());
        boolean root = c.getParentId() == null;
        return new CommentView(c.getId(), c.getPlanId(), c.getAuthor(), c.getContent(), c.getKind(), c.getCreatedAt(),
                c.getParentId(), c.getAnchorLine(), c.getAnchorText(), c.getSectionTitle(), c.getBodyRevision(),
                c.isResolved(), c.getResolvedBy(), c.getResolvedAt(),
                root && authorOrOwner && c.getKind() == PlanCommentKind.REVIEW,
                authorOrOwner && c.getKind() == PlanCommentKind.REVIEW,
                authorOrOwner && c.getKind() == PlanCommentKind.REVIEW);
    }

    private record Viewer(String username, boolean ownerLike) {
    }

    private Viewer viewerOf(PersistentTaskPlanRecord plan, HumanPrincipal actor) {
        ProjectAccessResolver.PlanActorRole role =
                actor == null ? ProjectAccessResolver.PlanActorRole.NONE
                        : accessResolver.resolve(plan.getProjectId(), actor, plan.getCreatedBy());
        boolean ownerLike = role == ProjectAccessResolver.PlanActorRole.SYSTEM_ADMIN
                || role == ProjectAccessResolver.PlanActorRole.PROJECT_OWNER
                || role == ProjectAccessResolver.PlanActorRole.PLAN_OWNER;
        return new Viewer(actor == null ? "" : actor.username(), ownerLike);
    }

    private void requireMember(PersistentTaskPlanRecord plan, HumanPrincipal actor) {
        if (actor == null || accessResolver.resolve(plan.getProjectId(), actor, plan.getCreatedBy())
                == ProjectAccessResolver.PlanActorRole.NONE) {
            throw new PlanAccessDeniedException("PLAN_ACCESS_DENIED：非项目成员");
        }
    }

    /** COMMENT 门禁（PlanAccess 唯一真相源）：非成员 403；成员但阶段不允许 409。COMMENT 不受 hasAnyExecution 影响。 */
    private void requireCommenter(PersistentTaskPlanRecord plan, HumanPrincipal actor) {
        if (actor == null) {
            throw new PlanAccessDeniedException("PLAN_ACCESS_DENIED：未登录");
        }
        ProjectAccessResolver.PlanActorRole role =
                accessResolver.resolve(plan.getProjectId(), actor, plan.getCreatedBy());
        if (role == ProjectAccessResolver.PlanActorRole.NONE) {
            throw new PlanAccessDeniedException("PLAN_ACCESS_DENIED：非项目成员");
        }
        var permissions = PlanAccess.compute(role, plan.getPhase(), plan.getStatus(), false);
        if (!Boolean.TRUE.equals(permissions.get("COMMENT"))) {
            throw new PlanStateException("PLAN_STATE：当前阶段不可批注（当前 "
                    + plan.getPhase() + "/" + plan.getStatus() + "）", plan.getPhase(), plan.getStatus(), List.of());
        }
    }

    private PersistentTaskPlanRecord requirePlan(long planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new PlanValidationException("PLAN_INVALID：task plan does not exist"));
    }
}
