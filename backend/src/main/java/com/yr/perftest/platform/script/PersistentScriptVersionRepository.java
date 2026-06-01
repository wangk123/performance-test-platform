package com.yr.perftest.platform.script;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PersistentScriptVersionRepository extends JpaRepository<PersistentScriptVersionRecord, Long> {
    int countByProjectId(Long projectId);

    List<PersistentScriptVersionRecord> findAllByProjectIdOrderByVersionNoDesc(Long projectId);
}
