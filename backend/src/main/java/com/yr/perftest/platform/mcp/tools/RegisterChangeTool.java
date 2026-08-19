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
        return "Register Change";
    }

    @Override
    public String description() {
        return "Register a code or config change reference (e.g. commit hash) before verifying an optimization.";
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
                "change record",
                Map.of(
                        "changeType", Map.of("type", "string", "description", "CODE or CONFIG"),
                        "changeRef", Map.of("type", "string", "description", "commit hash or config key"),
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
