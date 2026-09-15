package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.PersistentTaskScenarioRecord;
import com.yr.perftest.platform.task.PersistentTaskScenarioRepository;
import com.yr.perftest.platform.task.TaskScenarioService;
import com.yr.perftest.platform.task.TestType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「测试方法」章节骨架同步：文档含 `## ` 标题以「测试方法」结尾的章节时，场景增删改维护
 * `### S{n} 名称 · 类型` 小节骨架，方法说明自由区（`**方法说明**：` 行到块尾）逐字保留；
 * 章节缺失时 body 原样。共享命名 H2：本项目仅按保存后的 planId 精确断言，不依赖库内其他数据。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-scenario-sync-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.h2.console.enabled=false"
})
@Transactional
class PlanScenarioDocSyncMethodSectionTest {

    private static final long PROJECT_ID = 903L;

    @Autowired
    private PlanScenarioDocSync docSync;
    @Autowired
    private PersistentTaskPlanRepository planRepository;
    @Autowired
    private PersistentTaskScenarioRepository scenarioRepository;
    @Autowired
    private TaskScenarioService scenarioService;

    private PersistentTaskPlanRecord planWithBody(String body) {
        PersistentTaskPlanRecord plan = new PersistentTaskPlanRecord(PROJECT_ID, "测试方法章节计划", null, "owner");
        plan.initializeBody(body);
        return plan;
    }

    private PersistentTaskScenarioRecord scenario(int sortOrder, String name, TestType testType) {
        PersistentTaskScenarioRecord scenario = new PersistentTaskScenarioRecord(PROJECT_ID, null, name, sortOrder);
        scenario.updateBusinessFields(null, testType);
        return scenario;
    }

    private long givenPlanWithBody(String body) {
        PersistentTaskPlanRecord plan = planRepository.save(
                new PersistentTaskPlanRecord(PROJECT_ID, "测试方法章节计划", null, "owner"));
        plan.initializeBody(body);
        return planRepository.save(plan).getId();
    }

    @Test
    void upsertsScenarioBlocksInsideTestMethodSection() {
        PersistentTaskPlanRecord plan = planWithBody("""
                ## 八、测试方法
                章节导语保留。
                ### S1 放款提交 · 容量测试
                **方法说明**：手写内容保留。
                ## 九、风险与预案
                后续章节不受影响。""");
        boolean synced = docSync.syncTestMethodSection(plan, List.of(scenario(0, "放款提交", TestType.BENCHMARK)));
        assertThat(synced).isTrue();
        assertThat(plan.getBody()).contains("### S1 放款提交 · BENCHMARK"); // 标题行按实体重写
        assertThat(plan.getBody()).doesNotContain("容量测试");
        assertThat(plan.getBody()).contains("**方法说明**：手写内容保留。");   // 方法说明自由区保留
        assertThat(plan.getBody()).contains("## 九、风险与预案");             // 后续章节不受影响
        assertThat(plan.getBody()).contains("后续章节不受影响。");
    }

    @Test
    void skipsWhenSectionAbsent() {
        PersistentTaskPlanRecord plan = planWithBody("## 一、背景\n内容");
        assertThat(docSync.syncTestMethodSection(plan, List.of())).isFalse();
        assertThat(plan.getBody()).isEqualTo("## 一、背景\n内容");
        assertThat(plan.getRevision()).isEqualTo(1); // 未触碰 body 不 bump revision
    }

    @Test
    void addsNewScenarioAndKeepsLead() {
        PersistentTaskPlanRecord plan = planWithBody("""
                ## 八、测试方法
                章节导语保留。
                ## 九、风险与预案""");
        docSync.syncTestMethodSection(plan, List.of(
                scenario(0, "放款提交", TestType.BENCHMARK),
                scenario(1, "还款扣款", TestType.STABILITY)));
        String body = plan.getBody();
        assertThat(body).contains("### S1 放款提交 · BENCHMARK");
        assertThat(body).contains("### S2 还款扣款 · STABILITY");
        assertThat(body).contains("**方法说明**：（自由编辑，实体同步不触碰此处）");
        assertThat(body).contains("> 执行记录与监控证据：见 Pretty 视图 / 报告");
        assertThat(body.indexOf("章节导语保留。")).isLessThan(body.indexOf("### S1")); // 导语仍在场景前
        assertThat(body.indexOf("### S1")).isLessThan(body.indexOf("### S2"));         // 顺序 = sortOrder
    }

    @Test
    void preservesFreeRegionUnmatchedBlockAndIsIdempotent() {
        PersistentTaskPlanRecord plan = planWithBody("""
                ## 八、测试方法
                导语。
                ### S1 放款提交 · BENCHMARK
                **方法说明**：手写内容保留。
                ### 手写小节
                与实体无关的小节保留。
                ## 九、风险与预案""");
        docSync.syncTestMethodSection(plan, List.of(scenario(0, "放款提交", TestType.BENCHMARK)));
        String once = plan.getBody();
        assertThat(once).contains("### 手写小节");
        assertThat(once).contains("与实体无关的小节保留。");
        assertThat(once).contains("**方法说明**：手写内容保留。");
        docSync.syncTestMethodSection(plan, List.of(scenario(0, "放款提交", TestType.BENCHMARK)));
        assertThat(plan.getBody()).isEqualTo(once); // 幂等：重复同步不改写
    }

    @Test
    void removesBlockForDeletedScenario() {
        long planId = givenPlanWithBody("""
                ## 八、测试方法
                导语。
                ### S1 放款提交 · BENCHMARK
                **方法说明**：手写内容保留。
                ### S2 还款扣款 · STABILITY
                **方法说明**：还款说明。
                ## 九、风险与预案""");
        docSync.onScenarioDeleted(planId, "还款扣款");
        String body = planRepository.findById(planId).orElseThrow().getBody();
        assertThat(body).doesNotContain("还款扣款");
        assertThat(body).contains("### S1 放款提交 · BENCHMARK");
        assertThat(body).contains("**方法说明**：手写内容保留。");
        assertThat(body).contains("## 九、风险与预案");
    }

    @Test
    void legacyScenarioDesignSyncDoesNotTouchTestMethodSection() {
        long planId = givenPlanWithBody("""
                ## 八、测试方法
                导语。
                ### S1 放款提交 · BENCHMARK
                **方法说明**：手写内容保留。
                ## 九、风险与预案""");
        scenarioRepository.save(new PersistentTaskScenarioRecord(planId, null, "放款提交", 0));
        docSync.syncPlanScenarios(planId);
        String body = planRepository.findById(planId).orElseThrow().getBody();
        assertThat(body).doesNotContain("八、场景设计");   // 序号容错不得把测试方法章吞并为场景设计
        assertThat(body).doesNotContain("**场景目的**");    // 骨架小节不被场景设计事实回写改写
        assertThat(body).doesNotContain("#### 执行记录");   // 执行记录引用式，不落测试方法章 body
        assertThat(body).contains("**方法说明**：手写内容保留。");
    }

    @Test
    void createScenarioSyncsSkeletonIntoTestMethodSection() {
        long planId = givenPlanWithBody("## 八、测试方法\n导语。\n## 九、风险与预案\n");
        scenarioService.createScenario(planId, null, "放款提交", "验证放款", TestType.BENCHMARK,
                null, null, null, null, null);
        String body = planRepository.findById(planId).orElseThrow().getBody();
        assertThat(body).contains("### S1 放款提交 · BENCHMARK");
        assertThat(body).doesNotContain("八、场景设计");
        assertThat(body.indexOf("导语。")).isLessThan(body.indexOf("### S1"));
    }
}
