package com.yr.perftest.platform.execution.distributed;

public record RemoteRunnerResult(
        boolean ok,
        int exitCode,
        String message,
        String log
) {
    public static RemoteRunnerResult failed(String message) {
        return new RemoteRunnerResult(false, -1, message, "");
    }
}
