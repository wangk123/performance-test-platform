package com.yr.perftest.platform.seed;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PersistentSeedCaptureSampleRepository
        extends JpaRepository<PersistentSeedCaptureSampleRecord, Long>,
        JpaSpecificationExecutor<PersistentSeedCaptureSampleRecord> {
    Optional<PersistentSeedCaptureSampleRecord> findByIdAndProjectId(long id, long projectId);

    List<PersistentSeedCaptureSampleRecord> findByStrategyIdOrderBySampleSeqAsc(long strategyId);

    List<PersistentSeedCaptureSampleRecord> findByDatasourceIdAndStatusInOrderByCaptureStartedAtDesc(
            long datasourceId,
            Set<String> statuses
    );

    List<PersistentSeedCaptureSampleRecord> findByStatusInAndHeartbeatAtBefore(
            Set<String> statuses,
            java.time.Instant heartbeatBefore
    );

    List<PersistentSeedCaptureSampleRecord> findByStrategyIdAndStatusInOrderByCaptureStartedAtAscSampleSeqAsc(
            long strategyId,
            Set<String> statuses
    );

    @Query("""
            select coalesce(max(sample.sampleSeq), 0) + 1
            from PersistentSeedCaptureSampleRecord sample
            where sample.strategyId = :strategyId
            """)
    Integer findNextSampleSeq(@Param("strategyId") long strategyId);
}
