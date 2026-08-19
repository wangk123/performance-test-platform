package com.yr.perftest.platform.gitlog;

import com.yr.perftest.platform.execution.ExecutionValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Git 仓库管理（模块 10）：仓库配置与提交记录导入（JGit）。
 */
@Service
public class GitRepositoryService {
    private final PersistentGitRepositoryRepository repositoryRepository;
    private final PersistentGitCommitRepository commitRepository;
    private final GitCommitImporter commitImporter;

    public GitRepositoryService(
            PersistentGitRepositoryRepository repositoryRepository,
            PersistentGitCommitRepository commitRepository,
            GitCommitImporter commitImporter
    ) {
        this.repositoryRepository = repositoryRepository;
        this.commitRepository = commitRepository;
        this.commitImporter = commitImporter;
    }

    @Transactional
    public RepositoryView createRepository(
            long projectId, String name, String url, String authType, String credential, String createdBy
    ) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("git repository name is required");
        }
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("git repository url is required");
        }
        PersistentGitRepositoryRecord record = repositoryRepository.save(new PersistentGitRepositoryRecord(
                projectId,
                name.trim(),
                url.trim(),
                authType == null || authType.isBlank() ? "NONE" : authType.trim().toUpperCase(),
                credential,
                createdBy
        ));
        return view(record);
    }

    @Transactional(readOnly = true)
    public List<RepositoryView> listRepositories(long projectId) {
        return repositoryRepository.findAllByProjectIdOrderByIdDesc(projectId).stream()
                .map(this::view)
                .toList();
    }

    @Transactional
    public ImportResult importCommits(long repositoryId, String branch) {
        PersistentGitRepositoryRecord record = requireRepository(repositoryId);
        List<PersistentGitCommitRecord> imported = commitImporter.importCommits(record, branch);
        for (PersistentGitCommitRecord commit : imported) {
            commitRepository.findByRepositoryIdAndBranchAndCommitId(
                            repositoryId, branch, commit.getCommitId())
                    .ifPresentOrElse(ignored -> {
                    }, () -> commitRepository.save(commit));
        }
        List<PersistentGitCommitRecord> snapshots =
                commitRepository.findAllByRepositoryIdAndBranchOrderByAuthorTimeDesc(repositoryId, branch);
        return new ImportResult(repositoryId, branch, snapshots.size(),
                snapshots.isEmpty() ? null : snapshotView(snapshots.get(0)));
    }

    @Transactional(readOnly = true)
    public List<CommitView> listCommits(long repositoryId, String branch) {
        requireRepository(repositoryId);
        return commitRepository.findAllByRepositoryIdAndBranchOrderByAuthorTimeDesc(repositoryId, branch)
                .stream()
                .map(this::snapshotView)
                .toList();
    }

    private RepositoryView view(PersistentGitRepositoryRecord record) {
        return new RepositoryView(
                record.getId(),
                record.getProjectId(),
                record.getName(),
                record.getUrl(),
                record.getAuthType(),
                record.getStatus(),
                record.getCreatedBy(),
                record.getCreatedAt()
        );
    }

    private CommitView snapshotView(PersistentGitCommitRecord record) {
        return new CommitView(
                record.getId(),
                record.getRepositoryId(),
                record.getBranch(),
                record.getCommitId(),
                record.getMessage(),
                record.getAuthor(),
                record.getAuthorTime()
        );
    }

    private PersistentGitRepositoryRecord requireRepository(long repositoryId) {
        return repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new ExecutionValidationException(
                        "git repository " + repositoryId + " does not exist"));
    }

    public record RepositoryView(
            long repositoryId,
            long projectId,
            String name,
            String url,
            String authType,
            String status,
            String createdBy,
            Instant createdAt
    ) {
    }

    public record CommitView(
            long commitId,
            long repositoryId,
            String branch,
            String commitHash,
            String message,
            String author,
            Instant authorTime
    ) {
    }

    public record ImportResult(long repositoryId, String branch, int commitCount, CommitView latest) {
    }
}
