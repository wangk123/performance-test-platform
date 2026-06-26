package com.yr.perftest.platform.monitoring;

import java.util.List;

public record MonitorDeployCommand(
        String title,
        String command
) {
}
