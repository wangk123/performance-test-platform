package com.yr.perftest.platform.agent.contract;

import java.util.List;

public record ApiResponse<T>(
        String requestId,
        String schemaVersion,
        T data,
        ApiErrorBody error,
        List<String> warnings,
        Boolean truncated,
        String nextCursor
) {
    public static <T> ApiResponse<T> success(String requestId, String schemaVersion, T data) {
        return new ApiResponse<>(requestId, schemaVersion, data, null, List.of(), null, null);
    }

    public static <T> ApiResponse<T> error(String requestId, String schemaVersion, ApiErrorBody error) {
        return new ApiResponse<>(requestId, schemaVersion, null, error, List.of(), null, null);
    }
}
