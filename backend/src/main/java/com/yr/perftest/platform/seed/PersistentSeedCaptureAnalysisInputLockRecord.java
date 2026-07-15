package com.yr.perftest.platform.seed;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
        name = "seed_capture_analysis_input_lock",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_seed_capture_analysis_lock_sample",
                columnNames = "sample_id"
        ),
        indexes = @Index(
                name = "idx_seed_capture_analysis_lock_analysis",
                columnList = "analysis_id"
        )
)
public class PersistentSeedCaptureAnalysisInputLockRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "analysis_id", nullable = false)
    private Long analysisId;

    @Column(name = "sample_id", nullable = false)
    private Long sampleId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PersistentSeedCaptureAnalysisInputLockRecord() {
    }

    public PersistentSeedCaptureAnalysisInputLockRecord(long analysisId, long sampleId) {
        this.analysisId = analysisId;
        this.sampleId = sampleId;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getAnalysisId() {
        return analysisId;
    }

    public Long getSampleId() {
        return sampleId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
