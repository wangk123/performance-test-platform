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

        var reviewerView = comments.listComments(planId, REVIEWER).stream()
                .filter(c -> c.id() == created.id()).findFirst().orElseThrow();
        assertThat(reviewerView.canResolve()).isTrue();
        assertThat(reviewerView.canDelete()).isTrue();

        var ownerView = comments.listComments(planId, OWNER).stream()
                .filter(c -> c.id() == created.id()).findFirst().orElseThrow();
        assertThat(ownerView.canResolve()).isTrue();
        assertThat(ownerView.canDelete()).isTrue();

        memberRepository.save(new PersistentProjectMemberRecord(
                projectRepository.findAll().get(0).getId(), "outsider", ProjectRole.MEMBER));
        var otherView = comments.listComments(planId, OUTSIDER).stream()
                .filter(c -> c.id() == created.id()).findFirst().orElseThrow();
        assertThat(otherView.canResolve()).isFalse();
        assertThat(otherView.canDelete()).isFalse();
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
}
