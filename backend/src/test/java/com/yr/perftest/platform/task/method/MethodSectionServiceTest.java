package com.yr.perftest.platform.task.method;

import com.yr.perftest.platform.execution.aggregate.PersistentAggregateReportRecord;
import com.yr.perftest.platform.execution.aggregate.PersistentAggregateReportRepository;
import com.yr.perftest.platform.project.PersistentProjectRecord;
import com.yr.perftest.platform.project.PersistentProjectRepository;
import com.yr.perftest.platform.script.ScriptService;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRecord;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRepository;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.PersistentTaskScenarioRecord;
import com.yr.perftest.platform.task.PersistentTaskScenarioRepository;
import com.yr.perftest.platform.task.TestType;
import com.yr.perftest.platform.task.plandoc.PlanStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:method-section-service-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.h2.console.enabled=false",
        "platform.storage.root=./build/test-storage/method-section"
})
@Transactional
class MethodSectionServiceTest {

    private static final String CONFIG_JSON =
            "{\"threads\":300,\"rampUp\":10,\"duration\":600,\"loops\":1,\"jmeterProperties\":{},"
                    + "\"mode\":\"DISTRIBUTED\",\"controllerNodeId\":1,\"workerNodeIds\":[1],\"monitorTargetIds\":[]}";

    @Autowired
    private MethodSectionService methodSectionService;
    @Autowired
    private PersistentTaskPlanRepository planRepository;
    @Autowired
    private PersistentTaskScenarioRepository scenarioRepository;
    @Autowired
    private PersistentScenarioExecutionRepository executionRepository;
    @Autowired
    private PersistentAggregateReportRepository aggregateRepository;
    @Autowired
    private PlanEvidenceImageRepository imageRepository;
    @Autowired
    private PersistentProjectRepository projectRepository;
    @Autowired
    private ScriptService scriptService;

    private long planId;
    private long scenarioId;

    @BeforeEach
    void setUp() {
        PersistentProjectRecord project = projectRepository.save(
                new PersistentProjectRecord("P-MS", "测试方法章节", "", "owner"));
        long projectId = projectRepository.save(project).getId();

        PersistentTaskPlanRecord plan = planRepository.save(
                new PersistentTaskPlanRecord(projectId, "方法计划", null, "owner"));
        plan.forceState(PlanStatus.EXECUTING);
        planId = planRepository.save(plan).getId();

        long scriptVersionId = scriptService.createScript(projectId, "loan-submit", "tester").id();
        PersistentTaskScenarioRecord bound = scenarioRepository.save(
                new PersistentTaskScenarioRecord(planId, scriptVersionId, "场景A", 0));
        bound.updateBusinessFields("压测提交链路", TestType.SINGLE_TXN);
        scenarioId = scenarioRepository.save(bound).getId();
        scenarioRepository.save(new PersistentTaskScenarioRecord(planId, null, "场景B", 1));

        PersistentScenarioExecutionRecord success1 = executionRepository.save(
                new PersistentScenarioExecutionRecord(scenarioId, CONFIG_JSON));
        success1.setExecutionName("S1 300并发 首轮");
        success1.markRunning("r1.jtl", "j1.log");
        success1.markSuccess(0);
        executionRepository.save(success1);
        aggregateRepository.save(aggregate(success1.getId(),
                "{\"samples\":1000,\"throughput\":682.4,\"avgRt\":120,\"p95\":300,\"errorRate\":0.2,\"accuracy\":\"final\"}"));

        PersistentScenarioExecutionRecord success2 = executionRepository.save(
                new PersistentScenarioExecutionRecord(scenarioId, CONFIG_JSON));
        success2.setExecutionName("S1 300并发 复测");
        success2.markRunning("r2.jtl", "j2.log");
        success2.markSuccess(0);
        success2.setMethodHidden(true);
        executionRepository.save(success2);
        aggregateRepository.save(aggregate(success2.getId(),
                "{\"samples\":1200,\"throughput\":712.0,\"avgRt\":110,\"p95\":280,\"errorRate\":1.5,\"accuracy\":\"final\"}"));

        PersistentScenarioExecutionRecord running = executionRepository.save(
                new PersistentScenarioExecutionRecord(scenarioId, CONFIG_JSON));
        running.setExecutionName("S1 300并发 进行中");
        running.markRunning("r3.jtl", "j3.log");
        executionRepository.save(running);

        imageRepository.save(new PersistentPlanEvidenceImageRecord(
                planId, scenarioId, "TPS 截图", 0, "storage/evidence/tps.png", "image/png", 2048, "tester"));
        imageRepository.save(new PersistentPlanEvidenceImageRecord(
                planId, scenarioId, "RT 截图", 1, "storage/evidence/rt.png", "image/png", 4096, "tester"));
    }

    @Test
    void assemblesScenariosWithRowsAndImages() {
        MethodSectionResponse res = methodSectionService.getPlanMethod(planId);
        assertThat(res.planId()).isEqualTo(planId);
        assertThat(res.scenarios()).hasSize(2);

        MethodSectionResponse.ScenarioMethodData s1 = res.scenarios().get(0);
        assertThat(s1.scenarioId()).isEqualTo(scenarioId);
        assertThat(s1.name()).isEqualTo("场景A");
        assertThat(s1.testType()).isEqualTo("SINGLE_TXN");
        assertThat(s1.sortOrder()).isEqualTo(0);
        assertThat(s1.scriptVersionId()).isNotNull();
        assertThat(s1.scriptName()).isEqualTo("loan-submit.jmx");

        assertThat(s1.executions()).hasSize(3);
        assertThat(s1.executions())
                .extracting(MethodSectionResponse.ExecutionRow::tps)
                .contains(682.4, 712.0, (Double) null);
        MethodSectionResponse.ExecutionRow first = s1.executions().get(0);
        assertThat(first.executionName()).isEqualTo("S1 300并发 首轮");
        assertThat(first.status()).isEqualTo("SUCCESS");
        assertThat(first.successRate()).isEqualTo(99.8);
        assertThat(first.samples()).isEqualTo(1000L);
        assertThat(first.avgRtMs()).isEqualTo(120.0);
        assertThat(first.p95Ms()).isEqualTo(300.0);
        assertThat(first.threads()).isEqualTo(300);
        assertThat(first.rampUpSec()).isEqualTo(10);
        assertThat(first.durationSec()).isEqualTo(600);
        assertThat(first.startedAtText()).isNotNull();
        assertThat(first.hidden()).isFalse();

        assertThat(s1.executions().get(1).hidden()).isTrue();
        MethodSectionResponse.ExecutionRow runningRow = s1.executions().get(2);
        assertThat(runningRow.status()).isEqualTo("RUNNING");
        assertThat(runningRow.samples()).isNull();
        assertThat(runningRow.successRate()).isNull();
        assertThat(runningRow.avgRtMs()).isNull();
        assertThat(runningRow.p95Ms()).isNull();
        assertThat(runningRow.tps()).isNull();

        assertThat(s1.hiddenCount()).isEqualTo(1L);

        assertThat(s1.images()).hasSize(2);
        assertThat(s1.images().get(0).caption()).isEqualTo("TPS 截图");
        assertThat(s1.images().get(0).sortOrder()).isEqualTo(0);
        assertThat(s1.images().get(0).contentType()).isEqualTo("image/png");
        assertThat(s1.images().get(0).sizeBytes()).isEqualTo(2048L);
        assertThat(s1.images().get(1).executionId()).isNull();

        MethodSectionResponse.ScenarioMethodData s2 = res.scenarios().get(1);
        assertThat(s2.scriptVersionId()).isNull();
        assertThat(s2.scriptName()).isNull();
        assertThat(s2.executions()).isEmpty();
        assertThat(s2.hiddenCount()).isEqualTo(0L);
        assertThat(s2.images()).isEmpty();
    }

    @Test
    void returnsEmptyScenariosForPlanWithoutScenarios() {
        PersistentTaskPlanRecord plan = planRepository.save(
                new PersistentTaskPlanRecord(planRepository.findById(planId).orElseThrow().getProjectId(),
                        "空计划", null, "owner"));
        MethodSectionResponse res = methodSectionService.getPlanMethod(plan.getId());
        assertThat(res.scenarios()).isEmpty();
    }

    private PersistentAggregateReportRecord aggregate(long executionId, String summaryJson) {
        return new PersistentAggregateReportRecord(
                executionId,
                "final",
                System.currentTimeMillis() - 60_000,
                System.currentTimeMillis(),
                60.0,
                summaryJson,
                "[]",
                null,
                java.time.Instant.now(),
                "test-builder"
        );
    }
}
