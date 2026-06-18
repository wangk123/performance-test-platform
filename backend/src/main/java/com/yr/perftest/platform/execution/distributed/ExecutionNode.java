package com.yr.perftest.platform.execution.distributed;

import java.time.Instant;

public record ExecutionNode(
        long id,
        String name,
        String host,
        int sshPort,
        String sshUsername,
        String sshKeyPath,
        ExecutionNodeRole role,
        ExecutionNodeStatus status,
        String remoteWorkDir,
        Instant lastCheckedAt,
        String lastMessage,
        Instant createdAt,
        Instant updatedAt
) {
}
