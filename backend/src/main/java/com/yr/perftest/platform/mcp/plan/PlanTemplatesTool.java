package com.yr.perftest.platform.mcp.plan;

import com.yr.perftest.platform.identity.Principal;
import com.yr.perftest.platform.mcp.McpTool;
import com.yr.perftest.platform.task.plandoc.PlanMarkdownSupport;
import com.yr.perftest.platform.task.plandoc.PlanWorkflowService;
import com.yr.perftest.platform.task.plandoc.PersistentPlanTemplateRecord;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 计划工具：列出计划模板（内置 + 项目自定义），派生章节结构与占位符供本地渲染（spec §6）。
 */
@Component
public class PlanTemplatesTool implements McpTool {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([\\w.-]+)\\}\\}");

    private final PlanWorkflowService workflowService;

    public PlanTemplatesTool(PlanWorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @Override
    public String name() {
        return "plan_templates";
    }

    @Override
    public String title() {
        return "List Plan Templates";
    }

    @Override
    public String description() {
        return "List performance test plan templates (builtin plus project custom) with derived section "
                + "structure and placeholders, for rendering a full plan draft locally.";
    }

    @Override
    public String stage() {
        return "PLAN";
    }

    @Override
    public boolean requiresWriteScope() {
        return false;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "projectId", Map.of("type", "integer",
                                "description", "project id; omit to list builtin templates only")
                )
        );
    }

    @Override
    public String usageExample() {
        return """
                # 拉取可用计划模板（内置 + 项目自定义）
                plan_templates({ "projectId": 1 })

                → { "templates": [
                     { "id": 1, "name": "通用压测计划", "scope": "BUILTIN", "description": "…",
                       "sections": ["一、背景", "二、测试目的与指标", "…"],
                       "placeholders": ["{{planName}}"] } ] }""";
    }

    @Override
    public Object call(Map<String, Object> args, Principal principal) {
        Long projectId = PlanToolsSupport.optionalLong(args, "projectId");
        // projectId 缺省 → 0（非真实项目 id）：findAllVisible 的 "= :projectId" 无匹配，仅返回内置模板
        List<Map<String, Object>> templates = new ArrayList<>();
        for (PersistentPlanTemplateRecord record : workflowService.listTemplates(
                projectId == null ? 0L : projectId)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", record.getId());
            item.put("name", record.getName());
            item.put("scope", record.getProjectId() == null ? "BUILTIN" : "PROJECT");
            item.put("description", record.getDescription() == null ? "" : record.getDescription());
            item.put("sections", sectionsOf(record.getContent()));
            item.put("placeholders", placeholdersOf(record.getContent()));
            templates.add(item);
        }
        return Map.of("templates", templates);
    }

    private static List<String> sectionsOf(String content) {
        List<String> sections = new ArrayList<>();
        for (PlanMarkdownSupport.Section section : PlanMarkdownSupport.splitSections(content)) {
            sections.add(section.title());
        }
        return sections;
    }

    private static List<String> placeholdersOf(String content) {
        List<String> placeholders = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(content == null ? "" : content);
        while (matcher.find() && !placeholders.contains(matcher.group())) {
            placeholders.add(matcher.group());
        }
        return placeholders;
    }
}
