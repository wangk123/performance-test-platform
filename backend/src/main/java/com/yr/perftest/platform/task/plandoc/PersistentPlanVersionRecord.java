package com.yr.perftest.platform.task.plandoc;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/** 计划版本：发布时冻结的正文快照 + 修订记录（spec 2026-09-10 §2、§9）。 */
@Entity
@Table(name = "plan_versions", uniqueConstraints = @UniqueConstraint(columnNames = {"planId", "versionNo"}))
public class PersistentPlanVersionRecord {
    /** 来源：手动发版 / 报告发布（工作流 publish 转换登记）。 */
    public static final String KIND_MANUAL = "MANUAL";
    public static final String KIND_PUBLISH = "PUBLISH";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long planId;

    @Column(nullable = false, length = 32)
    private String versionNo;

    @Column(nullable = false, length = 1000)
    private String changeNote;

    @Column(nullable = false, length = 80)
    private String createdBy;

    @Column(nullable = false, length = 80)
    private String author;

    @Lob
    @Column(nullable = false)
    private String snapshotBody;

    @Column(nullable = false, length = 20)
    private String planPhase;

    @Column(nullable = false)
    private int planRevision;

    @Column(nullable = false, length = 20)
    private String kind;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected PersistentPlanVersionRecord() {
    }

    public PersistentPlanVersionRecord(Long planId, String versionNo, String changeNote, String createdBy,
                                       String author, String snapshotBody, String planPhase, int planRevision,
                                       Instant createdAt) {
        this(planId, versionNo, changeNote, createdBy, author, snapshotBody, planPhase, planRevision,
                KIND_MANUAL, createdAt);
    }

    public PersistentPlanVersionRecord(Long planId, String versionNo, String changeNote, String createdBy,
                                       String author, String snapshotBody, String planPhase, int planRevision,
                                       String kind, Instant createdAt) {
        this.planId = planId;
        this.versionNo = versionNo;
        this.changeNote = changeNote;
        this.createdBy = createdBy;
        this.author = author;
        this.snapshotBody = snapshotBody;
        this.planPhase = planPhase;
        this.planRevision = planRevision;
        this.kind = kind;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    /** 覆盖发布（版本号不变的小修复）：刷新正文快照与修订元数据，createdBy/createdAt/kind 不变。 */
    public void applyOverwrite(String snapshotBody, String changeNote, String author, String planPhase,
                               int planRevision, Instant updatedAt) {
        this.snapshotBody = snapshotBody;
        this.changeNote = changeNote;
        this.author = author;
        this.planPhase = planPhase;
        this.planRevision = planRevision;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public Long getPlanId() { return planId; }
    public String getVersionNo() { return versionNo; }
    public String getChangeNote() { return changeNote; }
    public String getCreatedBy() { return createdBy; }
    public String getAuthor() { return author; }
    public String getSnapshotBody() { return snapshotBody; }
    public String getPlanPhase() { return planPhase; }
    public int getPlanRevision() { return planRevision; }
    public String getKind() { return kind; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
