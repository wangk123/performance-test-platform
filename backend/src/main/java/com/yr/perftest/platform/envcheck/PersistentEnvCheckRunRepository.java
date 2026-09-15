package com.yr.perftest.platform.envcheck;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PersistentEnvCheckRunRepository extends JpaRepository<PersistentEnvCheckRunRecord, Long> {
    List<PersistentEnvCheckRunRecord> findTop50ByPlanIdOrderByStartedAtDesc(Long planId);

    Optional<PersistentEnvCheckRunRecord> findFirstByPlanIdOrderByStartedAtDesc(Long planId);
}
