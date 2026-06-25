package com.yr.perftest.platform.monitoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.project.PersistentProjectRepository;
import com.yr.perftest.platform.project.ProjectValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class MonitorTargetService {
    private final PersistentProjectRepository projectRepository;
    private final PersistentMonitorTargetRepository targetRepository;
    private final PrometheusFileSdWriter fileSdWriter;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public MonitorTargetService(
            PersistentProjectRepository projectRepository,
            PersistentMonitorTargetRepository targetRepository,
            PrometheusFileSdWriter fileSdWriter,
            ObjectMapper objectMapper
    ) {
        this.projectRepository = projectRepository;
        this.targetRepository = targetRepository;
        this.fileSdWriter = fileSdWriter;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }

    @Transactional(readOnly = true)
    public List<MonitorTarget> listTargets(long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ProjectValidationException("project does not exist");
        }
        return targetRepository.findAllByProjectIdOrderByIdDesc(projectId).stream().map(this::toTarget).toList();
    }

    @Transactional
    public MonitorTarget createTarget(long projectId, MonitorTargetInput input) {
        if (!projectRepository.existsById(projectId)) {
            throw new ProjectValidationException("project does not exist");
        }
        MonitorTargetInput normalized = normalize(input);
        PersistentMonitorTargetRecord record = targetRepository.save(new PersistentMonitorTargetRecord(
                projectId,
                normalized.name(),
                normalized.serviceName(),
                normalized.host(),
                normalized.sshUsername(),
                normalized.sshPassword(),
                normalized.sshPort(),
                normalized.pluginDir(),
                normalized.port(),
                normalized.metricsPath(),
                normalized.env(),
                MonitoringJson.writeLabels(objectMapper, normalized.labels()),
                MonitoringJson.writeItems(objectMapper, normalized.items()),
                normalized.enabled()
        ));
        fileSdWriter.rewrite();
        return toTarget(record);
    }

    @Transactional
    public MonitorTarget updateTarget(long targetId, MonitorTargetInput input) {
        PersistentMonitorTargetRecord record = targetRepository.findById(targetId)
                .orElseThrow(() -> new MonitoringValidationException("monitor target does not exist"));
        MonitorTargetInput normalized = normalizeForUpdate(record, normalize(input));
        record.update(
                normalized.name(),
                normalized.serviceName(),
                normalized.host(),
                normalized.sshUsername(),
                normalized.sshPassword(),
                normalized.sshPort(),
                normalized.pluginDir(),
                normalized.port(),
                normalized.metricsPath(),
                normalized.env(),
                MonitoringJson.writeLabels(objectMapper, normalized.labels()),
                MonitoringJson.writeItems(objectMapper, normalized.items()),
                normalized.enabled()
        );
        fileSdWriter.rewrite();
        return toTarget(record);
    }

    @Transactional
    public MonitorTarget checkTarget(long targetId) {
        PersistentMonitorTargetRecord record = targetRepository.findById(targetId)
                .orElseThrow(() -> new MonitoringValidationException("monitor target does not exist"));
        try {
            checkEndpoint(record.getHost(), record.getPort(), record.getMetricsPath());
            for (MonitorItem item : MonitoringJson.readItems(objectMapper, record.getItemsJson())) {
                checkEndpoint(record.getHost(), item.port(), item.metricsPath());
            }
            record.markCheck(MonitorTargetCheckStatus.SUCCESS, "metrics endpoints ready");
        } catch (Exception exception) {
            record.markCheck(MonitorTargetCheckStatus.FAILED, normalizeMessage(exception.getMessage()));
        }
        return toTarget(record);
    }

    private void checkEndpoint(String host, int port, String metricsPath) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + host + ":" + port + metricsPath))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300 || !response.body().contains("#")) {
            throw new MonitoringValidationException("metrics endpoint returned " + response.statusCode());
        }
    }

    @Transactional
    public void deleteTarget(long targetId) {
        if (!targetRepository.existsById(targetId)) {
            return;
        }
        targetRepository.deleteById(targetId);
        fileSdWriter.rewrite();
    }

    MonitorTarget toTarget(PersistentMonitorTargetRecord record) {
        return new MonitorTarget(
                record.getId(),
                record.getProjectId(),
                record.getType(),
                record.getName(),
                record.getServiceName(),
                record.getHost(),
                record.getSshUsername(),
                record.getSshPort(),
                record.getPluginDir(),
                record.getSshPassword() != null && !record.getSshPassword().isBlank(),
                record.getPort(),
                record.getMetricsPath(),
                record.getEnv(),
                MonitoringJson.readLabels(objectMapper, record.getLabelsJson()),
                MonitoringJson.readItems(objectMapper, record.getItemsJson()),
                record.getEnabled(),
                record.getLastCheckStatus(),
                record.getLastCheckMessage(),
                record.getLastCheckedAt(),
                record.getCreatedAt(),
                record.getUpdatedAt()
        );
    }

    private MonitorTargetInput normalize(MonitorTargetInput input) {
        if (input == null) {
            throw new MonitoringValidationException("monitor target is required");
        }
        String name = required(input.name(), "monitor target name is required");
        String serviceName = input.serviceName() == null || input.serviceName().isBlank() ? name : input.serviceName().trim();
        String host = required(input.host(), "host is required");
        int port = input.port() == null ? 0 : input.port();
        if (port <= 0 || port > 65535) {
            throw new MonitoringValidationException("port is invalid");
        }
        String metricsPath = input.metricsPath() == null || input.metricsPath().isBlank() ? "/metrics" : input.metricsPath().trim();
        if (!metricsPath.startsWith("/")) {
            metricsPath = "/" + metricsPath;
        }
        String env = input.env() == null || input.env().isBlank() ? "default" : input.env().trim();
        List<MonitorItem> items = MonitoringJson.readItems(objectMapper, MonitoringJson.writeItems(objectMapper, input.items()));
        return new MonitorTargetInput(
                name,
                serviceName,
                host,
                blankToNull(input.sshUsername()),
                blankToNull(input.sshPassword()),
                input.sshPort(),
                blankToNull(input.pluginDir()),
                port,
                metricsPath,
                env,
                input.labels(),
                items,
                input.enabled() == null || input.enabled()
        );
    }

    private MonitorTargetInput normalizeForUpdate(PersistentMonitorTargetRecord existing, MonitorTargetInput normalized) {
        return new MonitorTargetInput(
                normalized.name(),
                normalized.serviceName(),
                normalized.host(),
                normalized.sshUsername() != null ? normalized.sshUsername() : existing.getSshUsername(),
                normalized.sshPassword() != null ? normalized.sshPassword() : existing.getSshPassword(),
                normalized.sshPort() != null ? normalized.sshPort() : existing.getSshPort(),
                normalized.pluginDir() != null ? normalized.pluginDir() : existing.getPluginDir(),
                normalized.port(),
                normalized.metricsPath(),
                normalized.env(),
                normalized.labels(),
                normalized.items(),
                normalized.enabled()
        );
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new MonitoringValidationException(message);
        }
        return value.trim();
    }

    private String normalizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "metrics endpoint unavailable";
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    public record MonitorTargetInput(
            String name,
            String serviceName,
            String host,
            String sshUsername,
            String sshPassword,
            Integer sshPort,
            String pluginDir,
            Integer port,
            String metricsPath,
            String env,
            Map<String, String> labels,
            List<MonitorItem> items,
            Boolean enabled
    ) {
    }
}
