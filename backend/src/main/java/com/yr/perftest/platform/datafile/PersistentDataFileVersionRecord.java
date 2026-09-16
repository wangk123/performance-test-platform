package com.yr.perftest.platform.datafile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "data_file_versions")
public class PersistentDataFileVersionRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long dataFileId;

    @Column(nullable = false)
    private int versionNo;

    @Column(nullable = false, length = 255)
    private String originalFilename;

    @Column(nullable = false, length = 500)
    private String storedPath;

    @Column(nullable = false)
    private long sizeBytes;

    private Long rowCount;

    private String headerColumnsJson;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(length = 64)
    private String uploadedBy;

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    @Column(length = 500)
    private String remark;

    protected PersistentDataFileVersionRecord() {
    }

    PersistentDataFileVersionRecord(
            Long dataFileId,
            int versionNo,
            String originalFilename,
            String storedPath,
            long sizeBytes,
            Long rowCount,
            String headerColumnsJson,
            String sha256,
            String uploadedBy,
            LocalDateTime uploadedAt,
            String remark
    ) {
        this.dataFileId = dataFileId;
        this.versionNo = versionNo;
        this.originalFilename = originalFilename;
        this.storedPath = storedPath;
        this.sizeBytes = sizeBytes;
        this.rowCount = rowCount;
        this.headerColumnsJson = headerColumnsJson;
        this.sha256 = sha256;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
        this.remark = remark;
    }

    public Long getId() {
        return id;
    }

    public Long getDataFileId() {
        return dataFileId;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getStoredPath() {
        return storedPath;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public Long getRowCount() {
        return rowCount;
    }

    public String getHeaderColumnsJson() {
        return headerColumnsJson;
    }

    public String getSha256() {
        return sha256;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public String getRemark() {
        return remark;
    }
}
