package com.yr.perftest.platform.execution;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "idempotency_keys")
public class PersistentIdempotencyRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 128)
    private String idemKey;

    @Column(nullable = false, length = 64)
    private String requestHash;

    @Column(nullable = false)
    private Long executionId;

    @Column(nullable = false)
    private Instant createdAt;

    protected PersistentIdempotencyRecord() {
    }

    public PersistentIdempotencyRecord(String idemKey, String requestHash, Long executionId) {
        this.idemKey = idemKey;
        this.requestHash = requestHash;
        this.executionId = executionId;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getIdemKey() {
        return idemKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public Long getExecutionId() {
        return executionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
