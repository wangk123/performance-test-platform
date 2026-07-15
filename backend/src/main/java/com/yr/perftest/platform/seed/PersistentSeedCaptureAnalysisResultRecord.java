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

@Entity
@Table(
        name = "seed_capture_analysis_result",
        indexes = @Index(
                name = "idx_seed_capture_analysis_result_analysis",
                columnList = "analysis_id"
        )
)
public class PersistentSeedCaptureAnalysisResultRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "analysis_id", nullable = false)
    private Long analysisId;

    @Column(name = "table_name", length = 255)
    private String tableName;

    @Column(name = "chunk_seq")
    private Integer chunkSeq;

    @Column(name = "result_type", nullable = false, length = 64)
    private String resultType;

    @Lob
    @Column(name = "summary_json")
    private String summaryJson;

    @Column(name = "relative_path", length = 1000)
    private String relativePath;

    @Column(name = "file_checksum", length = 128)
    private String fileChecksum;

    @Column(name = "row_count", nullable = false)
    private Long rowCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PersistentSeedCaptureAnalysisResultRecord() {
    }

    public PersistentSeedCaptureAnalysisResultRecord(
            long analysisId,
            String resultType,
            String summaryJson,
            String relativePath,
            String fileChecksum,
            long rowCount
    ) {
        this(analysisId, null, 0, resultType, summaryJson, relativePath, fileChecksum, rowCount);
    }

    public PersistentSeedCaptureAnalysisResultRecord(
            long analysisId,
            String tableName,
            int chunkSeq,
            String resultType,
            String summaryJson,
            String relativePath,
            String fileChecksum,
            long rowCount
    ) {
        Instant now = Instant.now();
        this.analysisId = analysisId;
        this.tableName = tableName;
        this.chunkSeq = chunkSeq;
        this.resultType = resultType;
        this.summaryJson = summaryJson;
        this.relativePath = relativePath;
        this.fileChecksum = fileChecksum;
        this.rowCount = rowCount;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getAnalysisId() {
        return analysisId;
    }

    public String getTableName() {
        return tableName;
    }

    public Integer getChunkSeq() {
        return chunkSeq;
    }

    public String getResultType() {
        return resultType;
    }

    public String getSummaryJson() {
        return summaryJson;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public String getFileChecksum() {
        return fileChecksum;
    }

    public Long getRowCount() {
        return rowCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
