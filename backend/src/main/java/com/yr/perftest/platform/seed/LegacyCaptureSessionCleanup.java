package com.yr.perftest.platform.seed;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LegacyCaptureSessionCleanup implements ApplicationRunner {
    private final PersistentSeedCaptureSessionRepository sessionRepository;

    public LegacyCaptureSessionCleanup(PersistentSeedCaptureSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        cleanup();
    }

    @Transactional
    public void cleanup() {
        sessionRepository.deleteAllInBatch();
    }
}
