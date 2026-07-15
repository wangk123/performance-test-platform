package com.yr.perftest.platform.seed;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface PersistentSeedCaptureAnalysisInputLockRepository
        extends JpaRepository<PersistentSeedCaptureAnalysisInputLockRecord, Long> {
    Optional<PersistentSeedCaptureAnalysisInputLockRecord> findBySampleId(long sampleId);

    boolean existsBySampleId(long sampleId);

    List<PersistentSeedCaptureAnalysisInputLockRecord> findByAnalysisId(long analysisId);

    @Transactional
    void deleteByAnalysisId(long analysisId);
}
