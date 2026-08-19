package com.yr.perftest.platform.auxscript;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PersistentAuxScriptBindingRepository extends JpaRepository<PersistentAuxScriptBindingRecord, Long> {
    List<PersistentAuxScriptBindingRecord> findAllByScenarioIdOrderByPhaseAscSortOrderAsc(Long scenarioId);

    void deleteAllByScenarioId(Long scenarioId);
}
