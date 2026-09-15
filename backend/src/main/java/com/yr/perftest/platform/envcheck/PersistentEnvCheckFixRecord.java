package com.yr.perftest.platform.envcheck;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "env_check_fix")
public class PersistentEnvCheckFixRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long runId;

    @Column(nullable = false)
    private String host;

    @Column(nullable = false, length = 64)
    private String itemKey;

    /** LOW / MEDIUM / HIGH */
    @Column(nullable = false, length = 10)
    private String riskLevel;

    /** 备份标识（目标机备份路径或旧值） */
    @Column(nullable = false, length = 512)
    private String backupRef;

    /** 改动差异（参数：旧→新；文件：统一 diff） */
    @Lob
    private String diffText;

    @Column(length = 512)
    private String summary;

    @Column(nullable = false, length = 64)
    private String appliedBy;

    @Column(nullable = false)
    private Instant appliedAt;

    private Instant rolledBackAt;

    protected PersistentEnvCheckFixRecord() {
    }

    public PersistentEnvCheckFixRecord(Long runId, String host, String itemKey, String riskLevel,
                                       String backupRef, String diffText, String summary, String appliedBy) {
        this.runId = runId;
        this.host = host;
        this.itemKey = itemKey;
        this.riskLevel = riskLevel;
        this.backupRef = backupRef;
        this.diffText = diffText;
        this.summary = summary;
        this.appliedBy = appliedBy;
        this.appliedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getRunId() {
        return runId;
    }

    public String getHost() {
        return host;
    }

    public String getItemKey() {
        return itemKey;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public String getBackupRef() {
        return backupRef;
    }

    public String getDiffText() {
        return diffText;
    }

    public String getSummary() {
        return summary;
    }

    public String getAppliedBy() {
        return appliedBy;
    }

    public Instant getAppliedAt() {
        return appliedAt;
    }

    public Instant getRolledBackAt() {
        return rolledBackAt;
    }

    public void markRolledBack(Instant at) {
        this.rolledBackAt = at;
    }
}
