package com.yr.perftest.platform.mcp.tools;

import com.yr.perftest.platform.facade.AnalysisFacade;
import com.yr.perftest.platform.identity.Principal;
import com.yr.perftest.platform.mcp.McpTool;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 性能诊断工具：确定性分析（趋势/异常/错误聚类/资源饱和），只产出事实不带根因结论。
 */
@Component
public class AnalyzeExecutionTool implements McpTool {
    private final AnalysisFacade analysisFacade;

    public AnalyzeExecutionTool(AnalysisFacade analysisFacade) {
        this.analysisFacade = analysisFacade;
    }

    @Override
    public String name() {
        return "analyze_execution";
    }

    @Override
    public String title() {
        return "Analyze Execution";
    }

    @Override
    public String description() {
        return "Run deterministic analysis on an execution: trend, anomaly intervals, error clustering and "
                + "resource saturation. Facts only, no root-cause claims.";
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
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "executionId", Map.of("type", "integer"),
                        "from", Map.of("type", "string", "description", "ISO-8601 start time"),
                        "to", Map.of("type", "string", "description", "ISO-8601 end time"),
                        "kinds", Map.of("type", "array",
                                "items", Map.of("type", "string"),
                                "description", "trend / anomaly / error-cluster / resource-saturation")
                ),
                "required", List.of("executionId")
        );
    }

    @Override
    public Object call(Map<String, Object> args, Principal principal) {
        long executionId = InspectExecutionTool.requiredLong(args, "executionId");
        Instant from = optionalInstant(args, "from");
        Instant to = optionalInstant(args, "to");
        List<String> kinds = optionalStrings(args, "kinds");
        return analysisFacade.getExecutionAnalysis(executionId, from, to, kinds, null);
    }

    static Instant optionalInstant(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Instant.parse(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    static List<String> optionalStrings(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (!(value instanceof List<?> list)) {
            return null;
        }
        List<String> strings = new ArrayList<>();
        for (Object item : list) {
            strings.add(String.valueOf(item));
        }
        return strings;
    }

    static Map<String, Object> schema(String description, Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("description", description);
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }
}
