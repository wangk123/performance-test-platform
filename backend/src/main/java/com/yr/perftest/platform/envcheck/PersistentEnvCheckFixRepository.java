package com.yr.perftest.platform.envcheck;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PersistentEnvCheckFixRepository extends JpaRepository<PersistentEnvCheckFixRecord, Long> {
    List<PersistentEnvCheckFixRecord> findByRunIdOrderByIdDesc(Long runId);

    List<PersistentEnvCheckFixRecord> findByRunIdAndHostAndItemKey(Long runId, String host, String itemKey);
}
