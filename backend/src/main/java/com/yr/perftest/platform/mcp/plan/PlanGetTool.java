package com.yr.perftest.platform.mcp.plan;

import com.yr.perftest.platform.identity.Principal;
import com.yr.perftest.platform.mcp.McpTool;
import com.yr.perftest.platform.task.TaskPlan;
import com.yr.perftest.platform.task.plandoc.PlanDocumentService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 计划工具：读取计划当前全文与阶段状态（spec §6）。正文即唯一数据源，不另设摘要提取层。
 */
@Component
public class PlanGetTool implements McpTool {
    private final PlanDocumentService documentService;

    public PlanGetTool(PlanDocumentService documentService) {
        this.documentService = documentService;
    }

    @Override
    public String name() {
        return "plan_get";
    }

    @Override
    public String title() {
        return "Get Test Plan";
    }

    @Override
    public String description() {
        return "Read a performance test plan's full markdown document with phase/status and revision, "
                + "for local display or continued editing.";
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
                        "planId", Map.of("type", "integer", "description", "plan id")
                ),
                "required", List.of("planId")
        );
    }

    @Override
    public String usageExample() {
        return """
                plan_get({ "planId": 42 })

                → { "planId": 42, "projectId": 1, "title": "电商核心链路容量验证",
                     "revision": 3, "phase": "DRAFT", "status": "DRAFT",
                     "markdown": "# 一、背景\\n…全文",
                     "scenarioCount": 2, "createdBy": "agent", "updatedAt": "2026-09-07T10:00:00Z" }""";
    }

    @Override
    public Object call(Map<String, Object> args, Principal principal) {
        long planId = PlanToolsSupport.requiredLong(args, "planId");
        TaskPlan plan = documentService.getDocument(planId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("planId", plan.id());
        payload.put("projectId", plan.projectId());
        payload.put("title", plan.name());
        payload.put("markdown", plan.body() == null ? "" : plan.body());
        payload.put("revision", plan.revision());
        payload.put("phase", plan.phase().name());
        payload.put("status", plan.status().name());
        payload.put("scenarioCount", plan.scenarioCount());
        payload.put("createdBy", plan.createdBy());
        payload.put("updatedAt", plan.updatedAt().toString());
        return payload;
    }
}
