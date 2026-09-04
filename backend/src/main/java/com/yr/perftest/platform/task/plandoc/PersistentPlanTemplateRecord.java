package com.yr.perftest.platform.task.plandoc;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "plan_templates")
public class PersistentPlanTemplateRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id")
    private Long projectId; // NULL = 内置

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 1000)
    private String description;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private boolean builtin;

    @Column(nullable = false, length = 80)
    private String createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected PersistentPlanTemplateRecord() {
    }

    public PersistentPlanTemplateRecord(Long projectId, String name, String description, String content, boolean builtin, String createdBy) {
        this.projectId = projectId;
        this.name = name;
        this.description = description;
        this.content = content;
        this.builtin = builtin;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getContent() { return content; }
    public boolean isBuiltin() { return builtin; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(String name, String description, String content) {
        this.name = name;
        this.description = description;
        this.content = content;
        this.updatedAt = Instant.now();
    }
}
