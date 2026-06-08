package com.yr.perftest.platform.script;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PersistentScriptVersionRepository extends JpaRepository<PersistentScriptVersionRecord, Long> {
    int countByProjectId(Long projectId);

    boolean existsByIdAndProjectId(Long id, Long projectId);

    Optional<PersistentScriptVersionRecord> findByIdAndProjectId(Long id, Long projectId);

    List<PersistentScriptVersionRecord> findAllByProjectIdOrderByVersionNoDesc(Long projectId);
}
