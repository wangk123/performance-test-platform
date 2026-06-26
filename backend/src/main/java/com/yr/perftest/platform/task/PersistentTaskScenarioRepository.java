package com.yr.perftest.platform.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PersistentTaskScenarioRepository extends JpaRepository<PersistentTaskScenarioRecord, Long> {
    List<PersistentTaskScenarioRecord> findAllByPlanIdOrderBySortOrderAscIdAsc(Long planId);

    Optional<PersistentTaskScenarioRecord> findFirstByPlanIdOrderBySortOrderDescIdDesc(Long planId);

    void deleteAllByPlanId(Long planId);

    long countByPlanId(Long planId);
}
