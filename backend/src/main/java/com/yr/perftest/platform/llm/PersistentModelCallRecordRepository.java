package com.yr.perftest.platform.llm;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PersistentModelCallRecordRepository
        extends JpaRepository<PersistentModelCallRecord, Long>, JpaSpecificationExecutor<PersistentModelCallRecord> {
    Page<PersistentModelCallRecord> findAllByOrderByIdDesc(Pageable pageable);
}
