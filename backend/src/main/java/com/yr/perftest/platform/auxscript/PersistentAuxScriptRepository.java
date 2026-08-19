package com.yr.perftest.platform.auxscript;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PersistentAuxScriptRepository extends JpaRepository<PersistentAuxScriptRecord, Long> {
    List<PersistentAuxScriptRecord> findAllByProjectIdOrderByIdDesc(Long projectId);

    List<PersistentAuxScriptRecord> findAllByScopeOrderByIdDesc(AuxScriptScope scope);
}
