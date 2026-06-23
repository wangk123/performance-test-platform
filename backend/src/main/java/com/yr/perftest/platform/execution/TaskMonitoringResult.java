package com.yr.perftest.platform.execution;

import java.util.List;

public record TaskMonitoringResult(
        List<String> interfaces,
        List<Point> points
) {
    public static TaskMonitoringResult empty() {
        return new TaskMonitoringResult(List.of(), List.of());
    }

    public record Point(
            String time,
            String interfaceName,
            double tps,
            double avgRt,
            double p90,
            double p95
    ) {
    }
}
