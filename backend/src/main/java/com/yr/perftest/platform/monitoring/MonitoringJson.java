package com.yr.perftest.platform.monitoring;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;

final class MonitoringJson {
    private MonitoringJson() {
    }

    static String writeLabels(ObjectMapper objectMapper, Map<String, String> labels) {
        try {
            return objectMapper.writeValueAsString(normalizeLabels(labels));
        } catch (Exception exception) {
            throw new MonitoringValidationException("monitor target labels are invalid");
        }
    }

    static Map<String, String> readLabels(ObjectMapper objectMapper, String labelsJson) {
        if (labelsJson == null || labelsJson.isBlank()) {
            return Map.of();
        }
        try {
            return normalizeLabels(objectMapper.readValue(labelsJson, new TypeReference<>() {
            }));
        } catch (Exception exception) {
            return Map.of();
        }
    }

    static String writeItems(ObjectMapper objectMapper, List<MonitorItem> items) {
        try {
            return objectMapper.writeValueAsString(normalizeItems(items));
        } catch (Exception exception) {
            throw new MonitoringValidationException("monitor items are invalid");
        }
    }

    static List<MonitorItem> readItems(ObjectMapper objectMapper, String itemsJson) {
        if (itemsJson == null || itemsJson.isBlank()) {
            return List.of();
        }
        try {
            return normalizeItems(objectMapper.readValue(itemsJson, new TypeReference<List<MonitorItem>>() {
            }));
        } catch (Exception exception) {
            return List.of();
        }
    }

    private static Map<String, String> normalizeLabels(Map<String, String> labels) {
        if (labels == null || labels.isEmpty()) {
            return Map.of();
        }
        Map<String, String> normalized = new TreeMap<>();
        labels.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                normalized.put(key.trim(), value.trim());
            }
        });
        return normalized;
    }

    private static List<MonitorItem> normalizeItems(List<MonitorItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream().map(item -> {
            if (item == null || item.type() == null) {
                throw new MonitoringValidationException("monitor item type is required");
            }
            String id = item.id() == null || item.id().isBlank() ? UUID.randomUUID().toString() : item.id().trim();
            String name = item.name() == null || item.name().isBlank() ? item.type().name() : item.name().trim();
            int port = item.port() == null ? 0 : item.port();
            if (port <= 0 || port > 65535) {
                throw new MonitoringValidationException("monitor item port is invalid");
            }
            String metricsPath = item.metricsPath() == null || item.metricsPath().isBlank() ? "/metrics" : item.metricsPath().trim();
            if (!metricsPath.startsWith("/")) {
                metricsPath = "/" + metricsPath;
            }
            String processKeyword = trim(item.processKeyword());
            if (item.type() == MonitorItemType.JAVA_JMX_AGENT && processKeyword == null) {
                throw new MonitoringValidationException("jvm process keyword is required");
            }
            return new MonitorItem(
                    id,
                    item.type(),
                    name,
                    port,
                    metricsPath,
                    trim(item.serviceName()),
                    processKeyword,
                    trim(item.instanceName()),
                    trim(item.databaseName()),
                    normalizeLabels(item.labels())
            );
        }).toList();
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
