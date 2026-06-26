package com.yr.perftest.platform.execution.aggregate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PersistentAggregateReportRepository extends JpaRepository<PersistentAggregateReportRecord, Long> {
    Optional<PersistentAggregateReportRecord> findByExecutionId(Long executionId);

    void deleteByExecutionId(Long executionId);
}
