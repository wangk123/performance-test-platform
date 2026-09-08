package com.yr.perftest.platform.mcp.plan;

import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.identity.MachinePrincipal;
import com.yr.perftest.platform.identity.Principal;
import com.yr.perftest.platform.identity.SystemRole;
import com.yr.perftest.platform.project.PersistentProjectRecord;
import com.yr.perftest.platform.project.PersistentProjectRepository;
import com.yr.perftest.platform.task.TaskPlan;
import com.yr.perftest.platform.task.TaskPlanService;
import com.yr.perftest.platform.task.plandoc.PlanRevisionConflictException;
import com.yr.perftest.platform.task.plandoc.PlanStateException;
import com.yr.perftest.platform.task.plandoc.PlanValidationException;
import com.yr.perftest.platform.task.plandoc.PlanWorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 计划工具直调集成测试（P0-2①）：契约字段、冲突/状态异常、过滤分页（spec §6）。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-tools-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PlanToolsTest {
    private static final Principal AGENT = new MachinePrincipal(1L, "ops");

    @Autowired
    private PlanTemplatesTool templatesTool;
    @Autowired
    private PlanCreateTool createTool;
    @Autowired
    private PlanGetTool getTool;
    @Autowired
    private PlanUpdateTool updateTool;
    @Autowired
    private PlanQueryTool queryTool;
    @Autowired
    private TaskPlanService taskPlanService;
    @Autowired
    private PlanWorkflowService workflowService;
    @Autowired
    private PersistentProjectRepository projectRepository;

    @Test
    void metadataFollowsContract() {
        for (var tool : List.of(templatesTool, createTool, getTool, updateTool, queryTool)) {
            assertThat(tool.stage()).isEqualTo("PLAN");
            assertThat(tool.usageExample()).isNotBlank();
            assertThat(tool.inputSchema()).containsKey("properties");
        }
        assertThat(createTool.requiresWriteScope()).isTrue();
        assertThat(updateTool.requiresWriteScope()).isTrue();
        assertThat(templatesTool.requiresWriteScope()).isFalse();
        assertThat(getTool.requiresWriteScope()).isFalse();
        assertThat(queryTool.requiresWriteScope()).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void templatesReturnDerivedFields() {
        long projectId = newProject();
        Map<String, Object> payload = (Map<String, Object>) templatesTool.call(Map.of("projectId", projectId), AGENT);
        List<Map<String, Object>> templates = (List<Map<String, Object>>) payload.get("templates");
        assertThat(templates).isNotEmpty();
        Map<String, Object> builtin = templates.get(0);
        assertThat(builtin.get("scope")).isEqualTo("BUILTIN");
        assertThat((List<String>) builtin.get("sections")).contains("一、背景", "七、场景设计");
        assertThat((List<String>) builtin.get("placeholders")).contains("{{planName}}");
        assertThat(builtin.get("name")).isEqualTo("通用压测计划");
        // 缺省 projectId → 仅内置模板（findAllVisible(null) 语义）
        Map<String, Object> builtinOnly = (Map<String, Object>) templatesTool.call(Map.of(), AGENT);
        assertThat((List<?>) builtinOnly.get("templates")).isNotEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void createSeedsMarkdownAndValidatesTemplate() {
        long projectId = newProject();
        Map<String, Object> created = (Map<String, Object>) createTool.call(Map.of(
                "projectId", projectId,
                "title", "电商容量验证",
                "markdown", "# 一、背景\n全文"), AGENT);
        assertThat(created.get("revision")).isEqualTo(1);
        assertThat(created.get("phase")).isEqualTo("DRAFT");
        assertThat(created.get("status")).isEqualTo("DRAFT");
        long planId = ((Number) created.get("planId")).longValue();

        // 不可见模板 → PLAN_INVALID（杜绝服务端静默空正文）
        long otherProject = projectRepository.save(new PersistentProjectRecord("P2", "项目二", "", "owner")).getId();
        Long otherTemplate = workflowService.createTemplate(otherProject,
                new HumanPrincipal("owner", java.util.Set.of(SystemRole.ADMIN)), "他项目模板", null, "# 模板").getId();
        assertThatThrownBy(() -> createTool.call(Map.of(
                "projectId", projectId, "title", "t", "templateId", otherTemplate), AGENT))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("PLAN_INVALID");
        // title 缺失 → 参数校验
        assertThatThrownBy(() -> createTool.call(Map.of("projectId", projectId), AGENT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(planId).isPositive();
    }

    @Test
    @SuppressWarnings("unchecked")
    void getRoundTripsFullMarkdown() {
        long projectId = newProject();
        long planId = createPlan(projectId);
        Map<String, Object> payload = (Map<String, Object>) getTool.call(Map.of("planId", planId), AGENT);
        assertThat(payload.get("planId")).isEqualTo(planId);
        assertThat((String) payload.get("markdown")).contains("一、背景");
        assertThat(payload.get("phase")).isEqualTo("DRAFT");
        assertThat(payload.get("createdBy")).isEqualTo("agent");
        assertThat(((Number) payload.get("revision")).intValue()).isEqualTo(1);
        // 不存在 → PLAN_INVALID
        assertThatThrownBy(() -> getTool.call(Map.of("planId", 9999L), AGENT))
                .isInstanceOf(PlanValidationException.class);
    }

    @Test
    void updateSucceedsThenConflictsAndStateGuards() {
        long projectId = newProject();
        long planId = createPlan(projectId);
        Map<String, Object> updated = (Map<String, Object>) updateTool.call(Map.of(
                "planId", planId, "markdown", "# 一、背景\nv2", "baseRevision", 1L), AGENT);
        assertThat(((Number) updated.get("revision")).intValue()).isEqualTo(2);

        // 过期 base → 冲突（负载由 Task 1 的 failure 通道携带）
        assertThatThrownBy(() -> updateTool.call(Map.of(
                "planId", planId, "markdown", "# 一、背景\nv3", "baseRevision", 1L), AGENT))
                .isInstanceOf(PlanRevisionConflictException.class);

        // 提交评审后 → PLAN_STATE（skill 停止改稿依据）
        workflowService.submit(planId, new HumanPrincipal("owner", java.util.Set.of(SystemRole.PROJECT_MEMBER)), null);
        assertThatThrownBy(() -> updateTool.call(Map.of(
                "planId", planId, "markdown", "# 一、背景\nv4", "baseRevision", 2L), AGENT))
                .isInstanceOf(PlanStateException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void queryFiltersAndPages() {
        long projectId = newProject();
        long planA = createPlan(projectId);
        createTool.call(Map.of("projectId", projectId, "title", "登录压测", "markdown", "# 一、背景\na"), AGENT);
        workflowService.submit(planA, new HumanPrincipal("owner", java.util.Set.of(SystemRole.PROJECT_MEMBER)), null);

        Map<String, Object> all = (Map<String, Object>) queryTool.call(Map.of("projectId", projectId), AGENT);
        assertThat(((Number) all.get("total")).intValue()).isEqualTo(2);

        Map<String, Object> reviewOnly = (Map<String, Object>) queryTool.call(
                Map.of("projectId", projectId, "phase", "review"), AGENT);
        assertThat(((Number) reviewOnly.get("total")).intValue()).isEqualTo(1);

        Map<String, Object> keyword = (Map<String, Object>) queryTool.call(
                Map.of("projectId", projectId, "keyword", "登录"), AGENT);
        assertThat(((Number) keyword.get("total")).intValue()).isEqualTo(1);

        Map<String, Object> paged = (Map<String, Object>) queryTool.call(
                Map.of("projectId", projectId, "page", 2, "pageSize", 1), AGENT);
        assertThat((List<?>) paged.get("plans")).hasSize(1);
        assertThat(paged.get("page")).isEqualTo(2);
    }

    private long newProject() {
        return projectRepository.save(new PersistentProjectRecord("P1", "项目一", "", "owner")).getId();
    }

    private long createPlan(long projectId) {
        Map<String, Object> created = (Map<String, Object>) createTool.call(Map.of(
                "projectId", projectId, "title", "电商容量验证", "markdown", "# 一、背景\n全文"), AGENT);
        return ((Number) created.get("planId")).longValue();
    }
}
