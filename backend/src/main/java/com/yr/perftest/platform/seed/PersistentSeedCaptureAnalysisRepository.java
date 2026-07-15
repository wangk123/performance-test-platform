package com.yr.perftest.platform.seed;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PersistentSeedCaptureAnalysisRepository
        extends JpaRepository<PersistentSeedCaptureAnalysisRecord, Long> {
    List<PersistentSeedCaptureAnalysisRecord> findByProjectIdOrderByCreatedAtDesc(long projectId);

    Optional<PersistentSeedCaptureAnalysisRecord> findByIdAndProjectId(long id, long projectId);

    List<PersistentSeedCaptureAnalysisRecord> findByStrategyIdAndStatusInOrderByCreatedAtDesc(
            long strategyId,
            Set<String> statuses
    );

    List<PersistentSeedCaptureAnalysisRecord> findByStatusInAndHeartbeatAtBefore(
            Set<String> statuses,
            java.time.Instant heartbeatBefore
    );
}
