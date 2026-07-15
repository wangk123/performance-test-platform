package com.yr.perftest.platform.seed;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PersistentSeedCaptureDatasourceLeaseRepository
        extends JpaRepository<PersistentSeedCaptureDatasourceLeaseRecord, Long> {
    Optional<PersistentSeedCaptureDatasourceLeaseRecord> findByDatasourceId(long datasourceId);

    List<PersistentSeedCaptureDatasourceLeaseRecord> findByHeartbeatAtBefore(Instant heartbeatBefore);

    void deleteByDatasourceIdAndSampleId(long datasourceId, long sampleId);
}
