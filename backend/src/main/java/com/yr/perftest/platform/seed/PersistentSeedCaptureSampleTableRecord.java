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
        name = "seed_capture_table",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_seed_capture_table_sample_name",
                columnNames = {"sample_id", "table_name"}
        ),
        indexes = @Index(
                name = "idx_seed_capture_table_sample_status",
                columnList = "sample_id, status"
        )
)
public class PersistentSeedCaptureSampleTableRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sample_id", nullable = false)
    private Long sampleId;

    @Column(name = "table_name", nullable = false, length = 255)
    private String tableName;

    @Lob
    @Column(name = "schema_json", nullable = false)
    private String schemaJson;

    @Column(name = "schema_hash", length = 128)
    private String schemaHash;

    @Column(name = "row_count", nullable = false)
    private Long rowCount;

    @Column(name = "content_hash", length = 128)
    private String contentHash;

    @Column(name = "risky_no_pk", nullable = false)
    private Boolean riskyNoPk;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(nullable = false)
    private Boolean incomplete;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PersistentSeedCaptureSampleTableRecord() {
    }

    public PersistentSeedCaptureSampleTableRecord(
            long sampleId,
            String tableName,
            String schemaJson,
            String schemaHash,
            long rowCount,
            String contentHash,
            boolean riskyNoPk,
            String status
    ) {
        Instant now = Instant.now();
        this.sampleId = sampleId;
        this.tableName = tableName;
        this.schemaJson = schemaJson;
        this.schemaHash = schemaHash;
        this.rowCount = rowCount;
        this.contentHash = contentHash;
        this.riskyNoPk = riskyNoPk;
        this.status = status;
        this.incomplete = false;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markIncomplete(String errorMessage) {
        this.incomplete = true;
        this.status = "FAILED";
        this.errorMessage = errorMessage;
        this.updatedAt = Instant.now();
    }

    public void recordBatch(long capturedRows, String contentHash) {
        this.rowCount = capturedRows;
        this.contentHash = contentHash;
        this.updatedAt = Instant.now();
    }

    public void markCompleted(long rowCount, String contentHash) {
        this.rowCount = rowCount;
        this.contentHash = contentHash;
        this.status = "SUCCEEDED";
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

    public String getSchemaJson() {
        return schemaJson;
    }

    public String getSchemaHash() {
        return schemaHash;
    }

    public Long getRowCount() {
        return rowCount;
    }

    public String getContentHash() {
        return contentHash;
    }

    public Boolean getRiskyNoPk() {
        return riskyNoPk;
    }

    public String getStatus() {
        return status;
    }

    public Boolean getIncomplete() {
        return incomplete;
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
}
