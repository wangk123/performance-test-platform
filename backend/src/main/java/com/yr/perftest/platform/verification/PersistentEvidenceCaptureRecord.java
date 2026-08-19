package com.yr.perftest.platform.verification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 取证请求记录（T9）：声明目的/影响/成本，经审批后执行证据快照并回流证据定位。
 */
@Entity
@Table(name = "evidence_captures")
public class PersistentEvidenceCaptureRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long executionId;

    @Column(nullable = false, length = 48)
    private String kind;

    @Column(nullable = false, length = 512)
    private String purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CaptureImpact impactLevel;

    @Column(length = 512)
    private String costNote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private CaptureStatus status;

    @Column(nullable = false, length = 16)
    private String requestedByType;

    @Column(nullable = false, length = 128)
    private String requestedByName;

    @Column(length = 16)
    private String approvedByType;

    @Column(length = 128)
    private String approvedByName;

    @Column(length = 512)
    private String bundlePath;

    @Column(length = 4096)
    private String summaryJson;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant approvedAt;

    private Instant completedAt;

    protected PersistentEvidenceCaptureRecord() {
    }

    public PersistentEvidenceCaptureRecord(
            Long executionId,
            String kind,
            String purpose,
            CaptureImpact impactLevel,
            String costNote,
            String requestedByType,
            String requestedByName
    ) {
        this.executionId = executionId;
        this.kind = kind;
        this.purpose = purpose;
        this.impactLevel = impactLevel;
        this.costNote = costNote;
        this.status = CaptureStatus.PENDING_APPROVAL;
        this.requestedByType = requestedByType;
        this.requestedByName = requestedByName;
        this.createdAt = Instant.now();
    }

    public void markApproved(String approverType, String approverName) {
        this.status = CaptureStatus.APPROVED;
        this.approvedByType = approverType;
        this.approvedByName = approverName;
        this.approvedAt = Instant.now();
    }

    public void markRejected(String approverType, String approverName) {
        this.status = CaptureStatus.REJECTED;
        this.approvedByType = approverType;
        this.approvedByName = approverName;
        this.approvedAt = Instant.now();
    }

    public void markCompleted(String bundlePath, String summaryJson) {
        this.status = CaptureStatus.COMPLETED;
        this.bundlePath = bundlePath;
        this.summaryJson = summaryJson;
        this.completedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getExecutionId() {
        return executionId;
    }

    public String getKind() {
        return kind;
    }

    public String getPurpose() {
        return purpose;
    }

    public CaptureImpact getImpactLevel() {
        return impactLevel;
    }

    public String getCostNote() {
        return costNote;
    }

    public CaptureStatus getStatus() {
        return status;
    }

    public String getRequestedByType() {
        return requestedByType;
    }

    public String getRequestedByName() {
        return requestedByName;
    }

    public String getApprovedByType() {
        return approvedByType;
    }

    public String getApprovedByName() {
        return approvedByName;
    }

    public String getBundlePath() {
        return bundlePath;
    }

    public String getSummaryJson() {
        return summaryJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
