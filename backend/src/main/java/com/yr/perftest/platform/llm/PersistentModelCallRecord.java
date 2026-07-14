package com.yr.perftest.platform.llm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "model_call_record")
public class PersistentModelCallRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long providerId;

    private Long modelId;

    @Column(length = 120)
    private String providerNameSnapshot;

    @Column(length = 200)
    private String modelNameSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private LlmApiType apiType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private LlmCallScene scene;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private LlmCallStatus status;

    private Long latencyMs;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    @Column(length = 2000)
    private String errorMessage;

    @Lob
    private String requestBody;

    @Lob
    private String responseBody;

    @Column(length = 80)
    private String triggeredBy;

    @Column(nullable = false)
    private Instant createdAt;

    protected PersistentModelCallRecord() {
    }

    public PersistentModelCallRecord(
            Long providerId,
            Long modelId,
            String providerNameSnapshot,
            String modelNameSnapshot,
            LlmApiType apiType,
            LlmCallScene scene,
            LlmCallStatus status,
            Long latencyMs,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            String errorMessage,
            String requestBody,
            String responseBody,
            String triggeredBy
    ) {
        this.providerId = providerId;
        this.modelId = modelId;
        this.providerNameSnapshot = providerNameSnapshot;
        this.modelNameSnapshot = modelNameSnapshot;
        this.apiType = apiType;
        this.scene = scene;
        this.status = status;
        this.latencyMs = latencyMs;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.errorMessage = errorMessage;
        this.requestBody = requestBody;
        this.responseBody = responseBody;
        this.triggeredBy = triggeredBy;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getProviderId() {
        return providerId;
    }

    public Long getModelId() {
        return modelId;
    }

    public String getProviderNameSnapshot() {
        return providerNameSnapshot;
    }

    public String getModelNameSnapshot() {
        return modelNameSnapshot;
    }

    public LlmApiType getApiType() {
        return apiType;
    }

    public LlmCallScene getScene() {
        return scene;
    }

    public LlmCallStatus getStatus() {
        return status;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public Integer getTotalTokens() {
        return totalTokens;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public String getTriggeredBy() {
        return triggeredBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public LlmCallRecord toCallRecord() {
        return new LlmCallRecord(
                id,
                providerId,
                modelId,
                providerNameSnapshot,
                modelNameSnapshot,
                apiType,
                scene,
                status,
                latencyMs,
                promptTokens,
                completionTokens,
                totalTokens,
                errorMessage,
                requestBody,
                responseBody,
                triggeredBy,
                createdAt
        );
    }
}
