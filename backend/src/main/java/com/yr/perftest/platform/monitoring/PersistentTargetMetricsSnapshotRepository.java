package com.yr.perftest.platform.monitoring;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PersistentTargetMetricsSnapshotRepository extends JpaRepository<PersistentTargetMetricsSnapshotRecord, Long> {
    List<PersistentTargetMetricsSnapshotRecord> findAllByExecutionId(Long executionId);

    Optional<PersistentTargetMetricsSnapshotRecord> findByExecutionIdAndKind(Long executionId, String kind);

    void deleteByExecutionId(Long executionId);
}
