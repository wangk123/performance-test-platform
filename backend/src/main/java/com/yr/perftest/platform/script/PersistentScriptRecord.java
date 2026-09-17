package com.yr.perftest.platform.script;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "scripts")
public class PersistentScriptRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "latest_version_no", nullable = false)
    private int latestVersionNo;

    @Column(name = "created_by", length = 80)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PersistentScriptRecord() {
    }

    static PersistentScriptRecord persistentOf(
            Long projectId, String name, int latestVersionNo, String createdBy, Instant createdAt) {
        PersistentScriptRecord record = new PersistentScriptRecord();
        record.projectId = projectId;
        record.name = name;
        record.latestVersionNo = latestVersionNo;
        record.createdBy = createdBy;
        record.createdAt = createdAt;
        return record;
    }

    void bumpLatestVersionNo(int newNo) {
        if (newNo > latestVersionNo) {
            latestVersionNo = newNo;
        }
    }

    public Long getPersistentId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public String getName() {
        return name;
    }

    public int getLatestVersionNo() {
        return latestVersionNo;
    }
}
