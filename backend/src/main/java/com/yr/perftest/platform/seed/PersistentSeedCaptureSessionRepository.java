package com.yr.perftest.platform.seed;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PersistentSeedCaptureSessionRepository extends JpaRepository<PersistentSeedCaptureSessionRecord, Long> {
    List<PersistentSeedCaptureSessionRecord> findByProjectIdOrderByIdDesc(long projectId);

    Optional<PersistentSeedCaptureSessionRecord> findByIdAndProjectId(long id, long projectId);
}
