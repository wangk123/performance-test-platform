package com.yr.perftest.platform.execution.aggregate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PersistentExecutionMetricSeriesRepository extends JpaRepository<PersistentExecutionMetricSeriesRecord, Long> {
    List<PersistentExecutionMetricSeriesRecord> findByExecutionIdOrderByBucketTimeMsAsc(Long executionId);

    void deleteByExecutionId(Long executionId);
}
