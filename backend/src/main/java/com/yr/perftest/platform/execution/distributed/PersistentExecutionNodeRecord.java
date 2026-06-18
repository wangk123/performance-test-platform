package com.yr.perftest.platform.execution.distributed;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "execution_nodes")
public class PersistentExecutionNodeRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 160)
    private String host;

    @Column(nullable = false)
    private Integer sshPort;

    @Column(nullable = false, length = 80)
    private String sshUsername;

    @Column(nullable = false, length = 1000)
    private String sshKeyPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ExecutionNodeRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ExecutionNodeStatus status;

    @Column(nullable = false, length = 1000)
    private String remoteWorkDir;

    private Instant lastCheckedAt;

    @Column(length = 2000)
    private String lastMessage;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected PersistentExecutionNodeRecord() {
    }

    public PersistentExecutionNodeRecord(
            String name,
            String host,
            Integer sshPort,
            String sshUsername,
            String sshKeyPath,
            ExecutionNodeRole role,
            String remoteWorkDir
    ) {
        Instant now = Instant.now();
        this.name = name;
        this.host = host;
        this.sshPort = sshPort;
        this.sshUsername = sshUsername;
        this.sshKeyPath = sshKeyPath;
        this.role = role;
        this.remoteWorkDir = remoteWorkDir;
        this.status = ExecutionNodeStatus.UNKNOWN;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getHost() {
        return host;
    }

    public Integer getSshPort() {
        return sshPort;
    }

    public String getSshUsername() {
        return sshUsername;
    }

    public String getSshKeyPath() {
        return sshKeyPath;
    }

    public ExecutionNodeRole getRole() {
        return role;
    }

    public ExecutionNodeStatus getStatus() {
        return status;
    }

    public String getRemoteWorkDir() {
        return remoteWorkDir;
    }

    public Instant getLastCheckedAt() {
        return lastCheckedAt;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void update(
            String name,
            String host,
            Integer sshPort,
            String sshUsername,
            String sshKeyPath,
            ExecutionNodeRole role,
            String remoteWorkDir
    ) {
        this.name = name;
        this.host = host;
        this.sshPort = sshPort;
        this.sshUsername = sshUsername;
        this.sshKeyPath = sshKeyPath;
        this.role = role;
        this.remoteWorkDir = remoteWorkDir;
        this.updatedAt = Instant.now();
    }

    public void markHealth(ExecutionNodeStatus status, String message) {
        this.status = status;
        this.lastMessage = message == null ? "" : message;
        this.lastCheckedAt = Instant.now();
        this.updatedAt = lastCheckedAt;
    }
}
