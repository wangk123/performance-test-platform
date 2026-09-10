package com.yr.perftest.platform.task.plandoc;

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
@Table(name = "plan_comments")
public class PersistentPlanCommentRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long planId;

    @Column(nullable = false, length = 80)
    private String author;

    @Lob
    @Column(nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanCommentKind kind;

    @Column(nullable = false)
    private Instant createdAt;

    // ---- 线程（spec §4）：NULL=根批注，非空=对根的回复（仅一层） ----
    @Column(name = "parent_id")
    private Long parentId;

    // ---- 锚点三元组（spec §4）：三字段同现同缺；锚定状态不落库、前端派生 ----
    @Column(name = "anchor_line")
    private Integer anchorLine;

    @Column(name = "anchor_text", length = 200)
    private String anchorText;

    @Column(name = "section_title", length = 64)
    private String sectionTitle;

    // ---- 创建时文档 revision（审计：意见针对哪版提出），服务端写入 ----
    @Column(name = "body_revision")
    private Long bodyRevision;

    // ---- 解决状态：语义上只作用于根批注（spec §3.5） ----
    @Column(nullable = false)
    private boolean resolved = false;

    @Column(name = "resolved_by", length = 80)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected PersistentPlanCommentRecord() {
    }

    /** 流转附言/系统记录/历史形态：无锚点、无线程。 */
    public PersistentPlanCommentRecord(Long planId, String author, String content, PlanCommentKind kind) {
        this(planId, author, content, kind, null, null, null, null, null);
    }

    /** 全参：根批注带锚点（parentId/锚点为 null 即无线程/无锚点形态），回复只带 parentId。 */
    public PersistentPlanCommentRecord(Long planId, String author, String content, PlanCommentKind kind,
                                       Long parentId, Integer anchorLine, String anchorText,
                                       String sectionTitle, Long bodyRevision) {
        this.planId = planId;
        this.author = author;
        this.content = content;
        this.kind = kind;
        this.parentId = parentId;
        this.anchorLine = anchorLine;
        this.anchorText = anchorText;
        this.sectionTitle = sectionTitle;
        this.bodyRevision = bodyRevision;
        this.createdAt = Instant.now();
    }

    /** 解决/重开（spec §3.5）：重开时清空解决人与时间。 */
    public void applyResolve(boolean resolved, String actor) {
        this.resolved = resolved;
        this.resolvedBy = resolved ? actor : null;
        this.resolvedAt = resolved ? Instant.now() : null;
    }

    public Long getId() { return id; }
    public Long getPlanId() { return planId; }
    public String getAuthor() { return author; }
    public String getContent() { return content; }
    public PlanCommentKind getKind() { return kind; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getParentId() { return parentId; }
    public Integer getAnchorLine() { return anchorLine; }
    public String getAnchorText() { return anchorText; }
    public String getSectionTitle() { return sectionTitle; }
    public Long getBodyRevision() { return bodyRevision; }
    public boolean isResolved() { return resolved; }
    public String getResolvedBy() { return resolvedBy; }
    public Instant getResolvedAt() { return resolvedAt; }
}
