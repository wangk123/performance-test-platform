package com.yr.perftest.platform.seed;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface PersistentSeedCaptureAnalysisResultRepository
        extends JpaRepository<PersistentSeedCaptureAnalysisResultRecord, Long> {
    List<PersistentSeedCaptureAnalysisResultRecord> findByAnalysisIdOrderByIdAsc(long analysisId);

    List<PersistentSeedCaptureAnalysisResultRecord> findByAnalysisIdAndTableNameOrderByChunkSeqAsc(
            long analysisId,
            String tableName
    );

    @Transactional
    void deleteByAnalysisId(long analysisId);
}
