package com.yr.perftest.platform.monitoring;

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
        name = "execution_target_metrics_snapshot",
        uniqueConstraints = @UniqueConstraint(columnNames = {"execution_id", "kind"})
)
public class PersistentTargetMetricsSnapshotRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "execution_id", nullable = false)
    private Long executionId;

    @Column(name = "kind", nullable = false, length = 32)
    private String kind;

    @Column(name = "unit", length = 32)
    private String unit;

    @Lob
    @Column(name = "series_json", nullable = false)
    private String seriesJson;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    protected PersistentTargetMetricsSnapshotRecord() {
    }

    public PersistentTargetMetricsSnapshotRecord(
            Long executionId,
            String kind,
            String unit,
            String seriesJson,
            Instant capturedAt
    ) {
        this.executionId = executionId;
        this.kind = kind;
        this.unit = unit;
        this.seriesJson = seriesJson;
        this.capturedAt = capturedAt;
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

    public String getUnit() {
        return unit;
    }

    public String getSeriesJson() {
        return seriesJson;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }
}
