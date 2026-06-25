package com.yr.perftest.platform.execution;

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
@Table(name = "test_tasks")
public class PersistentTestTaskRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private Long scriptVersionId;

    @Column(nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ExecutionStatus status;

    @Column(length = 1000)
    private String remark;

    @Column(nullable = false, length = 80)
    private String createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected PersistentTestTaskRecord() {
    }

    public PersistentTestTaskRecord(
            Long projectId,
            Long scriptVersionId,
            String name,
            String remark,
            String createdBy
    ) {
        this.projectId = projectId;
        this.scriptVersionId = scriptVersionId;
        this.name = name;
        this.status = ExecutionStatus.QUEUED;
        this.remark = remark;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public Long getScriptVersionId() {
        return scriptVersionId;
    }

    public String getName() {
        return name;
    }

    public ExecutionStatus getStatus() {
        return status;
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

    public void changeStatus(ExecutionStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public void updateProfile(String name, String remark) {
        if (name != null && !name.trim().isEmpty()) {
            this.name = name.trim();
        }
        if (remark != null) {
            this.remark = remark;
        }
        this.updatedAt = Instant.now();
    }
}
