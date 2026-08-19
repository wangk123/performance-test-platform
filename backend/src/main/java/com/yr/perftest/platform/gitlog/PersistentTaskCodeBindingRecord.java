package com.yr.perftest.platform.gitlog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 任务（场景）与代码版本绑定（模块 10）：测试资产 ↔ 代码变更追溯。
 */
@Entity
@Table(name = "task_code_bindings")
public class PersistentTaskCodeBindingRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long scenarioId;

    @Column(nullable = false)
    private Long repositoryId;

    @Column(nullable = false, length = 128)
    private String branch;

    @Column(nullable = false, length = 64)
    private String commitId;

    @Column(length = 512)
    private String remark;

    @Column(nullable = false, length = 64)
    private String createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    protected PersistentTaskCodeBindingRecord() {
    }

    public PersistentTaskCodeBindingRecord(
            Long scenarioId,
            Long repositoryId,
            String branch,
            String commitId,
            String remark,
            String createdBy
    ) {
        this.scenarioId = scenarioId;
        this.repositoryId = repositoryId;
        this.branch = branch;
        this.commitId = commitId;
        this.remark = remark;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public void update(Long repositoryId, String branch, String commitId, String remark, String createdBy) {
        this.repositoryId = repositoryId;
        this.branch = branch;
        this.commitId = commitId;
        this.remark = remark;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getScenarioId() {
        return scenarioId;
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

    public String getRemark() {
        return remark;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
