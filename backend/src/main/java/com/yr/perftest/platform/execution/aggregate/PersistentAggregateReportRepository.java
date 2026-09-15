package com.yr.perftest.platform.execution.aggregate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PersistentAggregateReportRepository extends JpaRepository<PersistentAggregateReportRecord, Long> {
    Optional<PersistentAggregateReportRecord> findByExecutionId(Long executionId);

    List<PersistentAggregateReportRecord> findByExecutionIdIn(java.util.Collection<Long> executionIds);

    void deleteByExecutionId(Long executionId);
}
