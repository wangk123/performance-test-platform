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
@Table(name = "projects", uniqueConstraints = @UniqueConstraint(columnNames = "code"))
public class PersistentProjectRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, length = 80)
    private String ownerUsername;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ProjectStatus status;

    protected PersistentProjectRecord() {
    }

    public PersistentProjectRecord(String code, String name, String description, String ownerUsername) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.ownerUsername = ownerUsername;
        this.status = ProjectStatus.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    void update(String name, String description, String ownerUsername) {
        this.name = name;
        this.description = description;
        this.ownerUsername = ownerUsername;
    }

    void archive() {
        status = ProjectStatus.ARCHIVED;
    }

    void restore() {
        status = ProjectStatus.ACTIVE;
    }

    Project toProject() {
        Project project = new Project(id, code, name, description, ownerUsername);
        if (status == ProjectStatus.ARCHIVED) {
            project.archive();
        }
        return project;
    }
}
