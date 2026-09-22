package com.yr.perftest.platform.execution.failure;

public final class TraceIdHeaderExtractor {
    private TraceIdHeaderExtractor() {
    }

    public static String extract(String responseHeaders) {
        if (responseHeaders == null || responseHeaders.isBlank()) {
            return null;
        }
        String traceId = null;
        for (String line : responseHeaders.split("\r?\n")) {
            if (traceId != null) {
                break;
            }
            int separator = line.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            String name = line.substring(0, separator).trim().toLowerCase();
            String value = line.substring(separator + 1).trim();
            if ("sw8".equals(name)) {
                String[] segments = value.split("-");
                if (segments.length > 2 && !segments[2].isEmpty()) {
                    traceId = segments[2];
                }
            } else if ("x-trace-id".equals(name) && !value.isEmpty()) {
                traceId = value;
            }
        }
        return traceId;
    }
}
