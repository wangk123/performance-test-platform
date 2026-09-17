package com.yr.perftest.platform.script;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "script_versions")
public class PersistentScriptVersionRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "script_id")
    private Long scriptId;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private Integer versionNo;

    @Column(nullable = false, length = 255)
    private String originalFilename;

    @Column(nullable = false, length = 1000)
    private String storedPath;

    @Column(nullable = false, length = 80)
    private String uploadedBy;

    @Column(nullable = false)
    private Instant uploadedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ScriptVersionStatus status;

    @Column(length = 512)
    private String remark;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected PersistentScriptVersionRecord() {
    }

    PersistentScriptVersionRecord(
            Long scriptId,
            Long projectId,
            Integer versionNo,
            String originalFilename,
            String storedPath,
            String uploadedBy,
            Instant uploadedAt,
            ScriptVersionStatus status,
            String remark
    ) {
        this.scriptId = scriptId;
        this.projectId = projectId;
        this.versionNo = versionNo;
        this.originalFilename = originalFilename;
        this.storedPath = storedPath;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
        this.status = status;
        this.remark = remark;
        this.updatedAt = uploadedAt;
    }

    ScriptVersion toScriptVersion() {
        return new ScriptVersion(
                id,
                projectId,
                scriptId,
                versionNo,
                originalFilename,
                storedPath,
                uploadedBy,
                uploadedAt,
                status.name(),
                remark
        );
    }

    void markPublished(int versionNo, String remark, String publishedBy, Instant publishedAt) {
        this.versionNo = versionNo;
        this.remark = remark;
        this.uploadedBy = publishedBy;
        this.uploadedAt = publishedAt;
        this.updatedAt = publishedAt;
        this.status = ScriptVersionStatus.PUBLISHED;
    }

    public Long getId() {
        return id;
    }

    public Long getScriptId() {
        return scriptId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getStoredPath() {
        return storedPath;
    }

    public ScriptVersionStatus getStatus() {
        return status;
    }

    public String getRemark() {
        return remark;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    void updateMetadata(String originalFilename, String uploadedBy, Instant uploadedAt) {
        this.originalFilename = originalFilename;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
        this.updatedAt = uploadedAt;
    }
}
