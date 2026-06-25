package com.yr.perftest.platform.execution;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "task_executions")
public class PersistentTaskExecutionRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long taskId;

    @Lob
    @Column(nullable = false)
    private String configJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ExecutionStatus status;

    private Instant startTime;

    private Instant endTime;

    private Long durationMs;

    @Column(length = 1000)
    private String resultFilePath;

    @Column(length = 1000)
    private String logFilePath;

    @Column(length = 2000)
    private String errorMessage;

    private Integer exitCode;

    protected PersistentTaskExecutionRecord() {
    }

    public PersistentTaskExecutionRecord(Long taskId, String configJson) {
        this.taskId = taskId;
        this.configJson = configJson;
        this.status = ExecutionStatus.QUEUED;
    }

    public Long getId() {
        return id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public String getConfigJson() {
        return configJson;
    }

    public void updateConfig(String configJson) {
        this.configJson = configJson;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public String getResultFilePath() {
        return resultFilePath;
    }

    public String getLogFilePath() {
        return logFilePath;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void markRunning(String resultFilePath, String logFilePath) {
        this.status = ExecutionStatus.RUNNING;
        this.startTime = Instant.now();
        this.resultFilePath = resultFilePath;
        this.logFilePath = logFilePath;
    }

    public void markSuccess(int exitCode) {
        finish(ExecutionStatus.SUCCESS, exitCode, null);
    }

    public void markFailed(Integer exitCode, String errorMessage) {
        finish(ExecutionStatus.FAILED, exitCode, errorMessage);
    }

    private void finish(ExecutionStatus status, Integer exitCode, String errorMessage) {
        this.status = status;
        this.exitCode = exitCode;
        this.errorMessage = errorMessage;
        this.endTime = Instant.now();
        if (startTime != null) {
            this.durationMs = Duration.between(startTime, endTime).toMillis();
        }
    }
}
