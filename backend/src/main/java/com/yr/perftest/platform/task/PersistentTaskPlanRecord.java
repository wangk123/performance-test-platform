package com.yr.perftest.platform.task;

import com.yr.perftest.platform.task.plandoc.PlanPhase;
import com.yr.perftest.platform.task.plandoc.PlanStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanPhase phase = PlanPhase.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanStatus status = PlanStatus.DRAFT;

    @Lob
    private String body;

    @Column(nullable = false)
    private int revision = 1;

    private Instant publishedAt;

    @Lob
    private String precheckJson;

    private Instant precheckExecutedAt;

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

    public PlanPhase getPhase() { return phase; }
    public PlanStatus getStatus() { return status; }
    public String getBody() { return body; }
    public int getRevision() { return revision; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getPrecheckJson() { return precheckJson; }
    public Instant getPrecheckExecutedAt() { return precheckExecutedAt; }

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

    /** 仅供测试与数据订正直接置状态；正常流转走 PlanWorkflowService。 */
    public void forceState(PlanPhase phase, PlanStatus status) {
        this.phase = phase;
        this.status = status;
        this.updatedAt = Instant.now();
    }

    /** 状态机流转写入（前置校验在 PlanWorkflowService）。 */
    public void transitionTo(PlanPhase phase, PlanStatus status) {
        this.phase = phase;
        this.status = status;
        this.updatedAt = Instant.now();
    }

    /** 文档原文变更统一入口：body 变化必然 revision+1（设计 §5.1）。 */
    public void updateBody(String body) {
        this.body = body;
        this.revision = this.revision + 1;
        this.updatedAt = Instant.now();
    }

    /** 仅创建时初始化正文：不 bump revision（首版 revision=1）。 */
    public void initializeBody(String body) {
        this.body = body;
    }

    /** 仅创建时初始化环境检查设置：不 bump revision。 */
    public void initializePrecheck(String precheckJson) {
        this.precheckJson = precheckJson;
    }

    /** 环境检查设置变更（非文档内容）：只改 precheckJson+updatedAt，不 bump revision（设计 §10.2）。 */
    public void updatePrecheckJson(String precheckJson) {
        this.precheckJson = precheckJson;
        this.updatedAt = Instant.now();
    }

    public void applyPublish(Instant publishedAt) {
        this.phase = PlanPhase.PUBLISH;
        this.status = PlanStatus.PUBLISHED;
        this.publishedAt = publishedAt;
        this.updatedAt = Instant.now();
    }

    public void applyNewRevision() {
        this.phase = PlanPhase.DRAFT;
        this.status = PlanStatus.DRAFT;
        this.revision = this.revision + 1;
        this.precheckExecutedAt = null;
        this.updatedAt = Instant.now();
    }

    public void markPrecheckExecuted(Instant at) {
        this.precheckExecutedAt = at;
        this.updatedAt = Instant.now();
    }
}
