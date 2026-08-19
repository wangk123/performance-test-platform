package com.yr.perftest.platform.gitlog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Git 提交快照（模块 10）：仓库 × 分支 × 提交号唯一。
 */
@Entity
@Table(name = "git_commit_snapshots")
public class PersistentGitCommitRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long repositoryId;

    @Column(nullable = false, length = 128)
    private String branch;

    @Column(nullable = false, length = 64)
    private String commitId;

    @Column(length = 512)
    private String message;

    @Column(length = 128)
    private String author;

    @Column(nullable = false)
    private Instant authorTime;

    @Column(nullable = false)
    private Instant createdAt;

    protected PersistentGitCommitRecord() {
    }

    public PersistentGitCommitRecord(
            Long repositoryId,
            String branch,
            String commitId,
            String message,
            String author,
            Instant authorTime
    ) {
        this.repositoryId = repositoryId;
        this.branch = branch;
        this.commitId = commitId;
        this.message = message;
        this.author = author;
        this.authorTime = authorTime;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getRepositoryId() {
        return repositoryId;
    }

    public String getBranch() {
        return branch;
    }

    public String getCommitId() {
        return commitId;
    }

    public String getMessage() {
        return message;
    }

    public String getAuthor() {
        return author;
    }

    public Instant getAuthorTime() {
        return authorTime;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
