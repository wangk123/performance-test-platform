package com.yr.perftest.platform.mcp.tools;

import com.yr.perftest.platform.facade.DataFacade;
import com.yr.perftest.platform.facade.ExecutionFacade;
import com.yr.perftest.platform.identity.Principal;
import com.yr.perftest.platform.mcp.McpTool;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 执行观察工具：查看执行状态与结果摘要。
 */
@Component
public class InspectExecutionTool implements McpTool {
    private final ExecutionFacade executionFacade;
    private final DataFacade dataFacade;

    public InspectExecutionTool(ExecutionFacade executionFacade, DataFacade dataFacade) {
        this.executionFacade = executionFacade;
        this.dataFacade = dataFacade;
    }

    @Override
    public String name() {
        return "inspect_execution";
    }

    @Override
    public String title() {
        return "Inspect Execution";
    }

    @Override
    public String description() {
        return "Inspect a load test execution: status timeline and result summary (samples, throughput, avg/p95, error rate).";
    }

    @Override
    public String stage() {
        return "OBSERVE";
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
                        "executionId", Map.of("type", "integer", "description", "execution id")
                ),
                "required", java.util.List.of("executionId")
        );
    }

    @Override
    public Object call(Map<String, Object> args, Principal principal) {
        long executionId = requiredLong(args, "executionId");
        return Map.of(
                "status", executionFacade.getExecutionStatus(executionId),
                "summary", dataFacade.getExecutionSummary(executionId)
        );
    }

    static long requiredLong(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(key + " is required");
        }
        return number.longValue();
    }
}
