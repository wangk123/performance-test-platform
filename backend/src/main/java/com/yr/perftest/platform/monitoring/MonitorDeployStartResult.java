package com.yr.perftest.platform.monitoring;

public record MonitorDeployStartResult(
        String title,
        boolean success,
        String output
) {
}
