package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.identity.SystemRole;
import com.yr.perftest.platform.project.PersistentProjectMemberRecord;
import com.yr.perftest.platform.project.PersistentProjectMemberRepository;
import com.yr.perftest.platform.project.PersistentProjectRecord;
import com.yr.perftest.platform.project.PersistentProjectRepository;
import com.yr.perftest.platform.project.ProjectRole;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRecord;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRepository;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.PersistentTaskScenarioRecord;
import com.yr.perftest.platform.task.PersistentTaskScenarioRepository;
import com.yr.perftest.platform.task.TaskPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 四流转单行道（spec 2026-09-11 §4.1）：submit/approve/finishExecution/publish；无回退动作。 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-workflow-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.h2.console.enabled=false"
})
@Transactional
class PlanWorkflowServiceTest {

    private static final HumanPrincipal OWNER = new HumanPrincipal("owner", Set.of(SystemRole.PROJECT_MEMBER));
    private static final HumanPrincipal REVIEWER = new HumanPrincipal("reviewer", Set.of(SystemRole.PROJECT_MEMBER));
    private static final HumanPrincipal OUTSIDER = new HumanPrincipal("outsider", Set.of(SystemRole.PROJECT_MEMBER));

    @Autowired
    private PlanWorkflowService workflow;
    @Autowired
    private PlanCommentService comments;
    @Autowired
    private PlanDocumentService documentService;
    @Autowired
    private PersistentTaskPlanRepository planRepository;
    @Autowired
    private PersistentTaskScenarioRepository scenarioRepository;
    @Autowired
    private PersistentScenarioExecutionRepository executionRepository;
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

    private PlanStatus status() {
        return planRepository.findById(planId).orElseThrow().getStatus();
    }

    private PersistentTaskPlanRecord forceState(PlanStatus status) {
        PersistentTaskPlanRecord plan = planRepository.findById(planId).orElseThrow();
        plan.forceState(status);
        return planRepository.save(plan); // forceState 后需 save 持久化
    }

    /** 造一个 RUNNING 活跃执行（工厂方法同 PlanScenarioMutationGateTest，QUEUED/RUNNING/STOPPING 视为活跃）。 */
    private void startActiveExecution() {
        PersistentTaskScenarioRecord scenario = scenarioRepository.save(
                new PersistentTaskScenarioRecord(planId, null, "场景A", 0));
        PersistentScenarioExecutionRecord execution = executionRepository.save(
                new PersistentScenarioExecutionRecord(scenario.getId(),
                        "{\"threads\":1,\"rampUp\":0,\"duration\":1,\"loops\":1,\"jmeterProperties\":{}}"));
        execution.markRunning("result.jtl", "jmeter.log"); // RUNNING = 活跃执行
        executionRepository.save(execution);
    }

    @Test
    void fullHappyPathPlanningToPublished() {
        workflow.submit(planId, OWNER, "请评审");
        assertThat(status()).isEqualTo(PlanStatus.IN_REVIEW);
        workflow.approve(planId, REVIEWER, "同意");
        assertThat(status()).isEqualTo(PlanStatus.EXECUTING);
        workflow.finishExecution(planId, REVIEWER);
        assertThat(status()).isEqualTo(PlanStatus.REPORTING);
        TaskPlan published = workflow.publish(planId, OWNER, "结论：通过", "V1.0");
        assertThat(published.status()).isEqualTo(PlanStatus.PUBLISHED);
    }

    @Test
    void submitMovesPlanningToInReview() {
        workflow.submit(planId, OWNER, "请评审");
        assertThat(planRepository.findById(planId).orElseThrow().getStatus())
                .isEqualTo(PlanStatus.IN_REVIEW);
    }

    @Test
    void approveMovesInReviewToExecuting() {
        forceState(PlanStatus.IN_REVIEW);
        workflow.approve(planId, REVIEWER, null);
        assertThat(planRepository.findById(planId).orElseThrow().getStatus())
                .isEqualTo(PlanStatus.EXECUTING);
    }

    @Test
    void finishExecutionMovesToReporting() {
        forceState(PlanStatus.EXECUTING);
        workflow.finishExecution(planId, REVIEWER);
        assertThat(planRepository.findById(planId).orElseThrow().getStatus())
                .isEqualTo(PlanStatus.REPORTING);
    }

    @Test
    void finishExecutionRejectedWhenNotExecuting() {
        forceState(PlanStatus.PLANNING);
        assertThatThrownBy(() -> workflow.finishExecution(planId, REVIEWER))
                .isInstanceOf(PlanStateException.class);
    }

    @Test
    void publishMovesReportingToPublished() {
        forceState(PlanStatus.REPORTING);
        TaskPlan published = workflow.publish(planId, OWNER, "结论：通过", "V1.0");
        assertThat(planRepository.findById(planId).orElseThrow().getStatus())
                .isEqualTo(PlanStatus.PUBLISHED);
        assertThat(published.status()).isEqualTo(PlanStatus.PUBLISHED);
    }

    @Test
    void finishExecutionSucceedsWithActiveExecution() {
        forceState(PlanStatus.EXECUTING);
        startActiveExecution();
        // 软门禁（spec §4.3）：真实存在 RUNNING 活跃执行，后端仍放行执行完成（不 409，前端二次确认兜底）
        assertThat(documentService.countActiveExecutions(planId)).isPositive();
        workflow.finishExecution(planId, REVIEWER);
        assertThat(planRepository.findById(planId).orElseThrow().getStatus())
                .isEqualTo(PlanStatus.REPORTING);
    }

    @Test
    void publishSucceedsEvenWithActiveExecution() {
        forceState(PlanStatus.REPORTING);
        startActiveExecution();
        // 软门禁（spec §4.3）：真实存在 RUNNING 活跃执行，后端仍放行发布（原 409 拦截已删除，回归即本用例红）
        assertThat(documentService.countActiveExecutions(planId)).isPositive();
        workflow.publish(planId, OWNER, "结论：通过", "V1.0");
        assertThat(planRepository.findById(planId).orElseThrow().getStatus())
                .isEqualTo(PlanStatus.PUBLISHED);
    }

    @Test
    void illegalStartingStatusThrowsPlanState() {
        // 每个流转动作在错误起始状态一律 409 PLAN_STATE
        assertThatThrownBy(() -> workflow.approve(planId, REVIEWER, null))
                .isInstanceOf(PlanStateException.class)
                .hasMessageContaining("PLAN_STATE");
        assertThatThrownBy(() -> workflow.finishExecution(planId, REVIEWER))
                .isInstanceOf(PlanStateException.class)
                .hasMessageContaining("PLAN_STATE");
        assertThatThrownBy(() -> workflow.publish(planId, OWNER, "结论", "V1.0"))
                .isInstanceOf(PlanStateException.class)
                .hasMessageContaining("PLAN_STATE");
        forceState(PlanStatus.PUBLISHED);
        assertThatThrownBy(() -> workflow.submit(planId, OWNER, null))
                .isInstanceOf(PlanStateException.class)
                .hasMessageContaining("PLAN_STATE");
    }

    @Test
    void submitRejectedForNonMember() {
        assertThatThrownBy(() -> workflow.submit(planId, OUTSIDER, null))
                .isInstanceOf(PlanAccessDeniedException.class);
    }

    @Test
    void reviewCommentLifecycle() {
        PlanCommentService.CommentView comment = comments.addComment(
                planId, REVIEWER, new PlanCommentService.AddCommentCommand("第二章表格补口径", null, null));
        assertThat(comment.kind()).isEqualTo(PlanCommentKind.REVIEW);
        comments.deleteComment(planId, comment.id(), REVIEWER);
        assertThat(comments.listComments(planId, OWNER)).noneMatch(c -> c.id() == comment.id());
        PlanCommentService.CommentView otherMember = comments.addComment(
                planId, OWNER, new PlanCommentService.AddCommentCommand("成员批注", null, null));
        assertThatThrownBy(() -> comments.deleteComment(planId, otherMember.id(), REVIEWER)) // 非作者且非负责人
                .isInstanceOf(PlanAccessDeniedException.class);
    }
}
