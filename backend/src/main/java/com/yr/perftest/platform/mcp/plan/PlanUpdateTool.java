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
 * 计划工具：乐观并发更新计划全文（spec §6）。冲突/状态异常由 McpToolSupport.failure
 * 映射为 PLAN_REVISION_CONFLICT / PLAN_STATE 并携带 details 负载。
 */
@Component
public class PlanUpdateTool implements McpTool {
    private final PlanDocumentService documentService;

    public PlanUpdateTool(PlanDocumentService documentService) {
        this.documentService = documentService;
    }

    @Override
    public String name() {
        return "plan_update";
    }

    @Override
    public String title() {
        return "Update Test Plan";
    }

    @Override
    public String description() {
        return "Update a plan's full markdown with optimistic concurrency: pass the revision from plan_get "
                + "as baseRevision. On conflict the error carries currentRevision and serverMarkdown in "
                + "details; resolve by one of keep-platform / adopt-local / merge-then-resubmit.";
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
        properties.put("planId", Map.of("type", "integer", "description", "plan id"));
        properties.put("markdown", Map.of("type", "string", "description", "updated full plan markdown"));
        properties.put("baseRevision", Map.of("type", "integer",
                "description", "revision the update is based on (from plan_get)"));
        return Map.of(
                "type", "object",
                "properties", properties,
                "required", List.of("planId", "markdown", "baseRevision")
        );
    }

    @Override
    public String usageExample() {
        return """
                # 成功：以 plan_get 返回的 revision 作 baseRevision
                plan_update({ "planId": 42, "markdown": "# 一、背景\\n…修改后全文", "baseRevision": 3 })
                → { "planId": 42, "revision": 4, "updatedAt": "…" }

                # 冲突：平台侧已被他人修改（HTTP 409 语义）
                plan_update({ "planId": 42, "markdown": "…", "baseRevision": 3 })
                → error: { "code": "PLAN_REVISION_CONFLICT",
                           "details": { "currentRevision": 5,
                                        "serverMarkdown": "# 一、背景\\n…平台当前全文" } }
                # 调用方 diff 两版后三选一：保留平台版 / 采纳本地版（以 5 为新 base 重放）/ 手改合并后重提""";
    }

    @Override
    public Object call(Map<String, Object> args, Principal principal) {
        long planId = PlanToolsSupport.requiredLong(args, "planId");
        String markdown = PlanToolsSupport.requiredString(args, "markdown");
        long baseRevision = PlanToolsSupport.requiredLong(args, "baseRevision");
        TaskPlan updated = documentService.updateMarkdown(planId, baseRevision, markdown,
                PlanToolsSupport.agentActor());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("planId", updated.id());
        payload.put("revision", updated.revision());
        payload.put("updatedAt", updated.updatedAt().toString());
        return payload;
    }
}
