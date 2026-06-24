package com.yr.perftest.platform.execution;

import java.util.List;

public record TaskSamplePage(
        int page,
        int pageSize,
        int total,
        List<TaskExecutionResult.Sample> samples
) {
}
