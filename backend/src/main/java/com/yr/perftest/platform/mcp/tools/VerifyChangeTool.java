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
        return "验证变更";
    }

    @Override
    public String description() {
        return "验证优化效果：对比同一变更记录下登记的基线与候选执行，带护栏检查，"
                + "输出 IMPROVED / REGRESSED / INCONCLUSIVE。";
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
                "优化验证",
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
