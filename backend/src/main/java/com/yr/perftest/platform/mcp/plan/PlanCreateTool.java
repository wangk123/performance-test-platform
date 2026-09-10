package com.yr.perftest.platform.mcp.plan;

import com.yr.perftest.platform.identity.Principal;
import com.yr.perftest.platform.mcp.McpTool;
import com.yr.perftest.platform.task.TaskPlan;
import com.yr.perftest.platform.task.TaskPlanService;
import com.yr.perftest.platform.task.plandoc.PlanValidationException;
import com.yr.perftest.platform.task.plandoc.PlanWorkflowService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 计划工具：用本地渲染的 Markdown 全文创建草稿计划（spec §6）。createdBy 记 "agent"，
 * 人工接手靠项目 OWNER / 系统 ADMIN 权限（spec §6.1）。
 */
@Component
public class PlanCreateTool implements McpTool {
    private final TaskPlanService taskPlanService;
    private final PlanWorkflowService workflowService;

    public PlanCreateTool(TaskPlanService taskPlanService, PlanWorkflowService workflowService) {
        this.taskPlanService = taskPlanService;
        this.workflowService = workflowService;
    }

    @Override
    public String name() {
        return "plan_create";
    }

    @Override
    public String title() {
        return "创建测试计划";
    }

    @Override
    public String description() {
        return "创建性能测试计划草稿：传入本地渲染的完整 Markdown 作为初始文档（revision 保持为 1）；"
                + "未传 markdown 时由对该项目可见的模板渲染生成。评审与阶段流转在平台内完成，不经 MCP。";
    }

    @Override
    public String stage() {
        return "PLAN";
    }

    @Override
    public boolean requiresWriteScope() {
        return true;
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("projectId", Map.of("type", "integer", "description", "所属项目 id"));
        properties.put("title", Map.of("type", "string", "description", "计划标题，如：电商核心链路容量验证"));
        properties.put("markdown", Map.of("type", "string",
                "description", "本地渲染的完整计划 Markdown（建议 12 个规范章节）"));
        properties.put("templateId", Map.of("type", "integer",
                "description", "markdown 缺省时使用的模板 id（须对该项目可见）"));
        properties.put("remark", Map.of("type", "string", "description", "可选备注"));
        return Map.of(
                "type", "object",
                "properties", properties,
                "required", List.of("projectId", "title")
        );
    }

    @Override
    public String usageExample() {
        return """
                # 用本地渲染的全文创建草稿计划
                plan_create({
                  "projectId": 1,
                  "title": "电商核心链路容量验证",
                  "markdown": "# 一、背景\\n…（12 章节全文）"
                })

                → { "planId": 42, "revision": 1, "phase": "DRAFT", "status": "DRAFT",
                     "title": "电商核心链路容量验证" }

                # markdown 缺省时由模板渲染：templateId 省略则用内置模板""";
    }

    @Override
    public Object call(Map<String, Object> args, Principal principal) {
        long projectId = PlanToolsSupport.requiredLong(args, "projectId");
        String title = PlanToolsSupport.requiredString(args, "title");
        String markdown = PlanToolsSupport.optionalString(args, "markdown");
        Long templateId = PlanToolsSupport.optionalLong(args, "templateId");
        String remark = PlanToolsSupport.optionalString(args, "remark");
        if (templateId != null) {
            requireTemplateVisible(projectId, templateId);
        }
        TaskPlan plan = taskPlanService.createPlan(projectId, title, remark, null, null, null,
                PlanToolsSupport.AGENT_USERNAME, templateId, markdown);
        return Map.of(
                "planId", plan.id(),
                "revision", plan.revision(),
                "phase", plan.phase().name(),
                "status", plan.status().name(),
                "title", plan.name()
        );
    }

    /** 模板可见性校验：非内置且非本项目 → PLAN_INVALID（服务端 renderInitialBody 会静默置空，须前置拦截）。 */
    private void requireTemplateVisible(long projectId, Long templateId) {
        boolean visible = workflowService.listTemplates(projectId).stream()
                .anyMatch(template -> template.getId().equals(templateId));
        if (!visible) {
            throw new PlanValidationException(
                    "PLAN_INVALID：模板 " + templateId + " 不在项目 " + projectId + " 可见模板清单内");
        }
    }
}
