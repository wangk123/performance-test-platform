package com.yr.perftest.platform.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PersistentUserAccountRepository extends JpaRepository<PersistentUserAccountRecord, String> {
    Optional<PersistentUserAccountRecord> findByUsername(String username);
}
