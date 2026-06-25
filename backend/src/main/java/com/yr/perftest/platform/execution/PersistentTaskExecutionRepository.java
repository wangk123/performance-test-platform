package com.yr.perftest.platform.execution;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PersistentTaskExecutionRepository extends JpaRepository<PersistentTaskExecutionRecord, Long> {
    Optional<PersistentTaskExecutionRecord> findFirstByTaskIdOrderByIdDesc(Long taskId);

    List<PersistentTaskExecutionRecord> findAllByTaskId(Long taskId);

    void deleteAllByTaskId(Long taskId);
}
