package com.yr.perftest.platform.verification;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PersistentEvidenceCaptureRepository extends JpaRepository<PersistentEvidenceCaptureRecord, Long> {
}
