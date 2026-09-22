package com.yr.perftest.platform.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PersistentExecutionTraceSnapshotRepository extends JpaRepository<PersistentExecutionTraceSnapshotRecord, Long> {
    Optional<PersistentExecutionTraceSnapshotRecord> findByExecutionId(Long executionId);

    void deleteByExecutionId(Long executionId);
}
