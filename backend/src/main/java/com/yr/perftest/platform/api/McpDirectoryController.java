package com.yr.perftest.platform.api;

import com.yr.perftest.platform.mcp.McpTool;
import com.yr.perftest.platform.mcp.McpToolRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具目录端点（P0-2 ②）：直接映射内存 {@link McpToolRegistry}——单一事实源，
 * 工具上下线随服务重启自动生效，页面侧零配置。Web 页面展示全部注册工具
 * （写权限以徽标标注）；scope 过滤是 MCP 机器身份的调用期语义，不在此重复实现。
 */
@RestController
public class McpDirectoryController {
    /** 阶段固定规范序列（闭环时序，spec §4.1）：不随注册表去重，PLAN 为计划工具（P0-1 后）预留。 */
    static final List<String> STAGE_ORDER =
            List.of("PLAN", "NAVIGATE", "DESIGN", "OBSERVE", "DIAGNOSE", "VERIFY", "CAPTURE");

    private final McpToolRegistry registry;

    public McpDirectoryController(McpToolRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/api/mcp/tools")
    public Map<String, Object> directory() {
        List<Map<String, Object>> tools = new ArrayList<>();
        for (McpTool tool : registry.all().stream()
                .sorted(Comparator.comparingInt(item -> stageIndex(item.stage())))
                .toList()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", tool.name());
            item.put("title", tool.title());
            item.put("stage", tool.stage());
            item.put("requiresWriteScope", tool.requiresWriteScope());
            // 注册表 v1 无启停标志，全部 ENABLED；未来引入 enabled 后在此透传，页面自动跟随（spec §4.3）
            item.put("status", "ENABLED");
            item.put("description", tool.description());
            item.put("usageExample", tool.usageExample());
            item.put("inputSchema", tool.inputSchema());
            tools.add(item);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("server", Map.of(
                "name", "performance-test-platform",
                "endpoint", "/mcp",
                "toolCount", tools.size()
        ));
        body.put("stages", STAGE_ORDER);
        body.put("tools", tools);
        return body;
    }

    private int stageIndex(String stage) {
        int index = STAGE_ORDER.indexOf(stage);
        return index < 0 ? STAGE_ORDER.size() : index;
    }
}
