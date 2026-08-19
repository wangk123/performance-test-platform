package com.yr.perftest.platform.auxscript;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PersistentAuxScriptVersionRepository extends JpaRepository<PersistentAuxScriptVersionRecord, Long> {
    List<PersistentAuxScriptVersionRecord> findAllByScriptIdOrderByVersionNoAsc(Long scriptId);

    long countByScriptId(Long scriptId);
}
