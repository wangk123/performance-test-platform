package com.yr.perftest.platform.monitoring;

public record JvmInstanceSelectable(
        long targetId,
        String itemId,
        String serviceName,
        String host,
        String processKeyword
) {
}
