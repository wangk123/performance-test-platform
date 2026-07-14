package com.yr.perftest.platform.llm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "model_provider")
public class PersistentModelProviderRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String name;

    @Column(nullable = false, length = 500)
    private String baseUrl;

    @Column(length = 500)
    private String baseUrlAnthropic;

    @Column(nullable = false, length = 500)
    private String apiKey;

    @Column(nullable = false)
    private Boolean enabled;

    @Column(nullable = false)
    private Boolean storeBodyDefault;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected PersistentModelProviderRecord() {
    }

    public PersistentModelProviderRecord(
            String name,
            String baseUrl,
            String baseUrlAnthropic,
            String apiKey,
            boolean enabled,
            boolean storeBodyDefault
    ) {
        Instant now = Instant.now();
        this.name = name;
        this.baseUrl = baseUrl;
        this.baseUrlAnthropic = baseUrlAnthropic;
        this.apiKey = apiKey;
        this.enabled = enabled;
        this.storeBodyDefault = storeBodyDefault;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(
            String name,
            String baseUrl,
            String baseUrlAnthropic,
            String apiKeyOrNull,
            boolean enabled,
            boolean storeBodyDefault
    ) {
        this.name = name;
        this.baseUrl = baseUrl;
        this.baseUrlAnthropic = baseUrlAnthropic;
        if (apiKeyOrNull != null && !apiKeyOrNull.isBlank()) {
            this.apiKey = apiKeyOrNull;
        }
        this.enabled = enabled;
        this.storeBodyDefault = storeBodyDefault;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getBaseUrlAnthropic() {
        return baseUrlAnthropic;
    }

    public String getApiKey() {
        return apiKey;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public Boolean getStoreBodyDefault() {
        return storeBodyDefault;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public LlmProvider toProvider() {
        return new LlmProvider(
                id,
                name,
                baseUrl,
                blankToNull(baseUrlAnthropic),
                apiKey != null && !apiKey.isBlank(),
                Boolean.TRUE.equals(enabled),
                Boolean.TRUE.equals(storeBodyDefault),
                createdAt,
                updatedAt
        );
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
