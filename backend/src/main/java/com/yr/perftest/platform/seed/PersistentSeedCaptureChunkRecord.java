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
        name = "seed_capture_chunk",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_seed_capture_chunk_sample_table_seq",
                columnNames = {"sample_id", "table_name", "chunk_seq"}
        ),
        indexes = @Index(
                name = "idx_seed_capture_chunk_sample_table_status",
                columnList = "sample_id, table_name, status"
        )
)
public class PersistentSeedCaptureChunkRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sample_id", nullable = false)
    private Long sampleId;

    @Column(name = "table_name", nullable = false, length = 255)
    private String tableName;

    @Column(name = "chunk_seq", nullable = false)
    private Integer chunkSeq;

    @Column(name = "pk_range_start", length = 1000)
    private String pkRangeStart;

    @Column(name = "pk_range_end", length = 1000)
    private String pkRangeEnd;

    @Column(name = "row_count", nullable = false)
    private Long rowCount;

    @Column(name = "content_hash", length = 128)
    private String contentHash;

    @Column(name = "relative_path", length = 1000)
    private String relativePath;

    @Column(name = "file_checksum", length = 128)
    private String fileChecksum;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "byte_size", nullable = false)
    private Long byteSize;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PersistentSeedCaptureChunkRecord() {
    }

    public PersistentSeedCaptureChunkRecord(
            long sampleId,
            String tableName,
            int chunkSeq,
            String pkRangeStart,
            String pkRangeEnd,
            long rowCount,
            String contentHash,
            String relativePath,
            String fileChecksum,
            String status,
            long byteSize
    ) {
        Instant now = Instant.now();
        this.sampleId = sampleId;
        this.tableName = tableName;
        this.chunkSeq = chunkSeq;
        this.pkRangeStart = pkRangeStart;
        this.pkRangeEnd = pkRangeEnd;
        this.rowCount = rowCount;
        this.contentHash = contentHash;
        this.relativePath = relativePath;
        this.fileChecksum = fileChecksum;
        this.status = status;
        this.byteSize = byteSize;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markReady(String relativePath, String fileChecksum, long byteSize) {
        this.relativePath = relativePath;
        this.fileChecksum = fileChecksum;
        this.byteSize = byteSize;
        this.status = "READY";
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getSampleId() {
        return sampleId;
    }

    public String getTableName() {
        return tableName;
    }

    public Integer getChunkSeq() {
        return chunkSeq;
    }

    public String getPkRangeStart() {
        return pkRangeStart;
    }

    public String getPkRangeEnd() {
        return pkRangeEnd;
    }

    public Long getRowCount() {
        return rowCount;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public String getFileChecksum() {
        return fileChecksum;
    }

    public String getStatus() {
        return status;
    }

    public Long getByteSize() {
        return byteSize;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
