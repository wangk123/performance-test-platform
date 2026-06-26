package com.yr.perftest.platform.monitoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class PrometheusFileSdWriter {
    private final PersistentMonitorTargetRepository targetRepository;
    private final ObjectMapper objectMapper;
    private final Path fileSdPath;

    public PrometheusFileSdWriter(
            PersistentMonitorTargetRepository targetRepository,
            ObjectMapper objectMapper,
            @Value("${platform.monitoring.prometheus.file-sd-path:./deploy/monitoring/prometheus/file_sd/jmx-targets.json}") String fileSdPath
    ) {
        this.targetRepository = targetRepository;
        this.objectMapper = objectMapper;
        this.fileSdPath = Path.of(fileSdPath);
    }

    public void rewrite() {
        try {
            Files.createDirectories(fileSdPath.getParent());
            String content = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(
                    targetRepository.findAllByEnabledTrueOrderByIdAsc().stream()
                            .flatMap(target -> toDiscoveryTargets(target).stream())
                            .toList()
            );
            Files.writeString(fileSdPath, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception exception) {
            throw new MonitoringValidationException("prometheus target file write failed");
        }
    }

    private List<Map<String, Object>> toDiscoveryTargets(PersistentMonitorTargetRecord target) {
        List<Map<String, Object>> targets = new ArrayList<>();
        targets.add(toDiscoveryTarget(target, target.getPort(), target.getMetricsPath(), "SERVER_RESOURCE", Map.of()));
        for (MonitorItem item : MonitoringJson.readItems(objectMapper, target.getItemsJson())) {
            Map<String, String> itemLabels = new LinkedHashMap<>();
            itemLabels.put("item_id", item.id());
            itemLabels.put("item_name", item.name());
            putIfPresent(itemLabels, "service", item.serviceName());
            putIfPresent(itemLabels, "process_keyword", item.processKeyword());
            putIfPresent(itemLabels, "instance", item.instanceName());
            putIfPresent(itemLabels, "database", item.databaseName());
            itemLabels.putAll(item.labels());
            targets.add(toDiscoveryTarget(target, item.port(), item.metricsPath(), item.type().name(), itemLabels));
        }
        return targets;
    }

    private Map<String, Object> toDiscoveryTarget(PersistentMonitorTargetRecord target, int port, String metricsPath, String targetKind, Map<String, String> extraLabels) {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("__metrics_path__", metricsPath);
        labels.put("project_id", String.valueOf(target.getProjectId()));
        labels.put("target_id", String.valueOf(target.getId()));
        labels.put("server", target.getName());
        labels.put("env", target.getEnv());
        labels.put("target_type", target.getType().name());
        labels.put("target_kind", targetKind);
        labels.putAll(MonitoringJson.readLabels(objectMapper, target.getLabelsJson()));
        labels.putAll(extraLabels);
        return Map.of(
                "targets", List.of(target.getHost() + ":" + port),
                "labels", labels
        );
    }

    private void putIfPresent(Map<String, String> labels, String key, String value) {
        if (value != null && !value.isBlank()) {
            labels.put(key, value);
        }
    }
}
