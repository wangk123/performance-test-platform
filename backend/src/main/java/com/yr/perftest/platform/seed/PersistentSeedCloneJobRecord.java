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
@Table(name = "seed_clone_job")
public class PersistentSeedCloneJobRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private Long templateId;

    @Column(nullable = false)
    private Long datasourceId;

    @Column(nullable = false)
    private Integer cloneCount;

    @Column(nullable = false, length = 32)
    private String failurePolicy;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(nullable = false)
    private Integer successBatches;

    @Column(nullable = false)
    private Integer failedBatches;

    @Lob
    private String errorJson;

    @Column(nullable = false, length = 120)
    private String createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant finishedAt;

    protected PersistentSeedCloneJobRecord() {
    }

    public PersistentSeedCloneJobRecord(
            long projectId,
            long templateId,
            long datasourceId,
            int cloneCount,
            String failurePolicy,
            String createdBy
    ) {
        this.projectId = projectId;
        this.templateId = templateId;
        this.datasourceId = datasourceId;
        this.cloneCount = cloneCount;
        this.failurePolicy = failurePolicy;
        this.status = "PENDING";
        this.successBatches = 0;
        this.failedBatches = 0;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public void markRunning() {
        this.status = "RUNNING";
    }

    public void complete(int success, int failed, String errorJson, String status) {
        this.successBatches = success;
        this.failedBatches = failed;
        this.errorJson = errorJson;
        this.status = status;
        this.finishedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public Long getTemplateId() {
        return templateId;
    }

    public Long getDatasourceId() {
        return datasourceId;
    }

    public Integer getCloneCount() {
        return cloneCount;
    }

    public String getFailurePolicy() {
        return failurePolicy;
    }

    public String getStatus() {
        return status;
    }

    public Integer getSuccessBatches() {
        return successBatches;
    }

    public Integer getFailedBatches() {
        return failedBatches;
    }

    public String getErrorJson() {
        return errorJson;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
