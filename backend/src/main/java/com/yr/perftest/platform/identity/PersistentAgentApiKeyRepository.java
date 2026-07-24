package com.yr.perftest.platform.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PersistentAgentApiKeyRepository extends JpaRepository<PersistentAgentApiKeyRecord, Long> {
    Optional<PersistentAgentApiKeyRecord> findByKeyHash(String keyHash);
}
