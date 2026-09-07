package com.yr.perftest.platform.mcp;

import com.yr.perftest.platform.identity.Principal;

import java.util.Map;

/**
 * MCP 任务型工具契约（T12）：非 REST 机械 1:1，每个工具单一明确语义，
 * 按阶段（导航→设计→观察→诊断→验证）分组暴露。实现必须复用 Facade，不复制业务规则。
 */
public interface McpTool {
    String name();

    String title();

    String description();

    /** 阶段：PLAN / NAVIGATE / DESIGN / OBSERVE / DIAGNOSE / VERIFY / CAPTURE */
    String stage();

    /** 写操作工具需要非只读 scope 才能调用 */
    boolean requiresWriteScope();

    Map<String, Object> inputSchema();

    /** 使用示例（D18：说明文案与使用示例作为工具元数据随平台发布维护）；新工具必须提供，存量工具默认空串。 */
    default String usageExample() {
        return "";
    }

    Object call(Map<String, Object> args, Principal principal);
}
