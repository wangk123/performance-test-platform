package com.yr.perftest.platform.execution;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PersistentIdempotencyRepository extends JpaRepository<PersistentIdempotencyRecord, Long> {
    Optional<PersistentIdempotencyRecord> findByIdemKey(String idemKey);
}
