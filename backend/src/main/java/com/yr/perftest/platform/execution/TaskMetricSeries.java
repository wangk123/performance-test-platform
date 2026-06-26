package com.yr.perftest.platform.execution;

import com.yr.perftest.platform.execution.aggregate.MetricTick;

import java.util.List;

public record TaskMetricSeries(
        List<MetricTick> ticks
) {
    public static TaskMetricSeries empty() {
        return new TaskMetricSeries(List.of());
    }
}
