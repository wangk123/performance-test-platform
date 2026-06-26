package com.yr.perftest.platform.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PersistentScenarioExecutionRepository extends JpaRepository<PersistentScenarioExecutionRecord, Long> {
    List<PersistentScenarioExecutionRecord> findAllByScenarioIdOrderByIdDesc(Long scenarioId);

    Optional<PersistentScenarioExecutionRecord> findFirstByScenarioIdOrderByIdDesc(Long scenarioId);

    void deleteAllByScenarioId(Long scenarioId);
}
