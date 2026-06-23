package com.yr.perftest.platform.execution;

import java.util.Map;

public record HttpDebugResult(
        boolean ok,
        String url,
        String method,
        Integer status,
        String statusText,
        long durationMs,
        Map<String, String> requestHeaders,
        Map<String, String> responseHeaders,
        String requestBody,
        String responseBody,
        String error
) {
}
