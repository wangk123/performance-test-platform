package com.yr.perftest.platform.script;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PersistentScriptRepository extends JpaRepository<PersistentScriptRecord, Long> {
    List<PersistentScriptRecord> findAllByProjectIdOrderByIdAsc(Long projectId);

    Optional<PersistentScriptRecord> findByIdAndProjectId(Long id, Long projectId);

    boolean existsByProjectIdAndName(Long projectId, String name);

    Optional<PersistentScriptRecord> findByProjectIdAndName(Long projectId, String name);
}
