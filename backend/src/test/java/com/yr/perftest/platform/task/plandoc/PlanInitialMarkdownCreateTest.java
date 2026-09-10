package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.project.PersistentProjectRecord;
import com.yr.perftest.platform.project.PersistentProjectRepository;
import com.yr.perftest.platform.task.TaskPlan;
import com.yr.perftest.platform.task.TaskPlanService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * createPlan 9 参重载（P0-2①）：initialMarkdown 作初始正文，revision=1 不加版（spec §6.1）。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-initial-markdown-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PlanInitialMarkdownCreateTest {
    @Autowired
    private TaskPlanService taskPlanService;

    @Autowired
    private PersistentProjectRepository projectRepository;

    @Test
    void initialMarkdownSeedsBodyAtRevisionOne() {
        long projectId = projectRepository.save(new PersistentProjectRecord("P1", "项目一", "", "owner")).getId();
        TaskPlan plan = taskPlanService.createPlan(projectId, "计划一", null, null, null, null,
                "agent", null, "# 一、背景\nagent 全文\n三、测试指标\n…");
        assertThat(plan.revision()).isEqualTo(1);
        assertThat(plan.body()).isEqualTo("# 一、背景\nagent 全文\n三、测试指标\n…");
        assertThat(plan.phase()).isEqualTo(PlanPhase.DRAFT);
        assertThat(plan.status()).isEqualTo(PlanStatus.DRAFT);
    }

    @Test
    void blankMarkdownFallsBackToTemplateRender() {
        long projectId = projectRepository.save(new PersistentProjectRecord("P1", "项目一", "", "owner")).getId();
        TaskPlan plan = taskPlanService.createPlan(projectId, "计划一", null, null, null, null,
                "agent", null, "  ");
        assertThat(plan.revision()).isEqualTo(1);
        assertThat(plan.body()).contains("一、背景"); // PlanTemplateSeeder 内置模板渲染
    }

    @Test
    void legacyEightArgOverloadStillRendersBuiltinTemplate() {
        long projectId = projectRepository.save(new PersistentProjectRecord("P1", "项目一", "", "owner")).getId();
        TaskPlan plan = taskPlanService.createPlan(projectId, "计划一", null, null, null, null, "owner", null);
        assertThat(plan.revision()).isEqualTo(1);
        assertThat(plan.body()).contains("一、背景");
    }
}
