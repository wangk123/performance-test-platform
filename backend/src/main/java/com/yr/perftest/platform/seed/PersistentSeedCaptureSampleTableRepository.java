package com.yr.perftest.platform.seed;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PersistentSeedCaptureSampleTableRepository
        extends JpaRepository<PersistentSeedCaptureSampleTableRecord, Long> {
    List<PersistentSeedCaptureSampleTableRecord> findBySampleIdOrderByTableNameAsc(long sampleId);

    Optional<PersistentSeedCaptureSampleTableRecord> findBySampleIdAndTableName(
            long sampleId,
            String tableName
    );
}
