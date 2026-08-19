package com.yr.perftest.platform.gitlog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Git 仓库配置（模块 10）：代码托管平台权限治理不在平台内，只存访问配置。
 */
@Entity
@Table(name = "git_repositories")
public class PersistentGitRepositoryRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 512)
    private String url;

    @Column(nullable = false, length = 16)
    private String authType;

    @Column(length = 256)
    private String credential;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(nullable = false, length = 64)
    private String createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    protected PersistentGitRepositoryRecord() {
    }

    public PersistentGitRepositoryRecord(
            Long projectId,
            String name,
            String url,
            String authType,
            String credential,
            String createdBy
    ) {
        this.projectId = projectId;
        this.name = name;
        this.url = url;
        this.authType = authType;
        this.credential = credential;
        this.status = "ACTIVE";
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public String getAuthType() {
        return authType;
    }

    public String getCredential() {
        return credential;
    }

    public String getStatus() {
        return status;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
