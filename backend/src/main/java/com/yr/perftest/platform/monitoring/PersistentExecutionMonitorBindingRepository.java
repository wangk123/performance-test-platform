package com.yr.perftest.platform.monitoring;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PersistentExecutionMonitorBindingRepository extends JpaRepository<PersistentExecutionMonitorBindingRecord, Long> {
    List<PersistentExecutionMonitorBindingRecord> findAllByExecutionIdOrderByIdAsc(Long executionId);

    void deleteAllByExecutionId(Long executionId);
}
