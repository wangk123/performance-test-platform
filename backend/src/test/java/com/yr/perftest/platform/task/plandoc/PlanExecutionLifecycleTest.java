package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.execution.ExecutionStatus;
import com.yr.perftest.platform.project.PersistentProjectRecord;
import com.yr.perftest.platform.project.PersistentProjectRepository;
import com.yr.perftest.platform.task.ExecutionLifecycleEvent;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRecord;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRepository;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.PersistentTaskScenarioRecord;
import com.yr.perftest.platform.task.PersistentTaskScenarioRepository;
import com.yr.perftest.platform.task.TestType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-lifecycle-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PlanExecutionLifecycleTest {

    private static final String CONFIG_JSON =
            "{\"threads\":50,\"rampUp\":0,\"duration\":300,\"loops\":1,\"jmeterProperties\":{},\"mode\":\"DISTRIBUTED\",\"controllerNodeId\":1,\"workerNodeIds\":[1],\"monitorTargetIds\":[]}";

    @Autowired
    private PlanWorkflowService workflow;
    @Autowired
    private PlanExecutionLifecycleListener listener;
    @Autowired
    private PersistentTaskPlanRepository planRepository;
    @Autowired
    private PersistentTaskScenarioRepository scenarioRepository;
    @Autowired
    private PersistentScenarioExecutionRepository executionRepository;
    @Autowired
    private PersistentProjectRepository projectRepository;

    private long planId;
    private long scenarioId;

    @BeforeEach
    void setUp() {
        PersistentProjectRecord project = projectRepository.save(new PersistentProjectRecord("P1", "项目一", "", "owner"));
        PersistentTaskPlanRecord plan = planRepository.save(
                new PersistentTaskPlanRecord(project.getId(), "计划", null, "owner"));
        plan.updateBody("## 七、场景设计\n\n### S1 场景A · SINGLE_TXN\n\n**场景目的**：p\n\n"
                + "**场景设置**（由场景执行配置生成，勿手改）：\n\n| 用户数 | 持续时长 | 加载方式 | 退出方式 |\n|---|---|---|---|\n| 50 | 300 秒 | 同时加载 | 同时退出 |\n\n#### 执行记录\n");
        plan.forceState(PlanPhase.EXECUTION, PlanStatus.RUNNING);
        planId = planRepository.save(plan).getId();
        PersistentTaskScenarioRecord scenario = scenarioRepository.save(
                new PersistentTaskScenarioRecord(planId, 9L, "场景A", 0));
        scenario.updateBusinessFields("p", TestType.SINGLE_TXN);
        scenarioId = scenarioRepository.save(scenario).getId();
    }

    @Test
    void terminalEventBackfillsScenarioBlockAndMarksDone() {
        PersistentScenarioExecutionRecord execution = executionRepository.save(
                new PersistentScenarioExecutionRecord(scenarioId, CONFIG_JSON));
        execution.markRunning("result.jtl", "jmeter.log");
        execution.markSuccess(0);
        executionRepository.save(execution);

        listener.onExecutionTerminal(new ExecutionLifecycleEvent(execution.getId(), ExecutionStatus.SUCCESS));

        String body = planRepository.findById(planId).orElseThrow().getBody();
        assertThat(body).contains("<!-- backfill:execution:" + execution.getId() + " -->");
        assertThat(body).contains("· SUCCESS ·");
        assertThat(body).contains("50 并发");
        PersistentTaskPlanRecord plan = planRepository.findById(planId).orElseThrow();
        assertThat(plan.getStatus()).isEqualTo(PlanStatus.DONE);
        assertThat(plan.getPhase()).isEqualTo(PlanPhase.EXECUTION);
    }

    @Test
    void terminalEventIsIdempotent() {
        PersistentScenarioExecutionRecord execution = executionRepository.save(
                new PersistentScenarioExecutionRecord(scenarioId, CONFIG_JSON));
        execution.markRunning("result.jtl", "jmeter.log");
        execution.markSuccess(0);
        executionRepository.save(execution);
        listener.onExecutionTerminal(new ExecutionLifecycleEvent(execution.getId(), ExecutionStatus.SUCCESS));
        int revisionAfterFirst = planRepository.findById(planId).orElseThrow().getRevision();
        int markers = planRepository.findById(planId).orElseThrow().getBody()
                .split("<!-- backfill:execution:" + execution.getId() + " -->", -1).length - 1;
        listener.onExecutionTerminal(new ExecutionLifecycleEvent(execution.getId(), ExecutionStatus.SUCCESS));
        assertThat(markers).isEqualTo(1);
        assertThat(planRepository.findById(planId).orElseThrow().getRevision()).isEqualTo(revisionAfterFirst);
    }

    @Test
    void stillActiveStaysRunning() {
        PersistentScenarioExecutionRecord done = executionRepository.save(
                new PersistentScenarioExecutionRecord(scenarioId, CONFIG_JSON));
        done.markRunning("a.jtl", "a.log");
        done.markSuccess(0);
        executionRepository.save(done);
        PersistentScenarioExecutionRecord active = executionRepository.save(
                new PersistentScenarioExecutionRecord(scenarioId, CONFIG_JSON));
        active.markRunning("b.jtl", "b.log");
        executionRepository.save(active);

        listener.onExecutionTerminal(new ExecutionLifecycleEvent(done.getId(), ExecutionStatus.SUCCESS));
        assertThat(planRepository.findById(planId).orElseThrow().getStatus()).isEqualTo(PlanStatus.RUNNING);
    }
}
