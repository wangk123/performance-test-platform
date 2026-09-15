package com.yr.perftest.platform.envcheck;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "env_check_run")
public class PersistentEnvCheckRunRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long planId;

    @Column(nullable = false, length = 64)
    private String triggeredBy;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant finishedAt;

    /** OK+FIXED 数 */
    @Column(nullable = false)
    private int passed = 0;

    /** WARNING 数 */
    @Column(nullable = false)
    private int warned = 0;

    /** 完整矩阵：targets[] + results[]{host,itemKey,state,detail,suggestion,method,risk,fixable} */
    @Lob
    private String detailJson;

    protected PersistentEnvCheckRunRecord() {
    }

    public PersistentEnvCheckRunRecord(Long planId, String triggeredBy) {
        this.planId = planId;
        this.triggeredBy = triggeredBy;
        this.startedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getPlanId() {
        return planId;
    }

    public String getTriggeredBy() {
        return triggeredBy;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public int getPassed() {
        return passed;
    }

    public int getWarned() {
        return warned;
    }

    public String getDetailJson() {
        return detailJson;
    }

    public void markFinished(int passed, int warned, String detailJson) {
        this.passed = passed;
        this.warned = warned;
        this.detailJson = detailJson;
        this.finishedAt = Instant.now();
    }
}
