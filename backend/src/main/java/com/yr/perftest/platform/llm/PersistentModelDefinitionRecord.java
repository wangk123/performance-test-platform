package com.yr.perftest.platform.llm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "model_definition", uniqueConstraints = @UniqueConstraint(columnNames = {"provider_id", "model_name"}))
public class PersistentModelDefinitionRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(name = "model_name", nullable = false, length = 200)
    private String modelName;

    @Column(length = 200)
    private String displayName;

    @Column(name = "api_type", nullable = false, length = 40)
    private String apiTypes;

    @Column(nullable = false)
    private Boolean enabled;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected PersistentModelDefinitionRecord() {
    }

    public PersistentModelDefinitionRecord(
            Long providerId,
            String modelName,
            String displayName,
            List<LlmApiType> apiTypes,
            boolean enabled
    ) {
        Instant now = Instant.now();
        this.providerId = providerId;
        this.modelName = modelName;
        this.displayName = displayName;
        this.apiTypes = LlmApiTypes.encode(apiTypes);
        this.enabled = enabled;
        this.isDefault = false;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String displayName, List<LlmApiType> apiTypes, boolean enabled) {
        this.displayName = displayName;
        this.apiTypes = LlmApiTypes.encode(apiTypes);
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }

    public void setApiTypes(List<LlmApiType> apiTypes) {
        this.apiTypes = LlmApiTypes.encode(apiTypes);
        this.updatedAt = Instant.now();
    }

    public void setDefault(boolean value) {
        this.isDefault = value;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getProviderId() {
        return providerId;
    }

    public String getModelName() {
        return modelName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<LlmApiType> getApiTypes() {
        return LlmApiTypes.decode(apiTypes);
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public Boolean getIsDefault() {
        return isDefault;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public LlmModel toModel() {
        List<LlmApiType> types = getApiTypes();
        return new LlmModel(
                id,
                providerId,
                modelName,
                displayName,
                types,
                types.get(0),
                Boolean.TRUE.equals(enabled),
                Boolean.TRUE.equals(isDefault),
                createdAt,
                updatedAt
        );
    }
}
