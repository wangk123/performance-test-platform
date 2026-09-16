package com.yr.perftest.platform.datafile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "data_files")
public class PersistentDataFileRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 500)
    private String remark;

    @Column(length = 64)
    private String createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected PersistentDataFileRecord() {
    }

    PersistentDataFileRecord(Long projectId, String name, String remark, String createdBy, LocalDateTime createdAt) {
        this.projectId = projectId;
        this.name = name;
        this.remark = remark;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
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

    public String getCreatedBy() {
        return createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
