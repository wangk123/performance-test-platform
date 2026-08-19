package com.yr.perftest.platform.gitlog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * AI 分析任务（模块 10）：保留输入数据、模型、Prompt 版本与输出结果，便于追溯。
 */
@Entity
@Table(name = "ai_analysis_jobs")
public class PersistentAiAnalysisJobRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long executionId;

    @Column(nullable = false)
    private Long modelId;

    @Column(nullable = false, length = 16)
    private String promptVersion;

    @Column(nullable = false, length = 16)
    private String status;

    @Lob
    @Column(length = 262144)
    private String inputFacts;

    @Lob
    @Column(length = 262144)
    private String result;

    @Column(nullable = false, length = 64)
    private String requestedBy;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant completedAt;

    protected PersistentAiAnalysisJobRecord() {
    }

    public PersistentAiAnalysisJobRecord(
            Long executionId,
            Long modelId,
            String promptVersion,
            String inputFacts,
            String requestedBy
    ) {
        this.executionId = executionId;
        this.modelId = modelId;
        this.promptVersion = promptVersion;
        this.status = "RUNNING";
        this.inputFacts = inputFacts;
        this.requestedBy = requestedBy;
        this.createdAt = Instant.now();
    }

    public void markSuccess(String result) {
        this.status = "SUCCESS";
        this.result = result;
        this.completedAt = Instant.now();
    }

    public void markFailed(String errorMessage) {
        this.status = "FAILED";
        this.result = errorMessage;
        this.completedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getExecutionId() {
        return executionId;
    }

    public Long getModelId() {
        return modelId;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public String getStatus() {
        return status;
    }

    public String getInputFacts() {
        return inputFacts;
    }

    public String getResult() {
        return result;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
