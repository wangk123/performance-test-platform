package com.yr.perftest.platform.api;

public record PlanErrorBody(
        String code,
        String message,
        Integer currentRevision,
        String serverMarkdown,
        String phase,
        String status,
        java.util.List<String> allowedActions
) {
    public static PlanErrorBody of(String code, String message) {
        return new PlanErrorBody(code, message, null, null, null, null, null);
    }
}
