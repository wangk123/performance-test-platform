package com.yr.perftest.platform.execution.distributed;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PersistentExecutionNodeRepository extends JpaRepository<PersistentExecutionNodeRecord, Long> {
    List<PersistentExecutionNodeRecord> findAllByOrderByIdDesc();
}
