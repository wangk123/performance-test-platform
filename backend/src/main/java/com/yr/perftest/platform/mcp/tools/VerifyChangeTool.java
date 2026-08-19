package com.yr.perftest.platform.mcp.tools;

import com.yr.perftest.platform.facade.VerificationFacade;
import com.yr.perftest.platform.identity.Principal;
import com.yr.perftest.platform.mcp.McpTool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 优化验证工具：基线 vs 候选三态结论（IMPROVED / REGRESSED / INCONCLUSIVE），带护栏判定。
 */
@Component
public class VerifyChangeTool implements McpTool {
    private final VerificationFacade verificationFacade;

    public VerifyChangeTool(VerificationFacade verificationFacade) {
        this.verificationFacade = verificationFacade;
    }

    @Override
    public String name() {
        return "verify_change";
    }

    @Override
    public String title() {
        return "Verify Change";
    }

    @Override
    public String description() {
        return "Verify an optimization: compare baseline vs candidate executions registered against a change "
                + "record, output IMPROVED / REGRESSED / INCONCLUSIVE with guardrail checks.";
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
                "verification",
                Map.of(
                        "baselineExecutionId", Map.of("type", "integer"),
                        "candidateExecutionId", Map.of("type", "integer"),
                        "changeRecordId", Map.of("type", "integer")
                ),
                List.of("baselineExecutionId", "candidateExecutionId", "changeRecordId")
        );
    }

    @Override
    public Object call(Map<String, Object> args, Principal principal) {
        long baselineExecutionId = InspectExecutionTool.requiredLong(args, "baselineExecutionId");
        long candidateExecutionId = InspectExecutionTool.requiredLong(args, "candidateExecutionId");
        long changeRecordId = InspectExecutionTool.requiredLong(args, "changeRecordId");
        return verificationFacade.verify(baselineExecutionId, candidateExecutionId, changeRecordId);
    }
}
