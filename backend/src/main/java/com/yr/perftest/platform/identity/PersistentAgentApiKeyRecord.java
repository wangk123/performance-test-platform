package com.yr.perftest.platform.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "agent_api_keys", uniqueConstraints = @UniqueConstraint(columnNames = "key_hash"))
public class PersistentAgentApiKeyRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "key_hash", nullable = false, length = 64)
    private String keyHash;

    @Column(nullable = false, length = 16)
    private String prefix;

    @Column(length = 120)
    private String scope;

    private Instant expiresAt;

    private Instant revokedAt;

    @Column(nullable = false)
    private Instant createdAt;

    protected PersistentAgentApiKeyRecord() {
    }

    public PersistentAgentApiKeyRecord(
            String keyHash,
            String prefix,
            String scope,
            Instant expiresAt,
            Instant createdAt
    ) {
        this.keyHash = keyHash;
        this.prefix = prefix;
        this.scope = scope;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getScope() {
        return scope;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    void revokeAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }
}
