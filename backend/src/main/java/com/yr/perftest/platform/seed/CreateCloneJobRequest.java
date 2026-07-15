package com.yr.perftest.platform.seed;

public record CreateCloneJobRequest(
        Long templateId,
        Long datasourceId,
        Integer cloneCount,
        String failurePolicy,
        String operator
) {
}
