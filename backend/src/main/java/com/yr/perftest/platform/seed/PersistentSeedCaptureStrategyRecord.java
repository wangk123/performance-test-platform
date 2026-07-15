package com.yr.perftest.platform.seed;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(
        name = "seed_capture_strategy",
        indexes = {
                @Index(name = "idx_seed_capture_strategy_project_updated", columnList = "project_id, updated_at"),
                @Index(name = "idx_seed_capture_strategy_datasource", columnList = "project_id, datasource_id")
        }
)
public class PersistentSeedCaptureStrategyRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "datasource_id", nullable = false)
    private Long datasourceId;

    @Lob
    @Column(name = "include_json", nullable = false)
    private String includeJson;

    @Lob
    @Column(name = "exclude_json", nullable = false)
    private String excludeJson;

    @Column(name = "thread_count", nullable = false)
    private Integer threadCount;

    @Column(name = "batch_rows", nullable = false)
    private Integer batchRows;

    @Column(name = "config_version", nullable = false)
    private Integer configVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PersistentSeedCaptureStrategyRecord() {
    }

    public PersistentSeedCaptureStrategyRecord(
            long projectId,
            String name,
            long datasourceId,
            String includeJson,
            String excludeJson,
            int threadCount,
            int batchRows
    ) {
        Instant now = Instant.now();
        this.projectId = projectId;
        this.name = name;
        this.datasourceId = datasourceId;
        this.includeJson = includeJson;
        this.excludeJson = excludeJson;
        this.threadCount = threadCount;
        this.batchRows = batchRows;
        this.configVersion = 1;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateConfiguration(
            String name,
            long datasourceId,
            String includeJson,
            String excludeJson,
            int threadCount,
            int batchRows
    ) {
        this.name = name;
        this.datasourceId = datasourceId;
        this.includeJson = includeJson;
        this.excludeJson = excludeJson;
        this.threadCount = threadCount;
        this.batchRows = batchRows;
        this.configVersion++;
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

    public Long getDatasourceId() {
        return datasourceId;
    }

    public String getIncludeJson() {
        return includeJson;
    }

    public String getExcludeJson() {
        return excludeJson;
    }

    public Integer getThreadCount() {
        return threadCount;
    }

    public Integer getBatchRows() {
        return batchRows;
    }

    public Integer getConfigVersion() {
        return configVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
