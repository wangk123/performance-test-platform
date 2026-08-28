package com.yr.perftest.platform.governance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PersistentExecutionAuditRepository extends JpaRepository<PersistentExecutionAuditRecord, Long> {
    List<PersistentExecutionAuditRecord> findByExecutionIdOrderByIdDesc(long executionId);
}
