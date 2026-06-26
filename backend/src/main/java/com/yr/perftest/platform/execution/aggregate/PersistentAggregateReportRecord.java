package com.yr.perftest.platform.execution.aggregate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
        name = "aggregate_report",
        uniqueConstraints = @UniqueConstraint(columnNames = "execution_id")
)
public class PersistentAggregateReportRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "execution_id", nullable = false)
    private Long executionId;

    @Column(name = "accuracy", length = 16, nullable = false)
    private String accuracy;

    @Column(name = "start_ms")
    private Long startMs;

    @Column(name = "end_ms")
    private Long endMs;

    @Column(name = "duration_seconds")
    private Double durationSeconds;

    @Lob
    @Column(name = "summary_json", nullable = false)
    private String summaryJson;

    @Lob
    @Column(name = "rows_json", nullable = false)
    private String rowsJson;

    @Lob
    @Column(name = "snapshot_blob")
    private byte[] snapshotBlob;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "builder_version", length = 32)
    private String builderVersion;

    protected PersistentAggregateReportRecord() {
    }

    public PersistentAggregateReportRecord(
            Long executionId,
            String accuracy,
            Long startMs,
            Long endMs,
            Double durationSeconds,
            String summaryJson,
            String rowsJson,
            byte[] snapshotBlob,
            Instant generatedAt,
            String builderVersion
    ) {
        this.executionId = executionId;
        this.accuracy = accuracy;
        this.startMs = startMs;
        this.endMs = endMs;
        this.durationSeconds = durationSeconds;
        this.summaryJson = summaryJson;
        this.rowsJson = rowsJson;
        this.snapshotBlob = snapshotBlob;
        this.generatedAt = generatedAt;
        this.builderVersion = builderVersion;
    }

    public Long getId() {
        return id;
    }

    public Long getExecutionId() {
        return executionId;
    }

    public String getAccuracy() {
        return accuracy;
    }

    public Long getStartMs() {
        return startMs;
    }

    public Long getEndMs() {
        return endMs;
    }

    public Double getDurationSeconds() {
        return durationSeconds;
    }

    public String getSummaryJson() {
        return summaryJson;
    }

    public String getRowsJson() {
        return rowsJson;
    }

    public byte[] getSnapshotBlob() {
        return snapshotBlob;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public String getBuilderVersion() {
        return builderVersion;
    }
}
