package com.yr.perftest.platform.gitlog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 日志制品（模块 10）：执行日志上传、定位与检索索引状态。
 */
@Entity
@Table(name = "log_artifacts")
public class PersistentLogArtifactRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long executionId;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(nullable = false, length = 512)
    private String filePath;

    @Column(nullable = false, length = 16)
    private String indexStatus;

    @Column(nullable = false, length = 64)
    private String uploadedBy;

    @Column(nullable = false)
    private Instant createdAt;

    protected PersistentLogArtifactRecord() {
    }

    public PersistentLogArtifactRecord(
            Long executionId,
            String fileName,
            String filePath,
            String uploadedBy
    ) {
        this.executionId = executionId;
        this.fileName = fileName;
        this.filePath = filePath;
        this.indexStatus = "STAGED";
        this.uploadedBy = uploadedBy;
        this.createdAt = Instant.now();
    }

    public void markIndexed() {
        this.indexStatus = "INDEXED";
    }

    public Long getId() {
        return id;
    }

    public Long getExecutionId() {
        return executionId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getIndexStatus() {
        return indexStatus;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
