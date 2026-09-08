package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.identity.SystemRole;
import com.yr.perftest.platform.mcp.plan.PlanUpdateTool;
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

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-acceptance-save-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PlanAcceptanceSaveValidationTest {

    private static final HumanPrincipal OWNER = new HumanPrincipal("owner", Set.of(SystemRole.PROJECT_MEMBER));

    @Autowired
    private PlanDocumentService documentService;
    @Autowired
    private PersistentTaskPlanRepository planRepository;
    @Autowired
    private PersistentProjectRepository projectRepository;
    @Autowired
    private PersistentProjectMemberRepository memberRepository;
    @Autowired
    private PlanUpdateTool planUpdateTool;

    private long planId;

    @BeforeEach
    void setUp() {
        PersistentProjectRecord project = projectRepository.save(new PersistentProjectRecord("P1", "项目一", "", "owner"));
        memberRepository.save(new PersistentProjectMemberRecord(project.getId(), "owner", ProjectRole.OWNER));
        PersistentTaskPlanRecord plan = planRepository.save(
                new PersistentTaskPlanRecord(project.getId(), "计划", null, "owner"));
        planId = plan.getId();
    }

    private String baseDoc(String metricTable) {
        return "## 二、测试目的与指标\n\n" + metricTable + "\n\n## 七、场景设计\n\n（空）\n";
    }

    @Test
    void validTableSavesAndParsesBackDeterministically() {
        String body = baseDoc("""
                | 对象 | 指标 | 目标值 | 口径 |
                |---|---|---|---|
                | 登录场景 | TPS | ≥ 200 | 5 分钟均值 |""");
        documentService.updateMarkdown(planId, 1, body, OWNER);
        var saved = planRepository.findById(planId).orElseThrow();
        assertThat(saved.getRevision()).isEqualTo(2);
        PlanAcceptanceParser.AcceptanceSection parsed = PlanAcceptanceParser.parse(saved.getBody());
        assertThat(parsed.present()).isTrue();
        assertThat(parsed.rows().get(0).objectName()).isEqualTo("登录场景");
    }

    @Test
    void missingSectionOrEmptyTableSavesAsNoMetricPath() {
        documentService.updateMarkdown(planId, 1, "## 一、背景\n\n纯摸底计划，无指标章节\n", OWNER);
        assertThat(planRepository.findById(planId).orElseThrow().getBody()).contains("纯摸底计划");
    }

    @Test
    void invalidHeaderRejectedAndDocumentUnchanged() {
        String original = planRepository.findById(planId).orElseThrow().getBody();
        String bad = baseDoc("""
                | 指标名 | 数值 |
                |---|---|
                | TPS | 200 |""");
        assertThatThrownBy(() -> documentService.updateMarkdown(planId, 1, bad, OWNER))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("表头缺少列");
        var unchanged = planRepository.findById(planId).orElseThrow();
        assertThat(unchanged.getBody()).isEqualTo(original);
        assertThat(unchanged.getRevision()).isEqualTo(1);
    }

    @Test
    void invalidTargetValueRejected() {
        String bad = baseDoc("""
                | 对象 | 指标 | 目标值 | 口径 |
                |---|---|---|---|
                | S | P95 | 慢 | 口径 |""");
        assertThatThrownBy(() -> documentService.updateMarkdown(planId, 1, bad, OWNER))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("非数字");
    }

    @Test
    void unrecognizedMetricTypeSavesAsOther() {
        String body = baseDoc("""
                | 对象 | 指标 | 目标值 | 口径 |
                |---|---|---|---|
                | S | GC 次数 | 少于 10 | 人工 |""");
        documentService.updateMarkdown(planId, 1, body, OWNER);
        PlanAcceptanceParser.AcceptanceSection parsed =
                PlanAcceptanceParser.parse(planRepository.findById(planId).orElseThrow().getBody());
        assertThat(parsed.rows().get(0).metricType()).isEqualTo(PlanAcceptanceParser.MetricType.OTHER);
    }

    @Test
    void mcpPlanUpdateGoesThroughSameValidation() {
        String bad = baseDoc("""
                | 对象 | 指标 | 目标值 |
                |---|---|---|
                | S |"""); // 单元格数不足（数据行 1 列 < 表头 3 列）
        Map<String, Object> args = Map.of("planId", (Number) planId, "markdown", bad, "baseRevision", (Number) 1);
        assertThatThrownBy(() -> planUpdateTool.call(args, null))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("单元格数不足");
        assertThat(planRepository.findById(planId).orElseThrow().getRevision()).isEqualTo(1);
    }
}
