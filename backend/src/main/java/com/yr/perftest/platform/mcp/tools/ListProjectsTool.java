package com.yr.perftest.platform.mcp.tools;

import com.yr.perftest.platform.facade.ProjectFacade;
import com.yr.perftest.platform.identity.Principal;
import com.yr.perftest.platform.mcp.McpTool;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 导航工具：列出项目（默认不含归档）。
 */
@Component
public class ListProjectsTool implements McpTool {
    private final ProjectFacade projectFacade;

    public ListProjectsTool(ProjectFacade projectFacade) {
        this.projectFacade = projectFacade;
    }

    @Override
    public String name() {
        return "list_projects";
    }

    @Override
    public String title() {
        return "列出项目";
    }

    @Override
    public String description() {
        return "列出平台项目及其负责人与状态。导航可用测试资产时请先调用本工具。";
    }

    @Override
    public String stage() {
        return "NAVIGATE";
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
                        "includeArchived", Map.of("type", "boolean", "description", "是否包含已归档项目")
                )
        );
    }

    @Override
    public Object call(Map<String, Object> args, Principal principal) {
        boolean includeArchived = Boolean.TRUE.equals(args.get("includeArchived"));
        return Map.of("items", projectFacade.listProjects(includeArchived));
    }
}
