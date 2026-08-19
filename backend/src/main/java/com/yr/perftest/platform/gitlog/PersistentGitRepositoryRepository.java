package com.yr.perftest.platform.gitlog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PersistentGitRepositoryRepository extends JpaRepository<PersistentGitRepositoryRecord, Long> {
    List<PersistentGitRepositoryRecord> findAllByProjectIdOrderByIdDesc(Long projectId);
}
