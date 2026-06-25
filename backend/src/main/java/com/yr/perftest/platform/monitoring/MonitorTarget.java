package com.yr.perftest.platform.monitoring;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record MonitorTarget(
        long id,
        long projectId,
        MonitorTargetType type,
        String name,
        String serviceName,
        String host,
        String sshUsername,
        Integer sshPort,
        String pluginDir,
        boolean sshPasswordConfigured,
        int port,
        String metricsPath,
        String env,
        Map<String, String> labels,
        List<MonitorItem> items,
        boolean enabled,
        MonitorTargetCheckStatus lastCheckStatus,
        String lastCheckMessage,
        Instant lastCheckedAt,
        Instant createdAt,
        Instant updatedAt
) {
    @JsonProperty
    public String address() {
        return host + ":" + port;
    }
}
