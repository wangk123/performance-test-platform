package com.yr.perftest.platform.governance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 请求审计记录（T10）：按请求粒度留存 agent 面访问轨迹，可与执行审计合并重建平台侧操作轨迹。
 */
@Entity
@Table(name = "request_audit")
public class PersistentRequestAuditRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 36)
    private String requestId;

    @Column(nullable = false, length = 16)
    private String principalType;

    @Column(nullable = false, length = 128)
    private String principalName;

    @Column(nullable = false, length = 16)
    private String method;

    @Column(nullable = false, length = 512)
    private String path;

    @Column(length = 1024)
    private String query;

    @Column(nullable = false)
    private int statusCode;

    @Column(nullable = false)
    private long durationMs;

    @Column(nullable = false)
    private Instant createdAt;

    protected PersistentRequestAuditRecord() {
    }

    public PersistentRequestAuditRecord(
            String requestId,
            String principalType,
            String principalName,
            String method,
            String path,
            String query,
            int statusCode,
            long durationMs
    ) {
        this.requestId = requestId;
        this.principalType = principalType;
        this.principalName = principalName;
        this.method = method;
        this.path = path;
        this.query = query;
        this.statusCode = statusCode;
        this.durationMs = durationMs;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getPrincipalType() {
        return principalType;
    }

    public String getPrincipalName() {
        return principalName;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public String getQuery() {
        return query;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
