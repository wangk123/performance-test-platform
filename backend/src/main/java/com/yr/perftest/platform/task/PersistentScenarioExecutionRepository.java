package com.yr.perftest.platform.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PersistentScenarioExecutionRepository extends JpaRepository<PersistentScenarioExecutionRecord, Long> {
    List<PersistentScenarioExecutionRecord> findAllByScenarioIdOrderByIdDesc(Long scenarioId);

    List<PersistentScenarioExecutionRecord> findAllByScenarioIdInOrderByIdAsc(java.util.Collection<Long> scenarioIds);

    boolean existsByScenarioId(Long scenarioId);

    boolean existsByScenarioIdAndStatusIn(Long scenarioId,
            java.util.List<com.yr.perftest.platform.execution.ExecutionStatus> statuses);

    Optional<PersistentScenarioExecutionRecord> findFirstByScenarioIdOrderByIdDesc(Long scenarioId);

    void deleteAllByScenarioId(Long scenarioId);

    long countByStatusIn(java.util.List<com.yr.perftest.platform.execution.ExecutionStatus> statuses);

    long countByScenarioIdInAndStatusIn(java.util.List<Long> scenarioIds,
            java.util.List<com.yr.perftest.platform.execution.ExecutionStatus> statuses);
}
