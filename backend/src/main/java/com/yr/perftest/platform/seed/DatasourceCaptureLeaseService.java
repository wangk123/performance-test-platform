package com.yr.perftest.platform.seed;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class DatasourceCaptureLeaseService {
    private final PersistentSeedCaptureDatasourceLeaseRepository leaseRepository;
    private final PersistentSeedCaptureSampleRepository sampleRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public DatasourceCaptureLeaseService(
            PersistentSeedCaptureDatasourceLeaseRepository leaseRepository,
            PersistentSeedCaptureSampleRepository sampleRepository
    ) {
        this.leaseRepository = leaseRepository;
        this.sampleRepository = sampleRepository;
    }

    public DatasourceCaptureLeaseService(
            PersistentSeedCaptureDatasourceLeaseRepository leaseRepository
    ) {
        this(leaseRepository, null);
    }

    @Transactional
    public synchronized DatasourceCaptureLease acquire(long datasourceId, long sampleId) {
        if (datasourceId < 0 || sampleId < 0) {
            throw new IllegalArgumentException("capture identifiers must not be negative");
        }
        Optional<PersistentSeedCaptureDatasourceLeaseRecord> current =
                leaseRepository.findByDatasourceId(datasourceId);
        if (current.isPresent()) {
            throw activeCapture(current.get());
        }
        if (sampleRepository != null) {
            Optional<PersistentSeedCaptureSampleRecord> activeSample = findActiveSample(datasourceId);
            if (activeSample.isPresent() && !activeSample.get().getId().equals(sampleId)) {
                throw new ActiveCaptureException(
                        "datasource " + datasourceId
                                + " already has an active capture sample " + activeSample.get().getId(),
                        activeSample.get().getId()
                );
            }
        }
        Instant now = Instant.now();
        try {
            return leaseRepository.saveAndFlush(
                    new PersistentSeedCaptureDatasourceLeaseRecord(datasourceId, sampleId, now)
            ).toLease();
        } catch (DataIntegrityViolationException ex) {
            throw new ActiveCaptureException(
                    "datasource " + datasourceId + " already has an active capture",
                    null,
                    ex
            );
        }
    }

    @Transactional(readOnly = true)
    public Optional<Long> findActiveSampleId(long datasourceId) {
        return leaseRepository.findByDatasourceId(datasourceId)
                .map(PersistentSeedCaptureDatasourceLeaseRecord::getSampleId);
    }

    @Transactional(readOnly = true)
    public Optional<PersistentSeedCaptureSampleRecord> findActiveSample(long datasourceId) {
        if (sampleRepository == null) {
            return Optional.empty();
        }
        return sampleRepository.findByDatasourceIdAndStatusInOrderByCaptureStartedAtDesc(
                        datasourceId,
                        Set.of("QUEUED", "PREPARING", "CAPTURING", "CANCEL_REQUESTED")
                ).stream()
                .findFirst();
    }

    @Transactional(readOnly = true)
    public List<DatasourceCaptureLease> findExpired(Instant heartbeatBefore) {
        return leaseRepository.findByHeartbeatAtBefore(heartbeatBefore).stream()
                .map(PersistentSeedCaptureDatasourceLeaseRecord::toLease)
                .toList();
    }

    @Transactional
    public void heartbeat(long datasourceId, long sampleId, Instant heartbeatAt) {
        PersistentSeedCaptureDatasourceLeaseRecord lease = leaseRepository.findByDatasourceId(datasourceId)
                .orElseThrow(() -> new IllegalStateException("capture lease not found for datasource " + datasourceId));
        if (lease.getSampleId() != sampleId) {
            throw new IllegalStateException("capture lease belongs to another sample");
        }
        lease.heartbeat(heartbeatAt == null ? Instant.now() : heartbeatAt);
        leaseRepository.save(lease);
    }

    @Transactional
    public void release(long datasourceId, long sampleId) {
        leaseRepository.deleteByDatasourceIdAndSampleId(datasourceId, sampleId);
    }

    @Transactional
    public void release(DatasourceCaptureLease lease) {
        release(lease.datasourceId(), lease.sampleId());
    }

    private static ActiveCaptureException activeCapture(
            PersistentSeedCaptureDatasourceLeaseRecord lease
    ) {
        return new ActiveCaptureException(
                "datasource " + lease.getDatasourceId()
                        + " already has an active capture sample " + lease.getSampleId(),
                lease.getSampleId()
        );
    }

    public static class ActiveCaptureException extends SeedValidationException {
        private final Long activeSampleId;

        public ActiveCaptureException(String message, Long activeSampleId) {
            super(message);
            this.activeSampleId = activeSampleId;
        }

        public ActiveCaptureException(String message, Long activeSampleId, Throwable cause) {
            super(message);
            this.activeSampleId = activeSampleId;
            initCause(cause);
        }

        public Long getActiveSampleId() {
            return activeSampleId;
        }
    }
}
