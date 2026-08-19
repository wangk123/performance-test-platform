package com.yr.perftest.platform.gitlog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PersistentGitCommitRepository extends JpaRepository<PersistentGitCommitRecord, Long> {
    List<PersistentGitCommitRecord> findAllByRepositoryIdAndBranchOrderByAuthorTimeDesc(
            Long repositoryId, String branch);

    Optional<PersistentGitCommitRecord> findByRepositoryIdAndBranchAndCommitId(
            Long repositoryId, String branch, String commitId);
}
