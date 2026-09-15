package com.yr.perftest.platform.task.method;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "plan_evidence_images")
public class PersistentPlanEvidenceImageRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long planId;

    @Column(nullable = false)
    private Long scenarioId;

    private Long executionId;

    @Column(length = 200)
    private String caption;

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false, length = 500)
    private String storedPath;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    @Column(length = 100)
    private String uploadedBy;

    @Column(nullable = false)
    private Instant createdAt;

    protected PersistentPlanEvidenceImageRecord() {
    }

    public PersistentPlanEvidenceImageRecord(Long planId, Long scenarioId, String caption, int sortOrder,
                                             String storedPath, String contentType, long sizeBytes,
                                             String uploadedBy) {
        this.planId = planId;
        this.scenarioId = scenarioId;
        this.caption = caption;
        this.sortOrder = sortOrder;
        this.storedPath = storedPath;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.uploadedBy = uploadedBy;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getPlanId() {
        return planId;
    }

    public Long getScenarioId() {
        return scenarioId;
    }

    public Long getExecutionId() {
        return executionId;
    }

    public void setExecutionId(Long executionId) {
        this.executionId = executionId;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getCaption() {
        return caption;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public String getStoredPath() {
        return storedPath;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
