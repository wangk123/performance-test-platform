package com.yr.perftest.platform.gitlog;

import com.yr.perftest.platform.execution.ExecutionValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 任务代码绑定（模块 10）：场景 ↔ 仓库/分支/提交号，可追溯。
 */
@Service
public class TaskCodeBindingService {
    private final PersistentTaskCodeBindingRepository bindingRepository;
    private final PersistentGitRepositoryRepository repositoryRepository;

    public TaskCodeBindingService(
            PersistentTaskCodeBindingRepository bindingRepository,
            PersistentGitRepositoryRepository repositoryRepository
    ) {
        this.bindingRepository = bindingRepository;
        this.repositoryRepository = repositoryRepository;
    }

    @Transactional
    public BindingView bind(
            long scenarioId, long repositoryId, String branch, String commitId, String remark, String createdBy
    ) {
        requireRepository(repositoryId);
        if (branch == null || branch.isBlank()) {
            throw new IllegalArgumentException("branch is required");
        }
        if (commitId == null || commitId.isBlank()) {
            throw new IllegalArgumentException("commit id is required");
        }
        PersistentTaskCodeBindingRecord record = bindingRepository.findByScenarioId(scenarioId)
                .map(existing -> {
                    existing.update(repositoryId, branch.trim(), commitId.trim(), remark, createdBy);
                    return existing;
                })
                .orElseGet(() -> new PersistentTaskCodeBindingRecord(
                        scenarioId, repositoryId, branch.trim(), commitId.trim(), remark, createdBy));
        return view(bindingRepository.save(record));
    }

    @Transactional(readOnly = true)
    public BindingView bindingForScenario(long scenarioId) {
        return bindingRepository.findByScenarioId(scenarioId)
                .map(this::view)
                .orElseThrow(() -> new ExecutionValidationException(
                        "code binding for scenario " + scenarioId + " does not exist"));
    }

    @Transactional(readOnly = true)
    public List<BindingView> bindingsForRepository(long repositoryId) {
        return bindingRepository.findAllByRepositoryIdOrderByIdDesc(repositoryId).stream()
                .map(this::view)
                .toList();
    }

    private BindingView view(PersistentTaskCodeBindingRecord record) {
        return new BindingView(
                record.getId(),
                record.getScenarioId(),
                record.getRepositoryId(),
                record.getBranch(),
                record.getCommitId(),
                record.getRemark(),
                record.getCreatedBy(),
                record.getCreatedAt()
        );
    }

    private void requireRepository(long repositoryId) {
        repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new ExecutionValidationException(
                        "git repository " + repositoryId + " does not exist"));
    }

    public record BindingView(
            long bindingId,
            long scenarioId,
            long repositoryId,
            String branch,
            String commitId,
            String remark,
            String createdBy,
            Instant createdAt
    ) {
    }
}
