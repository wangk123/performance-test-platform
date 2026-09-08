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
        "spring.datasource.url=jdbc:h2:mem:plan-verdict-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PlanVerdictServiceTest {

    private static final HumanPrincipal OWNER = new HumanPrincipal("owner", Set.of(SystemRole.PROJECT_MEMBER));

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

    private long scenario(String name) {
        return scenarioRepository.save(new PersistentTaskScenarioRecord(planId, null, name, 1)).getId();
    }

    private long finishedExecution(long scenarioId, TaskExecutionResult.Summary summary,
                                   List<TaskExecutionResult.AggregateRow> rows) {
        PersistentScenarioExecutionRecord execution =
                executionRepository.save(new PersistentScenarioExecutionRecord(scenarioId, "{\"threads\":50}"));
        execution.markSuccess(0);
        executionRepository.save(execution);
        try {
            aggregateRepository.save(new PersistentAggregateReportRecord(
                    execution.getId(), "final", 0L, 600_000L, 600d,
                    objectMapper.writeValueAsString(summary),
                    objectMapper.writeValueAsString(rows),
                    new byte[0], Instant.now(), "test"));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        return execution.getId();
    }

    private void doc(String metricTable) {
        PersistentTaskPlanRecord plan = planRepository.findById(planId).orElseThrow();
        plan.updateBody("## 二、测试目的与指标\n\n" + metricTable + "\n\n## 十一、结论\n\n（空）\n");
        planRepository.save(plan);
    }

    private static TaskExecutionResult.Summary summary(double throughput, long avgRt, long p95, double errorRate) {
        return new TaskExecutionResult.Summary(1000, throughput, avgRt, p95, errorRate, "final");
    }

    private static TaskExecutionResult.AggregateRow row(String label, long average, long p95, long p99,
                                                        double errorRate, double throughput) {
        return new TaskExecutionResult.AggregateRow(label, "线程组", 500, average, average, average,
                p95, p99, 1, 999, errorRate, throughput);
    }

    @Test
    void scenarioBindingJudgesFourMetricsWithDirections() {
        long sid = scenario("登录场景");
        long eid = finishedExecution(sid, summary(623.5, 120, 912, 0.32),
                List.of(row("登录", 120, 912, 1500, 0.32, 623.5)));
        doc("""
                | 对象 | 指标 | 目标值 | 口径 |
                |---|---|---|---|
                | 登录场景 | TPS | ≥ 500 | 5 分钟均值 |
                | 登录场景 | TPS | ≥ 700 | 5 分钟均值 |
                | 登录场景 | P95 | ≤ 800 ms | 全量样本 |
                | 登录场景 | 错误率 | ≤ 0.5% | 全量样本 |
                | 登录场景 | 平均RT | ≤ 200 ms | 全量样本 |""");
        PlanVerdictService.VerdictResult result = verdictService.compute(planId);
        assertThat(result.present()).isTrue();
        List<PlanVerdictService.VerdictRow> rows = result.rows();
        assertThat(rows.get(0).status()).isEqualTo(PlanVerdictService.VerdictStatus.ACHIEVED); // 623.5 ≥ 500
        assertThat(rows.get(1).status()).isEqualTo(PlanVerdictService.VerdictStatus.MISSED);   // 623.5 < 700
        assertThat(rows.get(2).status()).isEqualTo(PlanVerdictService.VerdictStatus.MISSED);   // 912 > 800
        assertThat(rows.get(3).status()).isEqualTo(PlanVerdictService.VerdictStatus.ACHIEVED); // 0.32 ≤ 0.5
        assertThat(rows.get(4).status()).isEqualTo(PlanVerdictService.VerdictStatus.ACHIEVED); // 平均RT 120 ≤ 200
        assertThat(rows.get(0).actualValue()).isEqualTo("623.5");
        assertThat(rows.get(2).actualValue()).isEqualTo("912ms");
        assertThat(rows.get(3).actualValue()).isEqualTo("0.32%");
        assertThat(rows.get(4).actualValue()).isEqualTo("120ms");
        assertThat(rows.get(0).scenarioId()).isEqualTo(sid);
        assertThat(rows.get(0).executionId()).isEqualTo(eid);
        assertThat(result.overall()).isEqualTo("FAILED"); // 任一未达成 → 未达成
        assertThat(result.prefillConclusion()).contains("未达成").contains("登录场景");
    }

    @Test
    void transactionBindingJudgesWithP99AndScenarioP99IsIndeterminate() {
        long sid = scenario("组合场景");
        finishedExecution(sid, summary(800, 100, 500, 0.1),
                List.of(row("下单交易", 80, 300, 950, 0.05, 400.2)));
        doc("""
                | 对象 | 指标 | 目标值 | 口径 |
                |---|---|---|---|
                | 下单交易 | P99 | ≤ 1000 ms | 全量样本 |
                | 下单交易 | TPS | ≥ 500 | 5 分钟均值 |
                | 组合场景 | P99 | ≤ 1000 ms | 全量样本 |
                | 下单交易 | 平均RT | ≤ 100 ms | 全量样本 |""");
        List<PlanVerdictService.VerdictRow> rows = verdictService.compute(planId).rows();
        assertThat(rows.get(0).status()).isEqualTo(PlanVerdictService.VerdictStatus.ACHIEVED);  // 交易级 P99 950 ≤ 1000
        assertThat(rows.get(0).actualValue()).isEqualTo("950ms");
        assertThat(rows.get(1).status()).isEqualTo(PlanVerdictService.VerdictStatus.MISSED);    // 400.2 < 500
        assertThat(rows.get(2).status()).isEqualTo(PlanVerdictService.VerdictStatus.INDETERMINATE);
        assertThat(rows.get(2).reason()).contains("P99");
        assertThat(rows.get(3).status()).isEqualTo(PlanVerdictService.VerdictStatus.ACHIEVED);  // 交易级平均RT 80 ≤ 100
        assertThat(rows.get(3).actualValue()).isEqualTo("80ms");
    }

    @Test
    void ambiguousLabelAndNoMatchAndNoExecutionAreIndeterminate() {
        long sid1 = scenario("场景一");
        long sid2 = scenario("场景二");
        finishedExecution(sid1, summary(100, 50, 100, 0),
                List.of(row("下单交易", 50, 100, 200, 0, 100)));
        finishedExecution(sid2, summary(100, 50, 100, 0),
                List.of(row("下单交易", 50, 100, 200, 0, 100)));
        long sid3 = scenario("未执行场景");
        // sid3 无执行记录
        doc("""
                | 对象 | 指标 | 目标值 | 口径 |
                |---|---|---|---|
                | 下单交易 | TPS | ≥ 50 | 口径 |
                | 不存在的对象 | TPS | ≥ 50 | 口径 |
                | 未执行场景 | TPS | ≥ 50 | 口径 |""");
        List<PlanVerdictService.VerdictRow> rows = verdictService.compute(planId).rows();
        assertThat(rows.get(0).status()).isEqualTo(PlanVerdictService.VerdictStatus.INDETERMINATE);
        assertThat(rows.get(0).reason()).contains("多个场景");
        assertThat(rows.get(1).status()).isEqualTo(PlanVerdictService.VerdictStatus.INDETERMINATE);
        assertThat(rows.get(1).reason()).contains("未匹配");
        assertThat(rows.get(2).status()).isEqualTo(PlanVerdictService.VerdictStatus.INDETERMINATE);
        assertThat(rows.get(2).reason()).contains("无执行");
        assertThat(verdictService.compute(planId).overall()).isEqualTo("INDETERMINATE");
    }

    @Test
    void peakConcurrencyCapacityOtherAreIndeterminateWithReason() {
        long sid = scenario("登录场景");
        finishedExecution(sid, summary(600, 100, 400, 0.1), List.of());
        doc("""
                | 对象 | 指标 | 目标值 | 口径 |
                |---|---|---|---|
                | 登录场景 | 并发峰值 | ≥ 1000 | - |
                | 登录场景 | 容量 | 1万在线用户 | - |
                | 登录场景 | GC 次数 | 少于 10 | 人工 |""");
        List<PlanVerdictService.VerdictRow> rows = verdictService.compute(planId).rows();
        assertThat(rows).allMatch(r -> r.status() == PlanVerdictService.VerdictStatus.INDETERMINATE);
        assertThat(rows.get(0).reason()).contains("不可自动判定");
    }

    @Test
    void noMetricPlanProducesNoVerdict() {
        scenario("登录场景");
        PersistentTaskPlanRecord plan = planRepository.findById(planId).orElseThrow();
        plan.updateBody("## 一、背景\n\n摸底计划\n");
        planRepository.save(plan);
        PlanVerdictService.VerdictResult result = verdictService.compute(planId);
        assertThat(result.present()).isFalse();
        assertThat(result.overall()).isEqualTo("NONE");
        assertThat(result.rows()).isEmpty();
        assertThat(result.prefillConclusion()).isNull();
    }

    @Test
    void legacyBrokenSectionDegradesToIndeterminateNotBlocking() {
        PersistentTaskPlanRecord plan = planRepository.findById(planId).orElseThrow();
        plan.updateBody("## 二、测试目的与指标\n\n| 指标名 | 数值 |\n|---|---|\n| TPS | 200 |\n");
        planRepository.save(plan);
        PlanVerdictService.VerdictResult result = verdictService.compute(planId);
        assertThat(result.present()).isFalse(); // lenient 兜底为空 → 无指标路径（保存校验上线后新文档不可能出现）
        assertThat(result.overall()).isEqualTo("NONE");
    }

    @Test
    void viewGatesByPhaseAndResetsAfterNewRevision() {
        doc("""
                | 对象 | 指标 | 目标值 | 口径 |
                |---|---|---|---|
                | 登录场景 | TPS | ≥ 500 | 口径 |""");
        PersistentTaskPlanRecord plan = planRepository.findById(planId).orElseThrow();
        plan.forceState(PlanPhase.REPORT, PlanStatus.PENDING); // 报告未生成
        planRepository.save(plan);
        PlanVerdictService.VerdictView before = verdictService.view(planId);
        assertThat(before.present()).isTrue();
        assertThat(before.available()).isFalse();
        assertThat(before.overall()).isEqualTo("NONE");
        assertThat(before.rows()).isEmpty();

        plan.forceState(PlanPhase.REPORT, PlanStatus.DONE);
        planRepository.save(plan);
        PlanVerdictService.VerdictView after = verdictService.view(planId);
        assertThat(after.available()).isTrue();
        assertThat(after.overall()).isEqualTo("INDETERMINATE"); // 有指标行、场景无执行 → 无法判定
        assertThat(after.prefillConclusion()).contains("无法判定");

        plan.forceState(PlanPhase.PUBLISH, PlanStatus.PUBLISHED);
        planRepository.save(plan);
        assertThat(verdictService.view(planId).available()).isTrue();
    }

    @Test
    void prefillTextCountsSummary() {
        long sid = scenario("登录场景");
        finishedExecution(sid, summary(623.5, 120, 912, 0.32), List.of());
        doc("""
                | 对象 | 指标 | 目标值 | 口径 |
                |---|---|---|---|
                | 登录场景 | TPS | ≥ 500 | 口径 |
                | 登录场景 | 容量 | 1万在线 | 口径 |""");
        PlanVerdictService.VerdictResult result = verdictService.compute(planId);
        assertThat(result.overall()).isEqualTo("INDETERMINATE");
        assertThat(result.prefillConclusion())
                .contains("无法判定")
                .contains("1 项需人工评估");
    }

    @Test
    void successExecutionWithoutAggregateDataIsIndeterminate() {
        long sid = scenario("登录场景");
        PersistentScenarioExecutionRecord execution =
                executionRepository.save(new PersistentScenarioExecutionRecord(sid, "{\"threads\":50}"));
        execution.markSuccess(0);
        executionRepository.save(execution);
        // 不落聚合结果：模拟终态执行但无持久化快照（中断/取消/快照缺失），零样本不得按 0 值误判
        doc("""
                | 对象 | 指标 | 目标值 | 口径 |
                |---|---|---|---|
                | 登录场景 | TPS | ≥ 500 | 口径 |""");
        PlanVerdictService.VerdictResult result = verdictService.compute(planId);
        assertThat(result.rows().get(0).status()).isEqualTo(PlanVerdictService.VerdictStatus.INDETERMINATE);
        assertThat(result.rows().get(0).reason()).contains("无聚合数据");
        assertThat(result.overall()).isEqualTo("INDETERMINATE");
    }
}
