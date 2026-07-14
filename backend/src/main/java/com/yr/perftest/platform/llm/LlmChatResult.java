package com.yr.perftest.platform.llm;

public record LlmChatResult(
        String content,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        String rawRequest,
        String rawResponse
) {
}
