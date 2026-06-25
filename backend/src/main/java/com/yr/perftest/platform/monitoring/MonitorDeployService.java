package com.yr.perftest.platform.monitoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.execution.distributed.RemoteRunnerClient;
import com.yr.perftest.platform.execution.distributed.RemoteRunnerResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
public class MonitorDeployService {
    private final PersistentMonitorTargetRepository targetRepository;
    private final RemoteRunnerClient remoteRunnerClient;
    private final ObjectMapper objectMapper;
    private final Path bundleRoot;

    public MonitorDeployService(
            PersistentMonitorTargetRepository targetRepository,
            RemoteRunnerClient remoteRunnerClient,
            ObjectMapper objectMapper,
            @Value("${platform.monitoring.deploy.bundle-path:../deploy/monitoring/prometheus}") String bundlePath
    ) {
        this.targetRepository = targetRepository;
        this.remoteRunnerClient = remoteRunnerClient;
        this.objectMapper = objectMapper;
        this.bundleRoot = resolveBundleRoot(bundlePath);
    }

    @Transactional(readOnly = true)
    public MonitorDeployResult deployTarget(long targetId) {
        PersistentMonitorTargetRecord record = targetRepository.findById(targetId)
                .orElseThrow(() -> new MonitoringValidationException("monitor target does not exist"));
        String username = required(record.getSshUsername(), "ssh username is required for deploy");
        String password = required(record.getSshPassword(), "ssh password is required for deploy");
        String pluginDir = required(record.getPluginDir(), "plugin directory is required for deploy");
        List<MonitorItem> items = MonitoringJson.readItems(objectMapper, record.getItemsJson());
        if (items.stream().anyMatch(item -> item.type() == MonitorItemType.JAVA_JMX_AGENT)
                && !Files.isRegularFile(bundleRoot.resolve("jmx_prometheus_javaagent.jar"))) {
            throw new MonitoringValidationException("jmx_prometheus_javaagent.jar is missing in monitoring bundle: " + bundleRoot);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("host", record.getHost());
        payload.put("sshPort", record.getSshPort() == null ? 22 : record.getSshPort());
        payload.put("sshUsername", username);
        payload.put("sshPassword", password);
        payload.put("pluginDir", pluginDir);
        payload.put("nodeExporterPort", record.getPort());
        payload.put("items", items.stream().map(this::itemPayload).toList());
        payload.put("files", listBundleFiles(items));
        RemoteRunnerResult result = remoteRunnerClient.deployMonitoring(payload);
        if (!result.ok()) {
            throw new MonitoringValidationException(result.message() == null || result.message().isBlank()
                    ? "monitoring deploy failed"
                    : result.message());
        }
        return new MonitorDeployResult(
                true,
                "bundle uploaded",
                prometheusDir(pluginDir),
                parseUploadedFiles(result.log()),
                result.startResults(),
                buildAgentCommands(pluginDir, items)
        );
    }

    private Map<String, Object> itemPayload(MonitorItem item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", item.type().name());
        payload.put("name", item.name());
        payload.put("port", item.port());
        return payload;
    }

    private List<Map<String, String>> listBundleFiles(List<MonitorItem> items) {
        List<String> allowedPrefixes = new ArrayList<>();
        allowedPrefixes.add("node-exporter/");
        for (MonitorItem item : items) {
            switch (item.type()) {
                case JAVA_JMX_AGENT -> {
                    allowedPrefixes.add("jmx_prometheus_javaagent.jar");
                    allowedPrefixes.add("java-agent-config.yml");
                }
                case MYSQL_EXPORTER -> allowedPrefixes.add("mysql-exporter/");
                case REDIS_EXPORTER -> allowedPrefixes.add("redis-exporter/");
                case NGINX_EXPORTER -> allowedPrefixes.add("nginx-exporter/");
                case KAFKA_EXPORTER -> allowedPrefixes.add("kafka-exporter/");
                default -> {
                }
            }
        }
        try (Stream<Path> paths = Files.walk(bundleRoot)) {
            List<Map<String, String>> files = new ArrayList<>();
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String relativePath = bundleRoot.relativize(path).toString().replace('\\', '/');
                if (relativePath.endsWith(".gitkeep") || !shouldInclude(relativePath, allowedPrefixes)) {
                    continue;
                }
                files.add(Map.of(
                        "localPath", path.toAbsolutePath().toString(),
                        "relativePath", relativePath
                ));
            }
            if (files.isEmpty()) {
                throw new MonitoringValidationException("monitoring bundle is empty");
            }
            return files;
        } catch (MonitoringValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new MonitoringValidationException("failed to read monitoring bundle");
        }
    }

    private boolean shouldInclude(String relativePath, List<String> allowedPrefixes) {
        for (String allowed : allowedPrefixes) {
            if (allowed.endsWith("/")) {
                if (relativePath.startsWith(allowed)) {
                    return true;
                }
            } else if (relativePath.equals(allowed)) {
                return true;
            }
        }
        return false;
    }

    private List<MonitorDeployCommand> buildAgentCommands(String pluginDir, List<MonitorItem> items) {
        String base = prometheusDir(pluginDir);
        List<MonitorDeployCommand> commands = new ArrayList<>();
        for (MonitorItem item : items) {
            if (item.type() != MonitorItemType.JAVA_JMX_AGENT) {
                continue;
            }
            String agentArg = "-javaagent:" + base + "/jmx_prometheus_javaagent.jar="
                    + item.port() + ":" + base + "/java-agent-config.yml";
            commands.add(new MonitorDeployCommand(
                    "JVM Agent 启动参数 · " + item.name(),
                    "export JAVA_OPTS=\"$JAVA_OPTS " + agentArg + "\""
            ));
            commands.add(new MonitorDeployCommand(
                    "JVM Agent 完整启动示例 · " + item.name(),
                    "java " + agentArg + " -jar <应用jar路径>"
            ));
            if (item.processKeyword() != null && !item.processKeyword().isBlank()) {
                commands.add(new MonitorDeployCommand(
                        "目标进程关键字 · " + item.name(),
                        "ps aux | grep '" + item.processKeyword() + "'"
                ));
            }
        }
        return commands;
    }

    private String prometheusDir(String pluginDir) {
        String root = pluginDir.endsWith("/") ? pluginDir.substring(0, pluginDir.length() - 1) : pluginDir;
        return root + "/prometheus";
    }

    private List<String> parseUploadedFiles(String message) {
        if (message == null || message.isBlank()) {
            return List.of();
        }
        return List.of(message.split("\n")).stream().map(String::trim).filter(line -> !line.isBlank()).toList();
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new MonitoringValidationException(message);
        }
        return value.trim();
    }

    private Path resolveBundleRoot(String bundlePath) {
        List<String> candidates = List.of(
                bundlePath,
                "../" + bundlePath.replaceFirst("^\\./", ""),
                bundlePath.replaceFirst("^\\./", "deploy/monitoring/prometheus")
        );
        for (String candidate : candidates) {
            Path root = Path.of(candidate).normalize();
            if (Files.isDirectory(root.resolve("node-exporter"))) {
                return root.toAbsolutePath().normalize();
            }
        }
        return Path.of(bundlePath).normalize().toAbsolutePath();
    }
}
