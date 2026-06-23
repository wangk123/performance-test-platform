package com.yr.perftest.platform.execution;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.util.Map;

public record HttpDebugRequest(
        @NotBlank String method,
        @NotBlank String url,
        Map<String, String> headers,
        String body,
        @Positive int timeoutMs
) {
    public HttpDebugRequest {
        headers = headers == null ? Map.of() : headers;
        body = body == null ? "" : body;
        if (timeoutMs <= 0) {
            timeoutMs = 30000;
        }
    }
}
