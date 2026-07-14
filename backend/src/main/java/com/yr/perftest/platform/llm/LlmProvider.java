package com.yr.perftest.platform.llm;

import java.time.Instant;

public record LlmProvider(
        long id,
        String name,
        String baseUrl,
        String baseUrlAnthropic,
        boolean apiKeyConfigured,
        boolean enabled,
        boolean storeBodyDefault,
        Instant createdAt,
        Instant updatedAt
) {
}
