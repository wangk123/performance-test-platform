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
@Table(name = "seed_capture_session")
public class PersistentSeedCaptureSessionRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private Long datasourceId;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(nullable = false, length = 32)
    private String status;

    @Lob
    @Column(nullable = false)
    private String includeJson;

    @Lob
    @Column(nullable = false)
    private String excludeJson;

    @Lob
    private String tableSetJson;

    @Lob
    private String baselineJson;

    @Lob
    private String samplesJson;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected PersistentSeedCaptureSessionRecord() {
    }

    public PersistentSeedCaptureSessionRecord(
            long projectId,
            long datasourceId,
            String provider,
            String includeJson,
            String excludeJson,
            String tableSetJson,
            String baselineJson
    ) {
        Instant now = Instant.now();
        this.projectId = projectId;
        this.datasourceId = datasourceId;
        this.provider = provider;
        this.status = "RECORDING";
        this.includeJson = includeJson;
        this.excludeJson = excludeJson;
        this.tableSetJson = tableSetJson;
        this.baselineJson = baselineJson;
        this.samplesJson = "[]";
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void appendSample(String samplesJson) {
        this.samplesJson = samplesJson;
        this.updatedAt = Instant.now();
    }

    public void updateBaseline(String baselineJson) {
        this.baselineJson = baselineJson;
        this.updatedAt = Instant.now();
    }

    public void finish(String status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public Long getDatasourceId() {
        return datasourceId;
    }

    public String getProvider() {
        return provider;
    }

    public String getStatus() {
        return status;
    }

    public String getIncludeJson() {
        return includeJson;
    }

    public String getExcludeJson() {
        return excludeJson;
    }

    public String getTableSetJson() {
        return tableSetJson;
    }

    public String getBaselineJson() {
        return baselineJson;
    }

    public String getSamplesJson() {
        return samplesJson;
    }
}
