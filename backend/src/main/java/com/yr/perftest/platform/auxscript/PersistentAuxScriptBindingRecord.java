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
 * 场景与辅助脚本版本绑定（模块 09）：前置/后置 + 失败策略 + 排序。
 */
@Entity
@Table(name = "aux_script_bindings")
public class PersistentAuxScriptBindingRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long scenarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private AuxScriptPhase phase;

    @Column(nullable = false)
    private Long scriptVersionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AuxScriptFailurePolicy failurePolicy;

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false, length = 64)
    private String createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    protected PersistentAuxScriptBindingRecord() {
    }

    public PersistentAuxScriptBindingRecord(
            Long scenarioId,
            AuxScriptPhase phase,
            Long scriptVersionId,
            AuxScriptFailurePolicy failurePolicy,
            int sortOrder,
            String createdBy
    ) {
        this.scenarioId = scenarioId;
        this.phase = phase;
        this.scriptVersionId = scriptVersionId;
        this.failurePolicy = failurePolicy;
        this.sortOrder = sortOrder;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getScenarioId() {
        return scenarioId;
    }

    public AuxScriptPhase getPhase() {
        return phase;
    }

    public Long getScriptVersionId() {
        return scriptVersionId;
    }

    public AuxScriptFailurePolicy getFailurePolicy() {
        return failurePolicy;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
