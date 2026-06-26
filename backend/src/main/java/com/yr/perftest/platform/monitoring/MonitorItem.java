package com.yr.perftest.platform.monitoring;

import java.util.Map;

public record MonitorItem(
        String id,
        MonitorItemType type,
        String name,
        Integer port,
        String metricsPath,
        String serviceName,
        String processKeyword,
        String instanceName,
        String databaseName,
        Map<String, String> labels
) {
}
