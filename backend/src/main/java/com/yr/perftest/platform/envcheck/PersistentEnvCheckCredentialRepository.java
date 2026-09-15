package com.yr.perftest.platform.envcheck;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PersistentEnvCheckCredentialRepository extends JpaRepository<PersistentEnvCheckCredentialRecord, Long> {
    List<PersistentEnvCheckCredentialRecord> findByProjectIdOrderByHostAsc(Long projectId);

    Optional<PersistentEnvCheckCredentialRecord> findByProjectIdAndPlanIdAndHost(Long projectId, Long planId, String host);

    Optional<PersistentEnvCheckCredentialRecord> findByProjectIdAndPlanIdIsNullAndHost(Long projectId, String host);
}
