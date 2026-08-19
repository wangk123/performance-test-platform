package com.yr.perftest.platform.mcp.tools;

import com.yr.perftest.platform.execution.RequestHashing;
import com.yr.perftest.platform.execution.ExecutionValidationException;
import com.yr.perftest.platform.facade.ExecutionFacade;
import com.yr.perftest.platform.facade.data.ExecutionPrecheckView;
import com.yr.perftest.platform.identity.Principal;
import com.yr.perftest.platform.mcp.McpTool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 压测执行工具（写）：预检 + 幂等启动。同一参数重复调用只启动一次；缺省幂等键由参数哈希生成。
 */
@Component
public class StartExecutionTool implements McpTool {
    private final ExecutionFacade executionFacade;

    public StartExecutionTool(ExecutionFacade executionFacade) {
        this.executionFacade = executionFacade;
    }

    @Override
    public String name() {
        return "start_execution";
    }

    @Override
    public String title() {
        return "Start Execution";
    }

    @Override
    public String description() {
        return "Precheck and start a load test execution for a scenario. Idempotent: repeated calls with the "
                + "same idempotencyKey (or identical parameters) start the execution only once.";
    }

    @Override
    public String stage() {
        return "DESIGN";
    }

    @Override
    public boolean requiresWriteScope() {
        return true;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return AnalyzeExecutionTool.schema(
                "start execution",
                Map.of(
                        "scenarioId", Map.of("type", "integer"),
                        "executionName", Map.of("type", "string"),
                        "threadGroupConfigId", Map.of("type", "integer"),
                        "threadGroupPresetSortOrder", Map.of("type", "integer"),
                        "idempotencyKey", Map.of("type", "string")
                ),
                List.of("scenarioId")
        );
    }

    @Override
    public Object call(Map<String, Object> args, Principal principal) {
        long scenarioId = InspectExecutionTool.requiredLong(args, "scenarioId");
        String executionName = args.get("executionName") == null ? null : String.valueOf(args.get("executionName"));
        Long threadGroupConfigId = optionalLong(args, "threadGroupConfigId");
        Integer presetSortOrder = optionalInt(args, "threadGroupPresetSortOrder");

        ExecutionPrecheckView precheck = executionFacade.precheckExecution(
                scenarioId, threadGroupConfigId, presetSortOrder);
        if (!precheck.valid()) {
            throw new ExecutionValidationException(
                    "precheck failed: " + String.join("; ", precheck.errors()));
        }

        String idempotencyKey = args.get("idempotencyKey") == null
                ? RequestHashing.sha256("start:" + scenarioId + "|" + executionName + "|"
                + threadGroupConfigId + "|" + presetSortOrder)
                : String.valueOf(args.get("idempotencyKey"));
        var started = executionFacade.startExecution(
                scenarioId, executionName, threadGroupConfigId, presetSortOrder, idempotencyKey);
        return Map.of("execution", started, "precheck", precheck);
    }

    private static Long optionalLong(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (!(value instanceof Number number)) {
            return null;
        }
        return number.longValue();
    }

    private static Integer optionalInt(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (!(value instanceof Number number)) {
            return null;
        }
        return number.intValue();
    }
}
