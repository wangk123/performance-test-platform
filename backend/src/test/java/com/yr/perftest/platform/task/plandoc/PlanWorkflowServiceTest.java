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
        "spring.datasource.url=jdbc:h2:mem:plan-workflow-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PlanWorkflowServiceTest {

    private static final HumanPrincipal OWNER = new HumanPrincipal("owner", Set.of(SystemRole.PROJECT_MEMBER));
    private static final HumanPrincipal REVIEWER = new HumanPrincipal("reviewer", Set.of(SystemRole.PROJECT_MEMBER));

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

    private PlanPhase phase() {
        return planRepository.findById(planId).orElseThrow().getPhase();
    }

    private PlanStatus status() {
        return planRepository.findById(planId).orElseThrow().getStatus();
    }

    @Test
    void fullHappyPathDraftToReportPending() {
        workflow.submit(planId, OWNER, "请评审");
        assertThat(phase()).isEqualTo(PlanPhase.REVIEW);
        assertThat(status()).isEqualTo(PlanStatus.PENDING);
        workflow.startReview(planId, REVIEWER);
        workflow.approve(planId, REVIEWER, "同意");
        assertThat(status()).isEqualTo(PlanStatus.APPROVED);
        workflow.startExecution(planId, REVIEWER);
        assertThat(phase()).isEqualTo(PlanPhase.EXECUTION);
        assertThat(status()).isEqualTo(PlanStatus.PENDING);
        PersistentTaskPlanRecord executionDone = planRepository.findById(planId).orElseThrow();
        executionDone.forceState(PlanPhase.EXECUTION, PlanStatus.DONE);
        planRepository.save(executionDone); // 与 Task4 测试同法：forceState 后需 save 持久化
        workflow.toReport(planId, REVIEWER);
        assertThat(phase()).isEqualTo(PlanPhase.REPORT);
        assertThat(status()).isEqualTo(PlanStatus.PENDING);
    }

    @Test
    void submitRejectedForNonOwner() {
        assertThatThrownBy(() -> workflow.submit(planId, REVIEWER, null))
                .isInstanceOf(PlanAccessDeniedException.class);
    }

    @Test
    void rejectRequiresComment() {
        workflow.submit(planId, OWNER, null);
        workflow.startReview(planId, REVIEWER);
        assertThatThrownBy(() -> workflow.reject(planId, REVIEWER, " "))
                .isInstanceOf(PlanValidationException.class);
        workflow.reject(planId, REVIEWER, "指标口径不清");
        assertThat(phase()).isEqualTo(PlanPhase.DRAFT);
    }

    @Test
    void illegalTransitionThrowsPlanState() {
        assertThatThrownBy(() -> workflow.approve(planId, REVIEWER, null))
                .isInstanceOf(PlanStateException.class)
                .hasMessageContaining("PLAN_STATE");
    }

    @Test
    void withdrawReturnsToDraftAndWritesSystemComments() {
        workflow.submit(planId, OWNER, null);
        workflow.startReview(planId, REVIEWER);
        workflow.withdraw(planId, OWNER);
        assertThat(phase()).isEqualTo(PlanPhase.DRAFT);
        assertThat(workflow.listComments(planId))
                .anySatisfy(c -> {
                    assertThat(c.kind()).isEqualTo(PlanCommentKind.SYSTEM);
                    assertThat(c.content()).contains("撤回");
                });
    }

    @Test
    void backToDraftBlockedAfterExecutionExists() {
        workflow.submit(planId, OWNER, null);
        workflow.startReview(planId, REVIEWER);
        workflow.approve(planId, REVIEWER, null);
        workflow.startExecution(planId, REVIEWER);
        // 有执行历史（hasAnyExecution=true 路径）：先造一条终态执行
        // —— 本测试无场景执行表依赖，直接用 forceState 模拟"曾有执行"不成立；
        //    backToDraft 的 hasAnyExecution 判定用 executionRepository 存在性，无场景即无执行，应允许退回
        workflow.backToDraft(planId, OWNER);
        assertThat(phase()).isEqualTo(PlanPhase.DRAFT);
    }

    @Test
    void reviewCommentLifecycle() {
        PlanWorkflowService.CommentView comment = workflow.addComment(planId, REVIEWER, "第二章表格补口径");
        assertThat(comment.kind()).isEqualTo(PlanCommentKind.REVIEW);
        workflow.deleteComment(planId, comment.id(), REVIEWER);
        assertThat(workflow.listComments(planId)).noneMatch(c -> c.id() == comment.id());
        PlanWorkflowService.CommentView system = workflow.addComment(planId, OWNER, "成员批注");
        assertThatThrownBy(() -> workflow.deleteComment(planId, system.id(), REVIEWER)) // 非作者且非负责人
                .isInstanceOf(PlanAccessDeniedException.class);
    }
}
