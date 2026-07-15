package com.yr.perftest.platform.seed;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(
        name = "seed_capture_analysis",
        indexes = {
                @Index(name = "idx_seed_capture_analysis_project_status", columnList = "project_id, status"),
                @Index(name = "idx_seed_capture_analysis_strategy_status", columnList = "strategy_id, status")
        }
)
public class PersistentSeedCaptureAnalysisRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "strategy_id", nullable = false)
    private Long strategyId;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(nullable = false, length = 32)
    private String phase;

    @Column(name = "completed_tables", nullable = false)
    private Integer completedTables;

    @Column(name = "total_tables", nullable = false)
    private Integer totalTables;

    @Lob
    @Column(name = "current_tables_json", nullable = false)
    private String currentTablesJson;

    @Column(name = "compared_rows", nullable = false)
    private Long comparedRows;

    @Column(name = "skipped_tables", nullable = false)
    private Integer skippedTables;

    @Column(name = "fine_screened_chunks", nullable = false)
    private Integer fineScreenedChunks;

    @Column(name = "candidate_operation_count", nullable = false)
    private Integer candidateOperationCount;

    @Lob
    @Column(name = "input_sample_ids_json", nullable = false)
    private String inputSampleIdsJson;

    @Lob
    @Column(name = "input_manifest_json", nullable = false)
    private String inputManifestJson;

    @Lob
    @Column(name = "summary_json", nullable = false)
    private String summaryJson;

    @Column(name = "template_id")
    private Long templateId;

    @Column(name = "heartbeat_at")
    private Instant heartbeatAt;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected PersistentSeedCaptureAnalysisRecord() {
    }

    public PersistentSeedCaptureAnalysisRecord(
            long projectId,
            long strategyId,
            String inputSampleIdsJson
    ) {
        Instant now = Instant.now();
        this.projectId = projectId;
        this.strategyId = strategyId;
        this.status = "QUEUED";
        this.phase = "QUEUED";
        this.completedTables = 0;
        this.totalTables = 0;
        this.currentTablesJson = "[]";
        this.comparedRows = 0L;
        this.skippedTables = 0;
        this.fineScreenedChunks = 0;
        this.candidateOperationCount = 0;
        this.inputSampleIdsJson = inputSampleIdsJson;
        this.inputManifestJson = "{}";
        this.summaryJson = "{}";
        this.heartbeatAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markStatus(String status) {
        transitionTo(status);
    }

    public void requestCancel() {
        transitionTo("CANCEL_REQUESTED");
    }

    public void cancel() {
        transitionTo("CANCELED");
    }

    public void markInterrupted(String errorMessage) {
        transitionTo("INTERRUPTED");
        this.errorMessage = errorMessage;
    }

    public void setInputManifest(String inputManifestJson) {
        String manifest = Objects.requireNonNull(inputManifestJson, "inputManifestJson");
        if (!"{}".equals(this.inputManifestJson) && !this.inputManifestJson.equals(manifest)) {
            throw new IllegalStateException("analysis input manifest is immutable");
        }
        this.inputManifestJson = manifest;
        this.updatedAt = Instant.now();
    }

    public void setSummary(String summaryJson) {
        this.summaryJson = Objects.requireNonNull(summaryJson, "summaryJson");
        this.updatedAt = Instant.now();
    }

    public void updateHeartbeat(Instant heartbeatAt) {
        this.heartbeatAt = heartbeatAt == null ? Instant.now() : heartbeatAt;
        this.updatedAt = Instant.now();
    }

    private void transitionTo(String target) {
        AnalysisStateMachine.requireTransition(this.status, target);
        this.status = target;
        this.phase = status;
        if (AnalysisStateMachine.isTerminal(status)) {
            this.finishedAt = Instant.now();
        }
        this.updatedAt = Instant.now();
    }

    public void updateProgress(
            String phase,
            int completedTables,
            int totalTables,
            String currentTablesJson,
            long comparedRows,
            int skippedTables,
            int fineScreenedChunks,
            int candidateOperationCount,
            Instant heartbeatAt
    ) {
        this.phase = phase;
        this.completedTables = completedTables;
        this.totalTables = totalTables;
        this.currentTablesJson = currentTablesJson;
        this.comparedRows = comparedRows;
        this.skippedTables = skippedTables;
        this.fineScreenedChunks = fineScreenedChunks;
        this.candidateOperationCount = candidateOperationCount;
        this.heartbeatAt = heartbeatAt;
        this.updatedAt = Instant.now();
    }

    public void complete(String summaryJson, Long templateId) {
        transitionTo("SUCCEEDED");
        this.summaryJson = summaryJson;
        this.templateId = templateId;
    }

    public void fail(String errorMessage) {
        transitionTo("FAILED");
        this.errorMessage = errorMessage;
    }

    public void recordDeletionFailure(String errorMessage) {
        this.errorMessage = errorMessage;
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

    public String getStatus() {
        return status;
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

    public Long getComparedRows() {
        return comparedRows;
    }

    public Integer getSkippedTables() {
        return skippedTables;
    }

    public Integer getFineScreenedChunks() {
        return fineScreenedChunks;
    }

    public Integer getCandidateOperationCount() {
        return candidateOperationCount;
    }

    public String getInputSampleIdsJson() {
        return inputSampleIdsJson;
    }

    public String getInputManifestJson() {
        return inputManifestJson;
    }

    public String getSummaryJson() {
        return summaryJson;
    }

    public Long getTemplateId() {
        return templateId;
    }

    public Instant getHeartbeatAt() {
        return heartbeatAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
