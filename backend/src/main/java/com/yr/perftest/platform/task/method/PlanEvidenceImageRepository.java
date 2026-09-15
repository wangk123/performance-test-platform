package com.yr.perftest.platform.task.method;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface PlanEvidenceImageRepository extends JpaRepository<PersistentPlanEvidenceImageRecord, Long> {
    List<PersistentPlanEvidenceImageRecord> findByScenarioIdOrderBySortOrderAscIdAsc(long scenarioId);

    List<PersistentPlanEvidenceImageRecord> findByScenarioIdInOrderBySortOrderAscIdAsc(java.util.Collection<Long> scenarioIds);

    List<PersistentPlanEvidenceImageRecord> findByExecutionIdIn(List<Long> executionIds);

    @Transactional
    void deleteByExecutionIdIn(List<Long> executionIds);
}
