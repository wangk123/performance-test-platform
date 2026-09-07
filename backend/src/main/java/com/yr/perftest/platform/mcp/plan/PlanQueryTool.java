package com.yr.perftest.platform.mcp.plan;

import com.yr.perftest.platform.identity.Principal;
import com.yr.perftest.platform.mcp.McpTool;
import com.yr.perftest.platform.task.TaskPlan;
import com.yr.perftest.platform.task.TaskPlanService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 计划工具：按项目/阶段/关键词查询计划列表（spec §6）。过滤与分页在 listPlans 结果上内存完成。
 */
@Component
public class PlanQueryTool implements McpTool {
    private static final int MAX_PAGE_SIZE = 100;

    private final TaskPlanService taskPlanService;

    public PlanQueryTool(TaskPlanService taskPlanService) {
        this.taskPlanService = taskPlanService;
    }

    @Override
    public String name() {
        return "plan_query";
    }

    @Override
    public String title() {
        return "Query Test Plans";
    }

    @Override
    public String description() {
        return "Query performance test plans by project with optional phase filter (DRAFT/REVIEW/EXECUTION/"
                + "REPORT/PUBLISH), case-insensitive title keyword and in-memory paging.";
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
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("projectId", Map.of("type", "integer", "description", "project id"));
        properties.put("phase", Map.of("type", "string",
                "description", "phase filter: DRAFT / REVIEW / EXECUTION / REPORT / PUBLISH (case-insensitive)"));
        properties.put("keyword", Map.of("type", "string", "description", "title keyword, case-insensitive contains"));
        properties.put("page", Map.of("type", "integer", "description", "1-based page, default 1"));
        properties.put("pageSize", Map.of("type", "integer", "description", "page size 1-100, default 20"));
        return Map.of(
                "type", "object",
                "properties", properties,
                "required", List.of("projectId")
        );
    }

    @Override
    public String usageExample() {
        return """
                plan_query({ "projectId": 1, "phase": "REVIEW", "keyword": "容量", "page": 1, "pageSize": 20 })

                → { "plans": [ { "planId": 42, "title": "电商核心链路容量验证",
                                 "phase": "REVIEW", "status": "IN_REVIEW",
                                 "revision": 7, "updatedAt": "2026-09-07T10:00:00Z" } ],
                    "total": 3, "page": 1, "pageSize": 20 }""";
    }

    @Override
    public Object call(Map<String, Object> args, Principal principal) {
        long projectId = PlanToolsSupport.requiredLong(args, "projectId");
        String phase = PlanToolsSupport.optionalString(args, "phase");
        String keyword = PlanToolsSupport.optionalString(args, "keyword");
        int page = PlanToolsSupport.optionalInt(args, "page", 1);
        int pageSize = Math.min(PlanToolsSupport.optionalInt(args, "pageSize", 20), MAX_PAGE_SIZE);

        List<TaskPlan> matched = taskPlanService.listPlans(projectId).stream()
                .filter(plan -> phase == null || plan.phase().name().equalsIgnoreCase(phase))
                .filter(plan -> keyword == null || plan.name().toLowerCase(Locale.ROOT)
                        .contains(keyword.toLowerCase(Locale.ROOT)))
                .toList();
        int total = matched.size();
        int from = Math.min(Math.max(0, (page - 1) * pageSize), total);
        int to = Math.min(from + pageSize, total);
        List<Map<String, Object>> plans = new ArrayList<>();
        for (TaskPlan plan : matched.subList(from, to)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("planId", plan.id());
            item.put("title", plan.name());
            item.put("phase", plan.phase().name());
            item.put("status", plan.status().name());
            item.put("revision", plan.revision());
            item.put("updatedAt", plan.updatedAt().toString());
            plans.add(item);
        }
        return Map.of("plans", plans, "total", total, "page", page, "pageSize", pageSize);
    }
}
