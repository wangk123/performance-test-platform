package com.yr.perftest.platform.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "task_plans")
public class PersistentTaskPlanRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 1000)
    private String remark;

    private Long defaultControllerNodeId;

    @Lob
    private String defaultWorkerNodeIdsJson;

    @Lob
    private String defaultMonitorTargetIdsJson;

    @Column(nullable = false, length = 80)
    private String createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected PersistentTaskPlanRecord() {
    }

    public PersistentTaskPlanRecord(Long projectId, String name, String remark, String createdBy) {
        this.projectId = projectId;
        this.name = name;
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

    public String getName() {
        return name;
    }

    public String getRemark() {
        return remark;
    }

    public Long getDefaultControllerNodeId() {
        return defaultControllerNodeId;
    }

    public String getDefaultWorkerNodeIdsJson() {
        return defaultWorkerNodeIdsJson;
    }

    public String getDefaultMonitorTargetIdsJson() {
        return defaultMonitorTargetIdsJson;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateProfile(String name, String remark, Long defaultControllerNodeId, String workerJson, String monitorJson) {
        if (name != null && !name.trim().isEmpty()) {
            this.name = name.trim();
        }
        if (remark != null) {
            this.remark = remark;
        }
        this.defaultControllerNodeId = defaultControllerNodeId;
        this.defaultWorkerNodeIdsJson = workerJson;
        this.defaultMonitorTargetIdsJson = monitorJson;
        this.updatedAt = Instant.now();
    }
}
