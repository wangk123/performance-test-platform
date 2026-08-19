package com.yr.perftest.platform.verification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 优化验证记录（T9）：基线 vs 候选三态结论 + 护栏判定 + 明细与理由留档。
 */
@Entity
@Table(name = "verification_records")
public class PersistentVerificationRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long baselineExecutionId;

    @Column(nullable = false)
    private Long candidateExecutionId;

    @Column(nullable = false)
    private Long changeRecordId;

    @Column(nullable = false, length = 16)
    private String verdict;

    @Column(nullable = false, length = 1024)
    private String reasonsJson;

    @Column(nullable = false, length = 8192)
    private String detailsJson;

    @Column(nullable = false, length = 16)
    private String requestedByType;

    @Column(nullable = false, length = 128)
    private String requestedByName;

    @Column(nullable = false)
    private Instant createdAt;

    protected PersistentVerificationRecord() {
    }

    public PersistentVerificationRecord(
            Long baselineExecutionId,
            Long candidateExecutionId,
            Long changeRecordId,
            String verdict,
            String reasonsJson,
            String detailsJson,
            String requestedByType,
            String requestedByName
    ) {
        this.baselineExecutionId = baselineExecutionId;
        this.candidateExecutionId = candidateExecutionId;
        this.changeRecordId = changeRecordId;
        this.verdict = verdict;
        this.reasonsJson = reasonsJson;
        this.detailsJson = detailsJson;
        this.requestedByType = requestedByType;
        this.requestedByName = requestedByName;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getBaselineExecutionId() {
        return baselineExecutionId;
    }

    public Long getCandidateExecutionId() {
        return candidateExecutionId;
    }

    public Long getChangeRecordId() {
        return changeRecordId;
    }

    public String getVerdict() {
        return verdict;
    }

    public String getReasonsJson() {
        return reasonsJson;
    }

    public String getDetailsJson() {
        return detailsJson;
    }

    public String getRequestedByType() {
        return requestedByType;
    }

    public String getRequestedByName() {
        return requestedByName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
