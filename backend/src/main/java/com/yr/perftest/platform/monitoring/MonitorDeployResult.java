package com.yr.perftest.platform.monitoring;

import java.util.List;

public record MonitorDeployResult(
        boolean success,
        String message,
        String remoteDir,
        List<String> uploadedFiles,
        List<MonitorDeployStartResult> startResults,
        List<MonitorDeployCommand> agentCommands
) {
}
