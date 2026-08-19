package com.yr.perftest.platform.gitlog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PersistentLogArtifactRepository extends JpaRepository<PersistentLogArtifactRecord, Long> {
    List<PersistentLogArtifactRecord> findAllByExecutionIdOrderByIdDesc(Long executionId);
}
