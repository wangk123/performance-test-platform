package com.yr.perftest.platform.execution.failure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FailureSampleRecord(
        long id,
        long ts,
        String label,
        String code,
        boolean success,
        long elapsed,
        String message,
        String threadName,
        String host,
        String url,
        String requestHeaders,
        String requestBody,
        String responseHeaders,
        String responseBody,
        String failureMessage
) {
}
