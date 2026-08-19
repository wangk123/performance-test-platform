package com.yr.perftest.platform.mcp.tools;

import com.yr.perftest.platform.facade.EvidenceFacade;
import com.yr.perftest.platform.identity.Principal;
import com.yr.perftest.platform.mcp.McpTool;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 证据链下钻工具：按 executionId/时间窗/traceId 收集基础与深度证据摘要，每源显式声明可用性。
 */
@Component
public class CollectEvidenceTool implements McpTool {
    private final EvidenceFacade evidenceFacade;

    public CollectEvidenceTool(EvidenceFacade evidenceFacade) {
        this.evidenceFacade = evidenceFacade;
    }

    @Override
    public String name() {
        return "collect_evidence";
    }

    @Override
    public String title() {
        return "Collect Evidence";
    }

    @Override
    public String description() {
        return "Drill down an execution's evidence chain (base sources plus deep sources like db-metrics, "
                + "trace, app-log) by time window and optional traceId. Every source declares availability.";
    }

    @Override
    public String stage() {
        return "DIAGNOSE";
    }

    @Override
    public boolean requiresWriteScope() {
        return false;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return AnalyzeExecutionTool.schema(
                "evidence drill-down",
                Map.of(
                        "executionId", Map.of("type", "integer"),
                        "from", Map.of("type", "string"),
                        "to", Map.of("type", "string"),
                        "traceId", Map.of("type", "string"),
                        "kinds", Map.of("type", "array", "items", Map.of("type", "string"))
                ),
                List.of("executionId")
        );
    }

    @Override
    public Object call(Map<String, Object> args, Principal principal) {
        long executionId = InspectExecutionTool.requiredLong(args, "executionId");
        Instant from = AnalyzeExecutionTool.optionalInstant(args, "from");
        Instant to = AnalyzeExecutionTool.optionalInstant(args, "to");
        String traceId = args.get("traceId") == null ? null : String.valueOf(args.get("traceId"));
        return evidenceFacade.collect(executionId, from, to, null, null, traceId,
                AnalyzeExecutionTool.optionalStrings(args, "kinds"));
    }
}
