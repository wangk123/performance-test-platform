package com.yr.perftest.platform.execution;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PersistentTestTaskRepository extends JpaRepository<PersistentTestTaskRecord, Long> {
    List<PersistentTestTaskRecord> findAllByProjectIdOrderByIdDesc(Long projectId);
}
