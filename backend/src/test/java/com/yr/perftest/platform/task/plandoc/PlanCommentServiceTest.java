package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.identity.SystemRole;
import com.yr.perftest.platform.project.PersistentProjectMemberRecord;
import com.yr.perftest.platform.project.PersistentProjectMemberRepository;
import com.yr.perftest.platform.project.PersistentProjectRecord;
import com.yr.perftest.platform.project.PersistentProjectRepository;
import com.yr.perftest.platform.project.ProjectRole;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-comment-service-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PlanCommentServiceTest {

    private static final HumanPrincipal OWNER = new HumanPrincipal("owner", Set.of(SystemRole.PROJECT_MEMBER));
    private static final HumanPrincipal REVIEWER = new HumanPrincipal("reviewer", Set.of(SystemRole.PROJECT_MEMBER));
    private static final HumanPrincipal OUTSIDER = new HumanPrincipal("outsider", Set.of(SystemRole.PROJECT_MEMBER));

    @Autowired
    private PlanCommentService comments;
    @Autowired
    private PlanWorkflowService workflow;
    @Autowired
    private PersistentTaskPlanRepository planRepository;
    @Autowired
    private PersistentProjectRepository projectRepository;
    @Autowired
    private PersistentProjectMemberRepository memberRepository;

    private long planId;

    @BeforeEach
    void setUp() {
        PersistentProjectRecord project = projectRepository.save(
                new PersistentProjectRecord("P1", "项目一", "", "owner"));
        memberRepository.save(new PersistentProjectMemberRecord(project.getId(), "owner", ProjectRole.OWNER));
        memberRepository.save(new PersistentProjectMemberRecord(project.getId(), "reviewer", ProjectRole.MEMBER));
        planId = planRepository.save(
                new PersistentTaskPlanRecord(project.getId(), "计划一", null, "owner")).getId();
    }

    @Test
    void addAndListWithViewerFlags() {
        PlanCommentService.CommentView created = comments.addComment(
                planId, REVIEWER, new PlanCommentService.AddCommentCommand("表格补口径", null, null));
        assertThat(created.canDelete()).isTrue();
        assertThat(created.canResolve()).isTrue();
        assertThat(created.canEdit()).isTrue();

        var reviewerView = comments.listComments(planId, REVIEWER).stream()
                .filter(c -> c.id() == created.id()).findFirst().orElseThrow();
        assertThat(reviewerView.canResolve()).isTrue();
        assertThat(reviewerView.canDelete()).isTrue();
        assertThat(reviewerView.canEdit()).isTrue();

        var ownerView = comments.listComments(planId, OWNER).stream()
                .filter(c -> c.id() == created.id()).findFirst().orElseThrow();
        assertThat(ownerView.canResolve()).isTrue();
        assertThat(ownerView.canDelete()).isTrue();
        assertThat(ownerView.canEdit()).isTrue();

        memberRepository.save(new PersistentProjectMemberRecord(
                projectRepository.findAll().get(0).getId(), "outsider", ProjectRole.MEMBER));
        var otherView = comments.listComments(planId, OUTSIDER).stream()
                .filter(c -> c.id() == created.id()).findFirst().orElseThrow();
        assertThat(otherView.canResolve()).isFalse();
        assertThat(otherView.canDelete()).isFalse();
        assertThat(otherView.canEdit()).isFalse();
    }

    @Test
    void systemCommentNotDeletable() {
        comments.systemComment(planId, "owner 提交评审");
        PlanCommentService.CommentView system = comments.listComments(planId, OWNER).get(0);
        assertThat(system.canDelete()).isFalse();
        assertThatThrownBy(() -> comments.deleteComment(planId, system.id(), OWNER))
                .isInstanceOf(PlanValidationException.class);
    }

    @Test
    void deleteRequiresAuthorOrOwner() {
        PlanCommentService.CommentView created = comments.addComment(
                planId, REVIEWER, new PlanCommentService.AddCommentCommand("成员批注", null, null));
        memberRepository.save(new PersistentProjectMemberRecord(
                projectRepository.findAll().get(0).getId(), "outsider", ProjectRole.MEMBER));
        assertThatThrownBy(() -> comments.deleteComment(planId, created.id(), OUTSIDER))
                .isInstanceOf(PlanAccessDeniedException.class);
        comments.deleteComment(planId, created.id(), REVIEWER);
        assertThat(comments.listComments(planId, OWNER)).noneMatch(c -> c.id() == created.id());
    }

    @Test
    void nonMemberRejected() {
        assertThatThrownBy(() -> comments.addComment(
                planId, OUTSIDER, new PlanCommentService.AddCommentCommand("外部", null, null)))
                .isInstanceOf(PlanAccessDeniedException.class);
    }

    @Test
    void blankContentRejected() {
        assertThatThrownBy(() -> comments.addComment(
                planId, REVIEWER, new PlanCommentService.AddCommentCommand("  ", null, null)))
                .isInstanceOf(PlanValidationException.class);
    }

    @Test
    void anchoredCommentRoundTrip() {
        var anchor = new PlanCommentService.CommentAnchor(42, "登录接口 TPS ≥ 1000", "三、测试指标");
        PlanCommentService.CommentView created = comments.addComment(
                planId, REVIEWER, new PlanCommentService.AddCommentCommand("目标偏乐观", null, anchor));
        long revision = planRepository.findById(planId).orElseThrow().getRevision();
        assertThat(created.anchorLine()).isEqualTo(42);
        assertThat(created.sectionTitle()).isEqualTo("三、测试指标");
        assertThat(created.bodyRevision()).isEqualTo(revision); // 服务端写入，不信任客户端
        assertThat(created.parentId()).isNull();
        assertThat(created.resolved()).isFalse();
    }

    @Test
    void anchorMustBeComplete() {
        var partial = new PlanCommentService.CommentAnchor(42, "登录接口", null);
        assertThatThrownBy(() -> comments.addComment(
                planId, REVIEWER, new PlanCommentService.AddCommentCommand("x", null, partial)))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("锚点不完整");
    }

    @Test
    void anchorSectionWhitelisted() {
        var bad = new PlanCommentService.CommentAnchor(1, "text", "十三、不存在");
        assertThatThrownBy(() -> comments.addComment(
                planId, REVIEWER, new PlanCommentService.AddCommentCommand("x", null, bad)))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("章节不合法");
    }

    @Test
    void anchorTextTruncatedTo200() {
        var anchor = new PlanCommentService.CommentAnchor(0, "长".repeat(300), "三、测试指标");
        PlanCommentService.CommentView created = comments.addComment(
                planId, REVIEWER, new PlanCommentService.AddCommentCommand("x", null, anchor));
        assertThat(created.anchorText()).hasSize(200);
    }

    @Test
    void replyToOneLevelOnly() {
        PlanCommentService.CommentView root = comments.addComment(
                planId, REVIEWER, new PlanCommentService.AddCommentCommand("根", null, null));
        PlanCommentService.CommentView reply = comments.addComment(
                planId, OWNER, new PlanCommentService.AddCommentCommand("回复", root.id(), null));
        assertThat(reply.parentId()).isEqualTo(root.id());
        assertThat(reply.canResolve()).isFalse(); // 回复不可被解决
        assertThatThrownBy(() -> comments.addComment(
                planId, REVIEWER, new PlanCommentService.AddCommentCommand("嵌套", reply.id(), null)))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("PLAN_COMMENT_NESTED");
    }

    @Test
    void resolveRootOnlyAndPermissionAware() {
        PlanCommentService.CommentView root = comments.addComment(
                planId, REVIEWER, new PlanCommentService.AddCommentCommand("根", null, null));
        PlanCommentService.CommentView reply = comments.addComment(
                planId, REVIEWER, new PlanCommentService.AddCommentCommand("回复", root.id(), null));
        assertThatThrownBy(() -> comments.resolveComment(planId, reply.id(), REVIEWER, true))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("仅根批注");
        memberRepository.save(new PersistentProjectMemberRecord(
                projectRepository.findAll().get(0).getId(), "outsider", ProjectRole.MEMBER));
        assertThatThrownBy(() -> comments.resolveComment(planId, root.id(), OUTSIDER, true))
                .isInstanceOf(PlanAccessDeniedException.class);
        comments.resolveComment(planId, root.id(), OWNER, true); // 负责人可解决他人批注
        var resolved = comments.listComments(planId, OWNER).stream()
                .filter(c -> c.id() == root.id()).findFirst().orElseThrow();
        assertThat(resolved.resolved()).isTrue();
        assertThat(resolved.resolvedBy()).isEqualTo("owner");
        comments.resolveComment(planId, root.id(), REVIEWER, false); // 作者可重开
        assertThat(comments.listComments(planId, OWNER).stream()
                .filter(c -> c.id() == root.id()).findFirst().orElseThrow().resolved()).isFalse();
    }

    @Test
    void resolveBlockedAfterExecution() {
        PlanCommentService.CommentView root = comments.addComment(
                planId, REVIEWER, new PlanCommentService.AddCommentCommand("根", null, null));
        workflow.submit(planId, OWNER, null);
        workflow.startReview(planId, REVIEWER);
        workflow.approve(planId, REVIEWER, null);
        workflow.startExecution(planId, REVIEWER);
        assertThatThrownBy(() -> comments.resolveComment(planId, root.id(), REVIEWER, true))
                .isInstanceOf(PlanStateException.class);
    }

    @Test
    void deleteRootCascadesReplies() {
        PlanCommentService.CommentView root = comments.addComment(
                planId, REVIEWER, new PlanCommentService.AddCommentCommand("根", null, null));
        comments.addComment(planId, OWNER, new PlanCommentService.AddCommentCommand("回复", root.id(), null));
        comments.deleteComment(planId, root.id(), REVIEWER);
        assertThat(comments.listComments(planId, OWNER)).isEmpty();
    }

    @Test
    void commentBlockedOutsideReviewPhases() {
        workflow.submit(planId, OWNER, null);
        workflow.startReview(planId, REVIEWER);
        workflow.approve(planId, REVIEWER, null);
        workflow.startExecution(planId, REVIEWER);
        assertThatThrownBy(() -> comments.addComment(
                planId, REVIEWER, new PlanCommentService.AddCommentCommand("迟到", null, null)))
                .isInstanceOf(PlanStateException.class);
    }

    @Test
    void editUpdatesContentAndKeepsAnchor() {
        var anchor = new PlanCommentService.CommentAnchor(42, "登录接口 TPS ≥ 1000", "三、测试指标");
        PlanCommentService.CommentView created = comments.addComment(
                planId, REVIEWER, new PlanCommentService.AddCommentCommand("目标偏乐观", null, anchor));
        PlanCommentService.CommentView edited = comments.editComment(planId, created.id(), REVIEWER, "  目标偏乐观，建议复测  ");
        assertThat(edited.content()).isEqualTo("目标偏乐观，建议复测");
        assertThat(edited.anchorLine()).isEqualTo(42); // 锚点与线程关系不变
        assertThat(edited.anchorText()).isEqualTo("登录接口 TPS ≥ 1000");
        assertThat(edited.sectionTitle()).isEqualTo("三、测试指标");
        assertThat(edited.parentId()).isNull();
        assertThat(edited.bodyRevision()).isEqualTo(created.bodyRevision());
        assertThat(edited.canEdit()).isTrue();
    }

    @Test
    void editRequiresAuthorOrOwner() {
        PlanCommentService.CommentView created = comments.addComment(
                planId, REVIEWER, new PlanCommentService.AddCommentCommand("成员批注", null, null));
        memberRepository.save(new PersistentProjectMemberRecord(
                projectRepository.findAll().get(0).getId(), "outsider", ProjectRole.MEMBER));
        assertThatThrownBy(() -> comments.editComment(planId, created.id(), OUTSIDER, "改别人的"))
                .isInstanceOf(PlanAccessDeniedException.class);
        comments.editComment(planId, created.id(), OWNER, "负责人代改");
        assertThat(comments.listComments(planId, REVIEWER).stream()
                .filter(c -> c.id() == created.id()).findFirst().orElseThrow().content()).isEqualTo("负责人代改");
    }

    @Test
    void editRejectsBlankAndSystem() {
        PlanCommentService.CommentView created = comments.addComment(
                planId, REVIEWER, new PlanCommentService.AddCommentCommand("正常批注", null, null));
        assertThatThrownBy(() -> comments.editComment(planId, created.id(), REVIEWER, "  "))
                .isInstanceOf(PlanValidationException.class);
        comments.systemComment(planId, "owner 提交评审");
        PlanCommentService.CommentView system = comments.listComments(planId, OWNER).stream()
                .filter(c -> c.kind() == PlanCommentKind.SYSTEM).findFirst().orElseThrow();
        assertThat(system.canEdit()).isFalse();
        assertThatThrownBy(() -> comments.editComment(planId, system.id(), OWNER, "改系统记录"))
                .isInstanceOf(PlanValidationException.class);
    }

    @Test
    void editBlockedAfterExecution() {
        PlanCommentService.CommentView root = comments.addComment(
                planId, REVIEWER, new PlanCommentService.AddCommentCommand("根", null, null));
        workflow.submit(planId, OWNER, null);
        workflow.startReview(planId, REVIEWER);
        workflow.approve(planId, REVIEWER, null);
        workflow.startExecution(planId, REVIEWER);
        assertThatThrownBy(() -> comments.editComment(planId, root.id(), REVIEWER, "迟到编辑"))
                .isInstanceOf(PlanStateException.class);
    }
}
