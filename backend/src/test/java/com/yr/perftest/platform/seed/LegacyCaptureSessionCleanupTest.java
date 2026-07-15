package com.yr.perftest.platform.seed;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(LegacyCaptureSessionCleanup.class)
class LegacyCaptureSessionCleanupTest {
    @Autowired
    private PersistentSeedCaptureSessionRepository sessionRepository;

    @Autowired
    private LegacyCaptureSessionCleanup cleanup;

    @Test
    void removesLegacySessionsIdempotently() {
        sessionRepository.saveAndFlush(new PersistentSeedCaptureSessionRecord(
                1L,
                11L,
                "MYSQL",
                "[\"orders\"]",
                "[]",
                "[]",
                "{}"
        ));
        assertThat(sessionRepository.count()).isOne();

        cleanup.cleanup();

        assertThat(sessionRepository.count()).isZero();

        cleanup.cleanup();

        assertThat(sessionRepository.count()).isZero();
    }
}
