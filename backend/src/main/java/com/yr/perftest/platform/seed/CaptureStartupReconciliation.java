package com.yr.perftest.platform.seed;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Component
public class CaptureStartupReconciliation implements ApplicationRunner {
    private static final Set<String> ACTIVE_STATUSES = Set.of(
            "QUEUED",
            "PREPARING",
            "CAPTURING",
            "CANCEL_REQUESTED"
    );
    private static final Set<String> ACTIVE_ANALYSIS_STATUSES = Set.of(
            "QUEUED",
            "VALIDATING",
            "DIFFING",
            "INFERRING",
            "PERSISTING",
            "CANCEL_REQUESTED"
    );

    private final PersistentSeedCaptureSampleRepository sampleRepository;
    private final DatasourceCaptureLeaseService leaseService;
    private final CaptureChunkStore chunkStore;
    private final Duration heartbeatTimeout;
    private final PersistentSeedCaptureAnalysisRepository analysisRepository;
    private final PersistentSeedCaptureAnalysisInputLockRepository inputLockRepository;

    @Autowired
    public CaptureStartupReconciliation(
            PersistentSeedCaptureSampleRepository sampleRepository,
            DatasourceCaptureLeaseService leaseService,
            CaptureChunkStore chunkStore,
            PersistentSeedCaptureAnalysisRepository analysisRepository,
            PersistentSeedCaptureAnalysisInputLockRepository inputLockRepository,
            @Value("${platform.seed.capture.heartbeat-timeout-seconds:300}") long heartbeatTimeoutSeconds
    ) {
        this(
                sampleRepository,
                leaseService,
                chunkStore,
                analysisRepository,
                inputLockRepository,
                Duration.ofSeconds(Math.max(1, heartbeatTimeoutSeconds))
        );
    }

    public CaptureStartupReconciliation(
            PersistentSeedCaptureSampleRepository sampleRepository,
            DatasourceCaptureLeaseService leaseService,
            CaptureChunkStore chunkStore,
            Duration heartbeatTimeout
    ) {
        this(
                sampleRepository,
                leaseService,
                chunkStore,
                null,
                null,
                heartbeatTimeout
        );
    }

    public CaptureStartupReconciliation(
            PersistentSeedCaptureSampleRepository sampleRepository,
            DatasourceCaptureLeaseService leaseService,
            CaptureChunkStore chunkStore,
            PersistentSeedCaptureAnalysisRepository analysisRepository,
            PersistentSeedCaptureAnalysisInputLockRepository inputLockRepository,
            Duration heartbeatTimeout
    ) {
        this.sampleRepository = sampleRepository;
        this.leaseService = leaseService;
        this.chunkStore = chunkStore;
        this.analysisRepository = analysisRepository;
        this.inputLockRepository = inputLockRepository;
        this.heartbeatTimeout = heartbeatTimeout;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        reconcile(Instant.now());
    }

    @Transactional
    public void reconcile(Instant now) {
        Instant deadline = now.minus(heartbeatTimeout);
        List<PersistentSeedCaptureSampleRecord> expired =
                sampleRepository.findByStatusInAndHeartbeatAtBefore(ACTIVE_STATUSES, deadline);
        for (PersistentSeedCaptureSampleRecord sample : expired) {
            sample.markInterrupted("capture heartbeat expired before startup");
            sampleRepository.saveAndFlush(sample);
            leaseService.release(sample.getDatasourceId(), sample.getId());
        }
        for (DatasourceCaptureLease lease : leaseService.findExpired(deadline)) {
            if (sampleRepository.findById(lease.sampleId())
                    .map(sample -> !CaptureSampleStateMachine.isActive(sample.getStatus()))
                    .orElse(true)) {
                leaseService.release(lease);
            }
        }
        reconcileAnalyses(deadline);
        chunkStore.cleanupTemporaryFiles();
    }

    private void reconcileAnalyses(Instant deadline) {
        if (analysisRepository == null || inputLockRepository == null) {
            return;
        }
        List<PersistentSeedCaptureAnalysisRecord> expired =
                analysisRepository.findByStatusInAndHeartbeatAtBefore(
                        ACTIVE_ANALYSIS_STATUSES,
                        deadline
                );
        for (PersistentSeedCaptureAnalysisRecord analysis : expired) {
            analysis.markInterrupted("analysis heartbeat expired before startup");
            analysisRepository.saveAndFlush(analysis);
            inputLockRepository.deleteByAnalysisId(analysis.getId());
            inputLockRepository.flush();
        }
    }
}
