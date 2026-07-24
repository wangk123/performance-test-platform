package com.yr.perftest.platform.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PersistentAuthTokenRepository extends JpaRepository<PersistentAuthTokenRecord, Long> {
    Optional<PersistentAuthTokenRecord> findByTokenHash(String tokenHash);
}
