package com.yr.perftest.platform.auxscript;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 辅助脚本版本（模块 09）：不可变，任务绑定的是版本号而非可变脚本主记录。
 */
@Entity
@Table(name = "aux_script_versions")
public class PersistentAuxScriptVersionRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long scriptId;

    @Column(nullable = false)
    private int versionNo;

    @Lob
    @Column(nullable = false)
    private String sourceCode;

    @Column(length = 512)
    private String remark;

    @Column(nullable = false, length = 64)
    private String createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    protected PersistentAuxScriptVersionRecord() {
    }

    public PersistentAuxScriptVersionRecord(
            Long scriptId,
            int versionNo,
            String sourceCode,
            String remark,
            String createdBy
    ) {
        this.scriptId = scriptId;
        this.versionNo = versionNo;
        this.sourceCode = sourceCode;
        this.remark = remark;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getScriptId() {
        return scriptId;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public String getRemark() {
        return remark;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
