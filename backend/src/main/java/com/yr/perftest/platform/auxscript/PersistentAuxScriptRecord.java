package com.yr.perftest.platform.auxscript;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 辅助脚本主记录（模块 09）：项目私有或系统公共，脚本版本不可变。
 */
@Entity
@Table(name = "aux_scripts")
public class PersistentAuxScriptRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AuxScriptType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AuxScriptScope scope;

    @Column(length = 512)
    private String description;

    @Column(nullable = false, length = 64)
    private String createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    protected PersistentAuxScriptRecord() {
    }

    public PersistentAuxScriptRecord(
            Long projectId,
            String name,
            AuxScriptType type,
            AuxScriptScope scope,
            String description,
            String createdBy
    ) {
        this.projectId = projectId;
        this.name = name;
        this.type = type;
        this.scope = scope;
        this.description = description;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
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

    public AuxScriptType getType() {
        return type;
    }

    public AuxScriptScope getScope() {
        return scope;
    }

    public String getDescription() {
        return description;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
