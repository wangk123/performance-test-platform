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
        name = "seed_capture_datasource_lease",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_seed_capture_datasource_lease_datasource",
                columnNames = "datasource_id"
        ),
        indexes = @Index(
                name = "idx_seed_capture_datasource_lease_heartbeat",
                columnList = "heartbeat_at"
        )
)
public class PersistentSeedCaptureDatasourceLeaseRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "datasource_id", nullable = false)
    private Long datasourceId;

    @Column(name = "sample_id", nullable = false)
    private Long sampleId;

    @Column(name = "acquired_at", nullable = false)
    private Instant acquiredAt;

    @Column(name = "heartbeat_at", nullable = false)
    private Instant heartbeatAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PersistentSeedCaptureDatasourceLeaseRecord() {
    }

    public PersistentSeedCaptureDatasourceLeaseRecord(long datasourceId, long sampleId, Instant acquiredAt) {
        this.datasourceId = datasourceId;
        this.sampleId = sampleId;
        this.acquiredAt = acquiredAt;
        this.heartbeatAt = acquiredAt;
        this.updatedAt = acquiredAt;
    }

    public PersistentSeedCaptureDatasourceLeaseRecord(long datasourceId, long sampleId) {
        this(datasourceId, sampleId, Instant.now());
    }

    public void heartbeat(Instant heartbeatAt) {
        this.heartbeatAt = heartbeatAt;
        this.updatedAt = heartbeatAt;
    }

    public DatasourceCaptureLease toLease() {
        return new DatasourceCaptureLease(datasourceId, sampleId, acquiredAt, heartbeatAt);
    }

    public Long getId() {
        return id;
    }

    public Long getDatasourceId() {
        return datasourceId;
    }

    public Long getSampleId() {
        return sampleId;
    }

    public Instant getAcquiredAt() {
        return acquiredAt;
    }

    public Instant getHeartbeatAt() {
        return heartbeatAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
