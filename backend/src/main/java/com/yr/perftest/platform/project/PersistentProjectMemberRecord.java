package com.yr.perftest.platform.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "project_members",
        uniqueConstraints = @UniqueConstraint(columnNames = {"projectId", "username"})
)
public class PersistentProjectMemberRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false, length = 80)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ProjectRole role;

    protected PersistentProjectMemberRecord() {
    }

    public PersistentProjectMemberRecord(Long projectId, String username, ProjectRole role) {
        this.projectId = projectId;
        this.username = username;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public String getUsername() {
        return username;
    }

    public ProjectRole getRole() {
        return role;
    }
}
