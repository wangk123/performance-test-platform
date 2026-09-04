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

    protected PersistentPlanCommentRecord() {
    }

    public PersistentPlanCommentRecord(Long planId, String author, String content, PlanCommentKind kind) {
        this.planId = planId;
        this.author = author;
        this.content = content;
        this.kind = kind;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getPlanId() { return planId; }
    public String getAuthor() { return author; }
    public String getContent() { return content; }
    public PlanCommentKind getKind() { return kind; }
    public Instant getCreatedAt() { return createdAt; }
}
