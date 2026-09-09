package com.yr.perftest.platform.mcp.tools;

import com.yr.perftest.platform.facade.VerificationFacade;
import com.yr.perftest.platform.identity.Principal;
import com.yr.perftest.platform.mcp.McpTool;
import com.yr.perftest.platform.verification.ChangeType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 变更登记工具：登记代码/配置引用，供优化验证挂靠。
 */
@Component
public class RegisterChangeTool implements McpTool {
    private final VerificationFacade verificationFacade;

    public RegisterChangeTool(VerificationFacade verificationFacade) {
        this.verificationFacade = verificationFacade;
    }

    @Override
    public String name() {
        return "register_change";
    }

    @Override
    public String title() {
        return "登记变更";
    }

    @Override
    public String description() {
        return "在验证优化前登记代码或配置变更引用（如 commit hash）。";
    }

    @Override
    public String stage() {
        return "VERIFY";
    }

    @Override
    public boolean requiresWriteScope() {
        return true;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return AnalyzeExecutionTool.schema(
                "变更登记",
                Map.of(
                        "changeType", Map.of("type", "string", "description", "CODE 或 CONFIG"),
                        "changeRef", Map.of("type", "string", "description", "commit hash 或配置键"),
                        "description", Map.of("type", "string")
                ),
                List.of("changeType", "changeRef")
        );
    }

    @Override
    public Object call(Map<String, Object> args, Principal principal) {
        String changeType = args.get("changeType") == null ? null : String.valueOf(args.get("changeType"));
        String changeRef = args.get("changeRef") == null ? null : String.valueOf(args.get("changeRef"));
        String description = args.get("description") == null ? null : String.valueOf(args.get("description"));
        ChangeType type;
        try {
            type = changeType == null ? null : ChangeType.valueOf(changeType.toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("changeType must be CODE or CONFIG");
        }
        return verificationFacade.registerChange(type, changeRef, description);
    }
}
