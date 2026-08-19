package com.yr.perftest.platform.verification;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PersistentChangeRepository extends JpaRepository<PersistentChangeRecord, Long> {
}
