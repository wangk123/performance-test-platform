package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.PersistentTaskScenarioRecord;
import com.yr.perftest.platform.task.PersistentTaskScenarioRepository;
import com.yr.perftest.platform.task.ScenarioThreadGroupConfig;
import com.yr.perftest.platform.task.ScenarioThreadGroupConfigSupport;
import com.yr.perftest.platform.task.TestType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-scenario-sync-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PlanScenarioDocSyncTest {

    @Autowired
    private PlanScenarioDocSync docSync;
    @Autowired
    private PersistentTaskPlanRepository planRepository;
    @Autowired
    private PersistentTaskScenarioRepository scenarioRepository;
    @Autowired
    private ScenarioThreadGroupConfigSupport configSupport;

    private long planId;

    @BeforeEach
    void setUp() {
        PersistentTaskPlanRecord plan = planRepository.save(new PersistentTaskPlanRecord(1L, "计划", null, "owner"));
        plan.updateBody("## 七、场景设计\n\n### S1 登录 · BENCHMARK\n\n**场景目的**：旧目的\n\n"
                + "**测试方法**：自由文本保留验证\n\n**场景设置**（由场景执行配置生成，勿手改）：\n\n"
                + "| 用户数 | 持续时长 | 加载方式 | 退出方式 |\n|---|---|---|---|\n| 10 | 60 秒 | 同时加载 | 同时退出 |\n\n#### 执行记录\n");
        planId = planRepository.save(plan).getId();
    }

    private PersistentTaskScenarioRecord addScenario(String name, int sortOrder, int threads, int rampUp, int duration) {
        PersistentTaskScenarioRecord scenario = scenarioRepository.save(
                new PersistentTaskScenarioRecord(planId, null, name, sortOrder));
        scenario.updateBusinessFields("验证" + name, TestType.SINGLE_TXN);
        scenario.updateProfile(name, null, "{}", null, null, null, configSupport.writeStored(List.of(
                new ScenarioThreadGroupConfig(1, "tg-1", "并发档位", threads, rampUp, duration, 0, null))));
        scenarioRepository.save(scenario);
        return scenario;
    }

    @Test
    void syncWritesScenarioFactsAndKeepsFreeTextAndRecords() {
        addScenario("登录", 0, 50, 30, 300);
        docSync.syncPlanScenarios(planId);
        String body = planRepository.findById(planId).orElseThrow().getBody();
        assertThat(body).contains("### S1 登录 · SINGLE_TXN");           // 标题/类型更新
        assertThat(body).contains("**场景目的**：验证登录");               // 目的更新
        assertThat(body).contains("| 50 | 300 秒 | 匀速加载 30 秒 | 同时退出 |"); // 设置表更新
        assertThat(body).contains("**测试方法**：自由文本保留验证");        // 自由文本保留
        assertThat(body).doesNotContain("旧目的");
    }

    @Test
    void syncAppendsBlockForNewScenario() {
        addScenario("转账", 0, 30, 0, 1800);
        docSync.syncPlanScenarios(planId);
        String body = planRepository.findById(planId).orElseThrow().getBody();
        assertThat(body).contains("### S1 转账 · SINGLE_TXN");
        assertThat(body).contains("| 30 | 1800 秒 | 同时加载 | 同时退出 |");
        assertThat(body).contains("#### 执行记录");
    }

    @Test
    void deletedScenarioBlockRemoved() {
        addScenario("查询", 0, 10, 0, 60);
        docSync.syncPlanScenarios(planId);
        docSync.onScenarioDeleted(planId, "查询");
        String body = planRepository.findById(planId).orElseThrow().getBody();
        assertThat(body).doesNotContain("查询");
    }

    @Test
    void renamedScenarioReplacesOldBlock() {
        PersistentTaskScenarioRecord scenario = addScenario("登录", 0, 20, 0, 120);
        docSync.syncPlanScenarios(planId); // 建块：S1 登录
        scenarioRepository.save(scenario);
        // 模拟改名流程：service 层先 remove 旧名再 sync（updateScenario 内实现）
        docSync.onScenarioDeleted(planId, "登录");
        PersistentTaskScenarioRecord renamed = scenarioRepository.findById(scenario.getId()).orElseThrow();
        renamed.updateProfile("新登录", null, "{}", null, null, null, null);
        scenarioRepository.save(renamed);
        docSync.syncPlanScenarios(planId);
        String body = planRepository.findById(planId).orElseThrow().getBody();
        assertThat(body).doesNotContain("### S1 登录");
        assertThat(body).contains("S1 新登录");
    }
}
