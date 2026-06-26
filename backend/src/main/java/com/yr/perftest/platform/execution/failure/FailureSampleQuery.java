package com.yr.perftest.platform.execution.failure;

public record FailureSampleQuery(
        String label,
        String code,
        Boolean success
) {
}
