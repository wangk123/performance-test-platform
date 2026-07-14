package com.yr.perftest.platform.llm;

import java.time.Instant;
import java.util.List;

public record LlmModel(
        long id,
        long providerId,
        String modelName,
        String displayName,
        List<LlmApiType> apiTypes,
        LlmApiType apiType,
        boolean enabled,
        boolean isDefault,
        Instant createdAt,
        Instant updatedAt
) {
}
