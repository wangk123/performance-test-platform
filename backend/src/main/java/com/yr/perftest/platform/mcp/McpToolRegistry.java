package com.yr.perftest.platform.mcp;

import com.yr.perftest.platform.identity.MachinePrincipal;
import com.yr.perftest.platform.identity.Principal;
import io.modelcontextprotocol.common.McpTransportContext;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MCP 工具注册表（T12）：按主体能力/scope 分批暴露——只读 scope 的主体不可见写工具。
 */
@Component
public class McpToolRegistry {
    public static final String CONTEXT_KEY_PRINCIPAL = "principal";

    private final List<McpTool> tools;

    public McpToolRegistry(List<McpTool> tools) {
        this.tools = List.copyOf(tools);
    }

    public List<McpTool> all() {
        return tools;
    }

    public boolean visible(McpTool tool, Principal principal) {
        if (!tool.requiresWriteScope()) {
            return true;
        }
        if (principal instanceof MachinePrincipal machine
                && "readonly".equalsIgnoreCase(machine.scope())) {
            return false;
        }
        return true;
    }

    public Principal principalFrom(McpTransportContext context) {
        if (context == null) {
            return null;
        }
        Object value = context.get(CONTEXT_KEY_PRINCIPAL);
        return value instanceof Principal principal ? principal : null;
    }
}
