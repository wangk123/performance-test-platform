package com.yr.perftest.platform.llm;

import java.time.Instant;

public record LlmCallRecord(
        long id,
        Long providerId,
        Long modelId,
        String providerNameSnapshot,
        String modelNameSnapshot,
        LlmApiType apiType,
        LlmCallScene scene,
        LlmCallStatus status,
        Long latencyMs,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        String errorMessage,
        String requestBody,
        String responseBody,
        String triggeredBy,
        Instant createdAt
) {
}
