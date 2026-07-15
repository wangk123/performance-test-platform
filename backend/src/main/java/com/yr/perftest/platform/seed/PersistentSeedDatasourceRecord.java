package com.yr.perftest.platform.seed;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "seed_datasource")
public class PersistentSeedDatasourceRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 255)
    private String host;

    @Column(nullable = false)
    private Integer port;

    @Column(nullable = false, length = 120)
    private String databaseName;

    @Column(nullable = false, length = 120)
    private String username;

    @Column(nullable = false, length = 1000)
    private String passwordEnc;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected PersistentSeedDatasourceRecord() {
    }

    public PersistentSeedDatasourceRecord(
            long projectId,
            String name,
            String host,
            int port,
            String databaseName,
            String username,
            String passwordEnc
    ) {
        Instant now = Instant.now();
        this.projectId = projectId;
        this.name = name;
        this.host = host;
        this.port = port;
        this.databaseName = databaseName;
        this.username = username;
        this.passwordEnc = passwordEnc;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String name, String host, int port, String databaseName, String username, String passwordEncOrNull) {
        this.name = name;
        this.host = host;
        this.port = port;
        this.databaseName = databaseName;
        this.username = username;
        if (passwordEncOrNull != null && !passwordEncOrNull.isBlank()) {
            this.passwordEnc = passwordEncOrNull;
        }
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public String getName() {
        return name;
    }

    public String getHost() {
        return host;
    }

    public Integer getPort() {
        return port;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordEnc() {
        return passwordEnc;
    }

    public SeedDatasourceView toView() {
        return new SeedDatasourceView(id, projectId, name, host, port, databaseName, username, passwordEnc != null && !passwordEnc.isBlank(), createdAt, updatedAt);
    }
}
