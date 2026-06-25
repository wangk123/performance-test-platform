package com.yr.perftest.platform.execution.distributed;

import com.yr.perftest.platform.monitoring.MonitorDeployStartResult;

import java.util.List;

public record RemoteRunnerResult(
        boolean ok,
        int exitCode,
        String message,
        String log,
        List<MonitorDeployStartResult> startResults
) {
    public RemoteRunnerResult(boolean ok, int exitCode, String message, String log) {
        this(ok, exitCode, message, log, List.of());
    }

    public static RemoteRunnerResult failed(String message) {
        return new RemoteRunnerResult(false, -1, message, "", List.of());
    }
}
