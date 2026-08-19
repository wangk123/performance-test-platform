package com.yr.perftest.platform.governance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 执行审计记录（T10）：记录谁在何时对某执行发起了 START/STOP/CANCEL，与请求审计联合重建操作轨迹。
 */
@Entity
@Table(name = "execution_audit")
public class PersistentExecutionAuditRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long executionId;

    @Column(nullable = false, length = 16)
    private String action;

    @Column(nullable = false)
    private boolean replayed;

    @Column(nullable = false, length = 16)
    private String principalType;

    @Column(nullable = false, length = 128)
    private String principalName;

    @Column(nullable = false)
    private Instant createdAt;

    protected PersistentExecutionAuditRecord() {
    }

    public PersistentExecutionAuditRecord(
            Long executionId,
            String action,
            boolean replayed,
            String principalType,
            String principalName
    ) {
        this.executionId = executionId;
        this.action = action;
        this.replayed = replayed;
        this.principalType = principalType;
        this.principalName = principalName;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getExecutionId() {
        return executionId;
    }

    public String getAction() {
        return action;
    }

    public boolean isReplayed() {
        return replayed;
    }

    public String getPrincipalType() {
        return principalType;
    }

    public String getPrincipalName() {
        return principalName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
