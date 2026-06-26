package com.yr.perftest.platform.execution.failure;

final class FailureSampleNormalizer {
    private static final String[] BODY_MARKERS = {
            "\nPOST data:\n",
            "\nPUT data:\n",
            "\nPATCH data:\n",
            "\nDELETE data:\n"
    };
    private static final String COOKIE_MARKER = "\n\nCookie Data:";

    private FailureSampleNormalizer() {
    }

    static String cleanRequestBody(String samplerData) {
        if (samplerData == null || samplerData.isBlank()) {
            return "";
        }
        String text = samplerData;
        int cookieIdx = text.lastIndexOf(COOKIE_MARKER);
        if (cookieIdx >= 0) {
            text = text.substring(0, cookieIdx);
        }
        for (String marker : BODY_MARKERS) {
            int idx = text.indexOf(marker);
            if (idx >= 0) {
                return text.substring(idx + marker.length()).strip();
            }
        }
        return "";
    }

    static String cleanResponseHeaders(String responseHeaders) {
        if (responseHeaders == null || responseHeaders.isBlank()) {
            return "";
        }
        if (responseHeaders.startsWith("HTTP/")) {
            int newlineIdx = responseHeaders.indexOf('\n');
            if (newlineIdx >= 0) {
                return responseHeaders.substring(newlineIdx + 1);
            }
        }
        return responseHeaders;
    }

    static String extractRequestLine(String samplerData, String fallbackUrl) {
        if (samplerData == null || samplerData.isBlank()) {
            return fallbackUrl == null ? "" : fallbackUrl;
        }
        int newlineIdx = samplerData.indexOf('\n');
        String firstLine = newlineIdx >= 0 ? samplerData.substring(0, newlineIdx) : samplerData;
        firstLine = firstLine.strip();
        if (firstLine.isEmpty()) {
            return fallbackUrl == null ? "" : fallbackUrl;
        }
        return firstLine;
    }
}
