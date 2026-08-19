package com.yr.perftest.platform.auxscript;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PersistentAuxScriptExecutionRepository extends JpaRepository<PersistentAuxScriptExecutionRecord, Long> {
    List<PersistentAuxScriptExecutionRecord> findAllByExecutionIdOrderByIdAsc(Long executionId);

    List<PersistentAuxScriptExecutionRecord> findAllByExecutionIdAndPhaseOrderByIdAsc(
            Long executionId, AuxScriptPhase phase);
}
