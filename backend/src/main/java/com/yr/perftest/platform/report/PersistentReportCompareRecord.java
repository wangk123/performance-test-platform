package com.yr.perftest.platform.report;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 报告对比记录（模块 06 增强）：基线 vs 目标计划报告的指标差异留档。
 */
@Entity
@Table(name = "report_compares")
public class PersistentReportCompareRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long basePlanId;

    @Column(nullable = false)
    private Long targetPlanId;

    @Lob
    @Column(nullable = false, length = 65536)
    private String summaryJson;

    @Column(nullable = false, length = 64)
    private String createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    protected PersistentReportCompareRecord() {
    }

    public PersistentReportCompareRecord(
            Long basePlanId, Long targetPlanId, String summaryJson, String createdBy
    ) {
        this.basePlanId = basePlanId;
        this.targetPlanId = targetPlanId;
        this.summaryJson = summaryJson;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getBasePlanId() {
        return basePlanId;
    }

    public Long getTargetPlanId() {
        return targetPlanId;
    }

    public String getSummaryJson() {
        return summaryJson;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
