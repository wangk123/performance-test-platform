package com.yr.perftest.platform.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.agent.contract.AgentErrorCode;
import com.yr.perftest.platform.execution.ExecutionConflictException;
import com.yr.perftest.platform.execution.ExecutionValidationException;
import com.yr.perftest.platform.execution.IdempotencyConflictException;
import com.yr.perftest.platform.facade.DataSourceUnavailableException;
import com.yr.perftest.platform.identity.AuthenticationException;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP 工具响应装配（T12）：结果复用 T4 稳定错误码语义，错误映射到 isError 结果。
 */
public final class McpToolSupport {
    private McpToolSupport() {
    }

    public static McpSchema.CallToolResult ok(ObjectMapper objectMapper, Object payload) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("data", payload);
        return text(objectMapper, envelope, false);
    }

    public static McpSchema.CallToolResult error(ObjectMapper objectMapper, String code, String message) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("data", null);
        envelope.put("error", Map.of("code", code, "message", message));
        return text(objectMapper, envelope, true);
    }

    public static McpSchema.CallToolResult failure(ObjectMapper objectMapper, RuntimeException exception) {
        return error(objectMapper, codeOf(exception), messageOf(exception));
    }

    private static McpSchema.CallToolResult text(ObjectMapper objectMapper, Map<String, Object> envelope, boolean isError) {
        try {
            return McpSchema.CallToolResult.builder()
                    .addTextContent(objectMapper.writeValueAsString(envelope))
                    .isError(isError)
                    .build();
        } catch (JsonProcessingException exception) {
            return McpSchema.CallToolResult.builder()
                    .addTextContent("{\"error\":{\"code\":\"INTERNAL_ERROR\",\"message\":\"cannot serialize tool result\"}}")
                    .isError(true)
                    .build();
        }
    }

    private static String codeOf(RuntimeException exception) {
        if (exception instanceof AuthenticationException) {
            return AgentErrorCode.AUTHENTICATION_FAILED.name();
        }
        if (exception instanceof ExecutionValidationException) {
            String message = exception.getMessage() == null ? "" : exception.getMessage();
            return message.contains("does not exist")
                    ? AgentErrorCode.NOT_FOUND.name()
                    : AgentErrorCode.VALIDATION_FAILED.name();
        }
        if (exception instanceof ExecutionConflictException) {
            return AgentErrorCode.EXECUTION_CONFLICT.name();
        }
        if (exception instanceof IdempotencyConflictException) {
            return AgentErrorCode.IDEMPOTENCY_CONFLICT.name();
        }
        if (exception instanceof DataSourceUnavailableException) {
            return AgentErrorCode.DATA_SOURCE_UNAVAILABLE.name();
        }
        if (exception instanceof IllegalArgumentException) {
            return AgentErrorCode.VALIDATION_FAILED.name();
        }
        return AgentErrorCode.INTERNAL_ERROR.name();
    }

    private static String messageOf(RuntimeException exception) {
        return exception.getMessage() == null ? "internal error" : exception.getMessage();
    }
}
