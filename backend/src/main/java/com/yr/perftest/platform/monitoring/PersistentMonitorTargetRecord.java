package com.yr.perftest.platform.monitoring;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "monitor_target")
public class PersistentMonitorTargetRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private MonitorTargetType type;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 120)
    private String serviceName;

    @Column(nullable = false, length = 160)
    private String host;

    @Column(length = 80)
    private String sshUsername;

    @Column(length = 200)
    private String sshPassword;

    private Integer sshPort;

    @Column(length = 500)
    private String pluginDir;

    @Column(nullable = false)
    private Integer port;

    @Column(nullable = false, length = 120)
    private String metricsPath;

    @Column(nullable = false, length = 40)
    private String env;

    @Lob
    private String labelsJson;

    @Lob
    private String itemsJson;

    @Column(nullable = false)
    private Boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private MonitorTargetCheckStatus lastCheckStatus;

    @Column(length = 1000)
    private String lastCheckMessage;

    private Instant lastCheckedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected PersistentMonitorTargetRecord() {
    }

    public PersistentMonitorTargetRecord(
            Long projectId,
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
            String labelsJson,
            String itemsJson,
            Boolean enabled
    ) {
        this.projectId = projectId;
        this.type = MonitorTargetType.SERVER;
        this.name = name;
        this.serviceName = serviceName;
        this.host = host;
        this.sshUsername = sshUsername;
        this.sshPassword = sshPassword;
        this.sshPort = sshPort;
        this.pluginDir = pluginDir;
        this.port = port;
        this.metricsPath = metricsPath;
        this.env = env;
        this.labelsJson = labelsJson;
        this.itemsJson = itemsJson;
        this.enabled = enabled;
        this.lastCheckStatus = MonitorTargetCheckStatus.UNKNOWN;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public MonitorTargetType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getHost() {
        return host;
    }

    public String getSshUsername() {
        return sshUsername;
    }

    public String getSshPassword() {
        return sshPassword;
    }

    public Integer getSshPort() {
        return sshPort;
    }

    public String getPluginDir() {
        return pluginDir;
    }

    public Integer getPort() {
        return port;
    }

    public String getMetricsPath() {
        return metricsPath;
    }

    public String getEnv() {
        return env;
    }

    public String getLabelsJson() {
        return labelsJson;
    }

    public String getItemsJson() {
        return itemsJson;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public MonitorTargetCheckStatus getLastCheckStatus() {
        return lastCheckStatus;
    }

    public String getLastCheckMessage() {
        return lastCheckMessage;
    }

    public Instant getLastCheckedAt() {
        return lastCheckedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void update(
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
            String labelsJson,
            String itemsJson,
            Boolean enabled
    ) {
        this.name = name;
        this.serviceName = serviceName;
        this.host = host;
        if (sshUsername != null) {
            this.sshUsername = sshUsername;
        }
        if (sshPassword != null) {
            this.sshPassword = sshPassword;
        }
        if (sshPort != null) {
            this.sshPort = sshPort;
        }
        if (pluginDir != null) {
            this.pluginDir = pluginDir;
        }
        this.port = port;
        this.metricsPath = metricsPath;
        this.env = env;
        this.labelsJson = labelsJson;
        this.itemsJson = itemsJson;
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }

    public void markCheck(MonitorTargetCheckStatus status, String message) {
        this.lastCheckStatus = status;
        this.lastCheckMessage = message;
        this.lastCheckedAt = Instant.now();
        this.updatedAt = lastCheckedAt;
    }
}
