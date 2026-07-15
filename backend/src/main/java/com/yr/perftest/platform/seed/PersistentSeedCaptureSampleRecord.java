package com.yr.perftest.platform.seed;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
        name = "seed_capture_sample",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_seed_capture_sample_strategy_seq",
                columnNames = {"strategy_id", "sample_seq"}
        ),
        indexes = {
                @Index(
                        name = "idx_seed_capture_sample_strategy_time",
                        columnList = "strategy_id, capture_started_at, sample_seq"
                ),
                @Index(
                        name = "idx_seed_capture_sample_datasource_status",
                        columnList = "datasource_id, status"
                )
        }
)
public class PersistentSeedCaptureSampleRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "strategy_id", nullable = false)
    private Long strategyId;

    @Column(name = "datasource_id", nullable = false)
    private Long datasourceId;

    @Column(name = "sample_seq", nullable = false)
    private Integer sampleSeq;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "capture_started_at", nullable = false)
    private Instant captureStartedAt;

    @Column(name = "capture_finished_at")
    private Instant captureFinishedAt;

    @Lob
    @Column(name = "config_snapshot_json", nullable = false)
    private String configSnapshotJson;

    @Column(name = "config_version", nullable = false)
    private Integer configVersion;

    @Column(nullable = false, length = 32)
    private String phase;

    @Column(name = "completed_tables", nullable = false)
    private Integer completedTables;

    @Column(name = "total_tables", nullable = false)
    private Integer totalTables;

    @Lob
    @Column(name = "current_tables_json", nullable = false)
    private String currentTablesJson;

    @Column(name = "captured_rows", nullable = false)
    private Long capturedRows;

    @Column(name = "written_bytes", nullable = false)
    private Long writtenBytes;

    @Column(name = "active_workers", nullable = false)
    private Integer activeWorkers;

    @Column(name = "heartbeat_at")
    private Instant heartbeatAt;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(nullable = false)
    private Boolean incomplete;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PersistentSeedCaptureSampleRecord() {
    }

    public PersistentSeedCaptureSampleRecord(
            long projectId,
            long strategyId,
            long datasourceId,
            int sampleSeq,
            String status,
            Instant captureStartedAt,
            Instant captureFinishedAt,
            String configSnapshotJson,
            int configVersion
    ) {
        Instant now = Instant.now();
        this.projectId = projectId;
        this.strategyId = strategyId;
        this.datasourceId = datasourceId;
        this.sampleSeq = sampleSeq;
        this.status = status;
        this.captureStartedAt = captureStartedAt;
        this.captureFinishedAt = captureFinishedAt;
        this.configSnapshotJson = configSnapshotJson;
        this.configVersion = configVersion;
        this.phase = status;
        this.completedTables = 0;
        this.totalTables = 0;
        this.currentTablesJson = "[]";
        this.capturedRows = 0L;
        this.writtenBytes = 0L;
        this.activeWorkers = 0;
        this.heartbeatAt = captureStartedAt;
        this.incomplete = false;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public PersistentSeedCaptureSampleRecord(
            long projectId,
            long strategyId,
            long datasourceId,
            int sampleSeq,
            String configSnapshotJson,
            int configVersion
    ) {
        this(
                projectId,
                strategyId,
                datasourceId,
                sampleSeq,
                "QUEUED",
                Instant.now(),
                null,
                configSnapshotJson,
                configVersion
        );
    }

    public void markStatus(String status) {
        CaptureSampleStateMachine.requireTransition(this.status, status);
        this.status = status;
        this.phase = status;
        if (CaptureSampleStateMachine.isTerminal(status)) {
            this.captureFinishedAt = Instant.now();
        }
        this.updatedAt = Instant.now();
    }

    public void markPreparing() {
        transitionTo("PREPARING");
    }

    public void markCapturing() {
        transitionTo("CAPTURING");
    }

    public void requestCancel() {
        transitionTo("CANCEL_REQUESTED");
    }

    public void cancel() {
        transitionTo("CANCELED");
        this.incomplete = true;
    }

    public void succeed() {
        transitionTo("SUCCEEDED");
        this.incomplete = false;
    }

    public void markInterrupted(String errorMessage) {
        transitionTo("INTERRUPTED");
        this.errorMessage = errorMessage;
        this.incomplete = true;
    }

    public void updateHeartbeat(Instant heartbeatAt) {
        this.heartbeatAt = heartbeatAt == null ? Instant.now() : heartbeatAt;
        this.updatedAt = Instant.now();
    }

    public void updateProgress(
            String phase,
            int completedTables,
            int totalTables,
            String currentTablesJson,
            long capturedRows,
            long writtenBytes,
            int activeWorkers,
            Instant heartbeatAt
    ) {
        this.phase = phase;
        this.completedTables = completedTables;
        this.totalTables = totalTables;
        this.currentTablesJson = currentTablesJson;
        this.capturedRows = capturedRows;
        this.writtenBytes = writtenBytes;
        this.activeWorkers = activeWorkers;
        this.heartbeatAt = heartbeatAt;
        this.updatedAt = Instant.now();
    }

    public void fail(String errorMessage, boolean incomplete) {
        CaptureSampleStateMachine.requireTransition(this.status, "FAILED");
        this.status = "FAILED";
        this.phase = "FAILED";
        this.errorMessage = errorMessage;
        this.incomplete = incomplete;
        this.captureFinishedAt = Instant.now();
        this.updatedAt = this.captureFinishedAt;
    }

    public void recordDeletionFailure(String errorMessage) {
        this.errorMessage = errorMessage;
        this.incomplete = true;
        this.updatedAt = Instant.now();
    }

    private void transitionTo(String target) {
        CaptureSampleStateMachine.requireTransition(this.status, target);
        this.status = target;
        this.phase = target;
        if (CaptureSampleStateMachine.isTerminal(target)) {
            this.captureFinishedAt = Instant.now();
        }
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public Long getStrategyId() {
        return strategyId;
    }

    public Long getDatasourceId() {
        return datasourceId;
    }

    public Integer getSampleSeq() {
        return sampleSeq;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCaptureStartedAt() {
        return captureStartedAt;
    }

    public Instant getCaptureFinishedAt() {
        return captureFinishedAt;
    }

    public String getConfigSnapshotJson() {
        return configSnapshotJson;
    }

    public Integer getConfigVersion() {
        return configVersion;
    }

    public String getPhase() {
        return phase;
    }

    public Integer getCompletedTables() {
        return completedTables;
    }

    public Integer getTotalTables() {
        return totalTables;
    }

    public String getCurrentTablesJson() {
        return currentTablesJson;
    }

    public Long getCapturedRows() {
        return capturedRows;
    }

    public Long getWrittenBytes() {
        return writtenBytes;
    }

    public Integer getActiveWorkers() {
        return activeWorkers;
    }

    public Instant getHeartbeatAt() {
        return heartbeatAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Boolean getIncomplete() {
        return incomplete;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
