package com.yr.perftest.platform.seed;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PersistentSeedCaptureChunkRepository extends JpaRepository<PersistentSeedCaptureChunkRecord, Long> {
    List<PersistentSeedCaptureChunkRecord> findBySampleIdOrderByTableNameAscChunkSeqAsc(long sampleId);

    List<PersistentSeedCaptureChunkRecord> findBySampleIdAndTableNameOrderByChunkSeqAsc(
            long sampleId,
            String tableName
    );

    Optional<PersistentSeedCaptureChunkRecord> findBySampleIdAndTableNameAndChunkSeq(
            long sampleId,
            String tableName,
            int chunkSeq
    );
}
