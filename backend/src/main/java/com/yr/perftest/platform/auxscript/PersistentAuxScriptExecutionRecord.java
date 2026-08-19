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
 * 辅助脚本执行记录（模块 09）：退出码、状态、日志定位、起止时间。
 */
@Entity
@Table(name = "aux_script_executions")
public class PersistentAuxScriptExecutionRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long executionId;

    @Column(nullable = false)
    private Long bindingId;

    @Column(nullable = false)
    private Long scriptVersionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private AuxScriptPhase phase;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AuxScriptExecutionStatus status;

    @Column(nullable = false)
    private Integer exitCode;

    @Column(length = 512)
    private String logPath;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant endedAt;

    protected PersistentAuxScriptExecutionRecord() {
    }

    public PersistentAuxScriptExecutionRecord(
            Long executionId,
            Long bindingId,
            Long scriptVersionId,
            AuxScriptPhase phase
    ) {
        this.executionId = executionId;
        this.bindingId = bindingId;
        this.scriptVersionId = scriptVersionId;
        this.phase = phase;
        this.status = AuxScriptExecutionStatus.SKIPPED;
        this.exitCode = -1;
        this.startedAt = Instant.now();
    }

    public void markRunning() {
        this.status = AuxScriptExecutionStatus.SKIPPED;
        this.startedAt = Instant.now();
    }

    public void markFinished(AuxScriptExecutionStatus status, int exitCode, String logPath) {
        this.status = status;
        this.exitCode = exitCode;
        this.logPath = logPath;
        this.endedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getExecutionId() {
        return executionId;
    }

    public Long getBindingId() {
        return bindingId;
    }

    public Long getScriptVersionId() {
        return scriptVersionId;
    }

    public AuxScriptPhase getPhase() {
        return phase;
    }

    public AuxScriptExecutionStatus getStatus() {
        return status;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public String getLogPath() {
        return logPath;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }
}
