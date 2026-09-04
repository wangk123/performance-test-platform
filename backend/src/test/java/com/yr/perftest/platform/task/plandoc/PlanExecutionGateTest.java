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
import com.yr.perftest.platform.task.PersistentTaskScenarioRecord;
import com.yr.perftest.platform.task.PersistentTaskScenarioRepository;
import com.yr.perftest.platform.task.TestType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-gate-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PlanExecutionGateTest {

    private static final HumanPrincipal OWNER = new HumanPrincipal("owner", Set.of(SystemRole.PROJECT_MEMBER));
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private PlanWorkflowService workflow;
    @Autowired
    private PersistentTaskPlanRepository planRepository;
    @Autowired
    private PersistentTaskScenarioRepository scenarioRepository;
    @Autowired
    private PersistentProjectRepository projectRepository;
    @Autowired
    private PersistentProjectMemberRepository memberRepository;

    private long planId;
    private long scenarioId;

    @BeforeEach
    void setUp() throws Exception {
        PersistentProjectRecord project = projectRepository.save(new PersistentProjectRecord("P1", "项目一", "", "owner"));
        memberRepository.save(new PersistentProjectMemberRecord(project.getId(), "owner", ProjectRole.OWNER));
        PersistentTaskPlanRecord plan = planRepository.save(
                new PersistentTaskPlanRecord(project.getId(), "计划", null, "owner"));
        plan.updateBody("## 二、测试目的与指标\n\n| 交易 | 指标 | 目标值 | 口径 |\n|---|---|---|---|\n| 查询 | TPS | 200 | 均值 |\n\n"
                + "## 五、测试约束\n\n### 入口准则\n\n- [ ] 指标已定义（自动）\n- [ ] 环境就绪（人工）\n\n## 七、场景设计\n");
        plan.forceState(PlanPhase.EXECUTION, PlanStatus.PENDING);
        planId = planRepository.save(plan).getId();
        PersistentTaskScenarioRecord scenario = scenarioRepository.save(
                new PersistentTaskScenarioRecord(planId, null, "场景A", 0));
        scenario.updateBusinessFields("目的", TestType.SINGLE_TXN);
        scenarioId = scenarioRepository.save(scenario).getId();
    }

    private void enablePrecheck() throws Exception {
        workflow.updatePrecheckSettings(planId, OWNER,
                new PrecheckSettings(true, List.of("指标已定义", "环境就绪")));
    }

    @Test
    void gateRejectsWhenPlanNotPastReview() {
        PersistentTaskPlanRecord plan = planRepository.findById(planId).orElseThrow();
        plan.forceState(PlanPhase.REVIEW, PlanStatus.IN_REVIEW);
        planRepository.save(plan);
        assertThatThrownBy(() -> workflow.assertExecutionAllowed(scenarioId))
                .isInstanceOf(PlanStateException.class)
                .hasMessageContaining("请先通过评审并进入执行阶段");
    }

    @Test
    void gateRejectsUnboundScript() {
        assertThatThrownBy(() -> workflow.assertExecutionAllowed(scenarioId))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("未关联脚本");
    }

    @Test
    void gatePassesAndSkipsPrecheckWhenDisabled() {
        PersistentTaskScenarioRecord bound = scenarioRepository.findById(scenarioId).orElseThrow();
        bound.bindScript(9L);
        scenarioRepository.save(bound);
        long resolved = workflow.assertExecutionAllowed(scenarioId);
        assertThat(resolved).isEqualTo(planId);
        assertThat(planRepository.findById(planId).orElseThrow().getPrecheckExecutedAt()).isNull();
    }

    @Test
    void firstExecutionWithPrecheckEnabledRunsItAndBlocksOnManualItems() throws Exception {
        PersistentTaskScenarioRecord bound = scenarioRepository.findById(scenarioId).orElseThrow();
        bound.bindScript(9L);
        scenarioRepository.save(bound);
        enablePrecheck();
        assertThatThrownBy(() -> workflow.assertExecutionAllowed(scenarioId))
                .isInstanceOf(PlanPrecheckFailedException.class)
                .hasMessageContaining("环境就绪");
        // 跳过后放行且不再重跑
        workflow.precheckSkip(planId, OWNER);
        workflow.assertExecutionAllowed(scenarioId);
        assertThat(workflow.listComments(planId)).anySatisfy(c -> {
            assertThat(c.kind()).isEqualTo(PlanCommentKind.SYSTEM);
            assertThat(c.content()).contains("跳过环境检查");
        });
    }

    @Test
    void onExecutionStartedResetsReportPhaseAndRuns() {
        PersistentTaskPlanRecord reset = planRepository.findById(planId).orElseThrow();
        reset.forceState(PlanPhase.REPORT, PlanStatus.DONE);
        planRepository.save(reset);
        workflow.onExecutionStarted(planId);
        PersistentTaskPlanRecord plan = planRepository.findById(planId).orElseThrow();
        assertThat(plan.getPhase()).isEqualTo(PlanPhase.EXECUTION);
        assertThat(plan.getStatus()).isEqualTo(PlanStatus.RUNNING);
        planRepository.save(plan).forceState(PlanPhase.EXECUTION, PlanStatus.DONE);
        workflow.onExecutionStarted(planId);
        assertThat(planRepository.findById(planId).orElseThrow().getStatus()).isEqualTo(PlanStatus.RUNNING);
    }

    @Test
    void precheckSettingsUpdateDoesNotBumpRevision() throws Exception {
        int before = planRepository.findById(planId).orElseThrow().getRevision();
        enablePrecheck();
        assertThat(planRepository.findById(planId).orElseThrow().getRevision()).isEqualTo(before);
        assertThat(workflow.getPrecheckSettings(planId).enabled()).isTrue();
    }

    @Test
    void manualPrecheckRunWritesBackAutoChecks() {
        PersistentTaskScenarioRecord bound = scenarioRepository.findById(scenarioId).orElseThrow();
        bound.bindScript(9L);
        scenarioRepository.save(bound);
        workflow.updatePrecheckSettings(planId, OWNER, new PrecheckSettings(true, List.of("指标已定义", "场景已配置")));
        PlanWorkflowService.PrecheckReport report = workflow.runPrecheck(planId, true);
        assertThat(report.ok()).isTrue();
        assertThat(report.autoPassed()).containsExactly("指标已定义", "场景已配置");
        // 指标已定义=自动通过 → 回写勾选（revision+1，系统回填语义）
        String constraints = PlanMarkdownSupport.extractSection(
                planRepository.findById(planId).orElseThrow().getBody(), "五、测试约束");
        assertThat(constraints).contains("- [x] 指标已定义（自动）");
    }
}
