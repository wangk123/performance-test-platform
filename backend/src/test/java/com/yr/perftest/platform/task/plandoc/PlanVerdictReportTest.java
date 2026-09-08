package com.yr.perftest.platform.task.plandoc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.execution.TaskExecutionResult;
import com.yr.perftest.platform.execution.aggregate.PersistentAggregateReportRecord;
import com.yr.perftest.platform.execution.aggregate.PersistentAggregateReportRepository;
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
import org.springframework.test.annotation.DirtiesContext;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-verdict-report-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PlanVerdictReportTest {

    private static final HumanPrincipal OWNER = new HumanPrincipal("owner", Set.of(SystemRole.PROJECT_MEMBER));

    @Autowired
    private PlanWorkflowService workflow;
    @Autowired
    private PlanVerdictService verdictService;
    @Autowired
    private PersistentTaskPlanRepository planRepository;
    @Autowired
    private PersistentTaskScenarioRepository scenarioRepository;
    @Autowired
    private PersistentScenarioExecutionRepository executionRepository;
    @Autowired
    private PersistentAggregateReportRepository aggregateRepository;
    @Autowired
    private PersistentProjectRepository projectRepository;
    @Autowired
    private PersistentProjectMemberRepository memberRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private long planId;

    @BeforeEach
    void setUp() {
        PersistentProjectRecord project = projectRepository.save(new PersistentProjectRecord("P1", "项目一", "", "owner"));
        memberRepository.save(new PersistentProjectMemberRecord(project.getId(), "owner", ProjectRole.OWNER));
        PersistentTaskPlanRecord plan = planRepository.save(
                new PersistentTaskPlanRecord(project.getId(), "计划", null, "owner"));
        planId = plan.getId();
    }

    private void docWithMetrics(String conclusionSection) {
        PersistentTaskPlanRecord plan = planRepository.findById(planId).orElseThrow();
        plan.updateBody("""
                ## 二、测试目的与指标

                | 对象 | 指标 | 目标值 | 口径 |
                |---|---|---|---|
                | 登录场景 | TPS | ≥ 700 | 5 分钟均值 |

                ## 七、场景设计

                ### S1 登录场景 · SINGLE_TXN

                #### 执行记录

                ## 十一、结论

                """ + conclusionSection);
        planRepository.save(plan);
    }

    private void finishedExecution(String scenarioName) {
        long sid = scenarioRepository.save(new PersistentTaskScenarioRecord(planId, null, scenarioName, 1)).getId();
        PersistentScenarioExecutionRecord execution =
                executionRepository.save(new PersistentScenarioExecutionRecord(sid, "{\"threads\":50}"));
        execution.markSuccess(0);
        executionRepository.save(execution);
        try {
            TaskExecutionResult.Summary summary = new TaskExecutionResult.Summary(1000, 623.5, 120, 912, 0.32, "final");
            aggregateRepository.save(new PersistentAggregateReportRecord(
                    execution.getId(), "final", 0L, 600_000L, 600d,
                    objectMapper.writeValueAsString(summary),
                    objectMapper.writeValueAsString(List.<TaskExecutionResult.AggregateRow>of()),
                    new byte[0], Instant.now(), "test"));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void reportWithMetricsRedrawsVerdictTableAndSkipsLegacyFill() {
        docWithMetrics("""
                ### 指标达成表

                | 指标 | 目标 | 实际结果 | 状态 |
                |---|---|---|---|
                | 登录 TPS | ≥ 200 | 待执行 | 待判定 |

                ### 风险与建议

                （发布前填写。）

                **总体结论**：（发布时填写）
                """);
        finishedExecution("登录场景");
        forceReportPhase();

        TaskPlan report = workflow.generateReport(planId, OWNER);
        assertThat(report.status()).isEqualTo(PlanStatus.DONE);
        String body = planRepository.findById(planId).orElseThrow().getBody();
        assertThat(body).contains("<!-- backfill:verdict -->");
        assertThat(body).contains("#### 指标达成表（生成于");
        assertThat(body).contains("| 登录场景 | TPS | ≥ 700 | 623.5 | 未达成 |"); // 数值实际列
        assertThat(body).contains("- 自动判定：未达成（1 项未达标）——登录场景 TPS");
        assertThat(body).doesNotContain("| 登录 TPS | ≥ 200 | 待执行 | 待判定 |"); // 旧占位表被重绘
        assertThat(body).doesNotContain("待执行"); // 有指标路径不再跑 fillConclusionActualColumn 场景摘要
        assertThat(body).contains("### 风险与建议"); // 块外内容保留
        assertThat(body).contains("**总体结论**：（发布时填写）"); // 总体结论不预写（V7）
    }

    @Test
    void regenerateIsIdempotentBlockReplace() {
        docWithMetrics("""
                ### 指标达成表

                | 指标 | 目标 | 实际结果 | 状态 |
                |---|---|---|---|
                | 登录 TPS | ≥ 200 | 待执行 | 待判定 |
                """);
        finishedExecution("登录场景");
        forceReportPhase();
        workflow.generateReport(planId, OWNER);
        int revisionAfterFirst = planRepository.findById(planId).orElseThrow().getRevision();
        workflow.generateReport(planId, OWNER);
        String body = planRepository.findById(planId).orElseThrow().getBody();
        assertThat(body.split("<!-- backfill:verdict -->", -1).length - 1).isEqualTo(1);
        assertThat(body.split("#### 指标达成表（生成于", -1).length - 1).isEqualTo(1);
        assertThat(planRepository.findById(planId).orElseThrow().getRevision())
                .isEqualTo(revisionAfterFirst + 1); // 系统回填每次 revision+1
    }

    @Test
    void reportWithoutMetricsKeepsP0ZeroOneBehaviour() {
        PersistentTaskPlanRecord plan = planRepository.findById(planId).orElseThrow();
        plan.updateBody("""
                ## 七、场景设计

                ### S1 登录场景 · SINGLE_TXN

                #### 执行记录

                ## 十一、结论

                ### 指标达成表

                | 指标 | 目标 | 实际结果 | 状态 |
                |---|---|---|---|
                | 登录场景 TPS | ≥ 200 | 待执行 | 待判定 |
                """);
        planRepository.save(plan);
        finishedExecution("登录场景");
        forceReportPhase();
        workflow.generateReport(planId, OWNER);
        String body = planRepository.findById(planId).orElseThrow().getBody();
        assertThat(body).doesNotContain("<!-- backfill:verdict -->"); // 无指标不重绘
        assertThat(body).contains("<!-- backfill:report -->");        // 总览照旧
        assertThat(body).contains("登录场景");                          // 实际列照 P0-1 现状填场景摘要
    }

    private void forceReportPhase() {
        PersistentTaskPlanRecord plan = planRepository.findById(planId).orElseThrow();
        plan.forceState(PlanPhase.REPORT, PlanStatus.PENDING);
        planRepository.save(plan);
    }

    @Test
    void verdictAvailabilityFollowsRealGeneratePublishAndNewRevisionChain() {
        docWithMetrics("""
                ### 指标达成表

                | 指标 | 目标 | 实际结果 | 状态 |
                |---|---|---|---|
                | 登录 TPS | ≥ 200 | 待执行 | 待判定 |
                """);
        finishedExecution("登录场景");
        forceReportPhase();
        assertThat(verdictService.view(planId).available()).isFalse(); // REPORT/PENDING 未生成不可读

        workflow.generateReport(planId, OWNER); // REPORT/DONE
        PlanVerdictService.VerdictView generated = verdictService.view(planId);
        assertThat(generated.available()).isTrue();
        assertThat(generated.present()).isTrue();
        assertThat(generated.overall()).isEqualTo("FAILED"); // 真实判等：TPS 623.5 < 700

        workflow.publish(planId, OWNER, "结论"); // PUBLISH/PUBLISHED
        assertThat(verdictService.view(planId).available()).isTrue();

        workflow.newRevision(planId, OWNER); // 复测重置回 DRAFT
        PlanVerdictService.VerdictView reset = verdictService.view(planId);
        assertThat(reset.available()).isFalse();
        assertThat(reset.overall()).isEqualTo("NONE");
        assertThat(reset.rows()).isEmpty();
    }
}
