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
@Table(name = "task_scenarios")
public class PersistentTaskScenarioRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long planId;

    @Column(nullable = false)
    private Long scriptVersionId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private int threads;

    @Column(nullable = false)
    private int rampUp;

    @Column(nullable = false)
    private int duration;

    @Column(nullable = false)
    private int loops;

    @Lob
    @Column(nullable = false)
    private String jmeterPropertiesJson;

    private Long controllerNodeId;

    @Lob
    private String workerNodeIdsJson;

    @Lob
    private String monitorTargetIdsJson;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected PersistentTaskScenarioRecord() {
    }

    public PersistentTaskScenarioRecord(Long planId, Long scriptVersionId, String name, int sortOrder) {
        this.planId = planId;
        this.scriptVersionId = scriptVersionId;
        this.name = name;
        this.sortOrder = sortOrder;
        this.threads = 1;
        this.rampUp = 0;
        this.duration = 0;
        this.loops = 1;
        this.jmeterPropertiesJson = "{}";
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getPlanId() {
        return planId;
    }

    public Long getScriptVersionId() {
        return scriptVersionId;
    }

    public String getName() {
        return name;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public int getThreads() {
        return threads;
    }

    public int getRampUp() {
        return rampUp;
    }

    public int getDuration() {
        return duration;
    }

    public int getLoops() {
        return loops;
    }

    public String getJmeterPropertiesJson() {
        return jmeterPropertiesJson;
    }

    public Long getControllerNodeId() {
        return controllerNodeId;
    }

    public String getWorkerNodeIdsJson() {
        return workerNodeIdsJson;
    }

    public String getMonitorTargetIdsJson() {
        return monitorTargetIdsJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateProfile(
            String name,
            Long scriptVersionId,
            String jmeterPropertiesJson,
            Long controllerNodeId,
            String workerNodeIdsJson,
            String monitorTargetIdsJson
    ) {
        if (name != null && !name.trim().isEmpty()) {
            this.name = name.trim();
        }
        if (scriptVersionId != null) {
            this.scriptVersionId = scriptVersionId;
        }
        this.jmeterPropertiesJson = jmeterPropertiesJson;
        this.controllerNodeId = controllerNodeId;
        this.workerNodeIdsJson = workerNodeIdsJson;
        this.monitorTargetIdsJson = monitorTargetIdsJson;
        this.updatedAt = Instant.now();
    }
}
