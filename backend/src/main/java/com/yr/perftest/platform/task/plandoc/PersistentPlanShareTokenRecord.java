package com.yr.perftest.platform.task.plandoc;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "plan_share_tokens", uniqueConstraints = @UniqueConstraint(columnNames = {"token"}))
public class PersistentPlanShareTokenRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long planId;

    @Column(nullable = false, length = 64)
    private String token;

    private Instant expiresAt;

    private Instant revokedAt;

    @Column(nullable = false, length = 80)
    private String createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    protected PersistentPlanShareTokenRecord() {
    }

    public PersistentPlanShareTokenRecord(Long planId, String token, Instant expiresAt, String createdBy) {
        this.planId = planId;
        this.token = token;
        this.expiresAt = expiresAt;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getPlanId() { return planId; }
    public String getToken() { return token; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }

    public void revoke() {
        this.revokedAt = Instant.now();
    }

    void expireForTest(Instant at) {
        this.expiresAt = at;
    }
}
