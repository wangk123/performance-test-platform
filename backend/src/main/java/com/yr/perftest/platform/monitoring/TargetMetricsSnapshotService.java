package com.yr.perftest.platform.monitoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class TargetMetricsSnapshotService {
    private final PersistentTargetMetricsSnapshotRepository repository;
    private final TargetMetricsService targetMetricsService;
    private final ObjectMapper objectMapper;

    public TargetMetricsSnapshotService(
            PersistentTargetMetricsSnapshotRepository repository,
            TargetMetricsService targetMetricsService,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.targetMetricsService = targetMetricsService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void captureAll(long executionId) {
        for (MetricKind kind : MetricKind.values()) {
            captureSilently(executionId, kind);
        }
    }

    @Transactional(readOnly = true)
    public Optional<TargetMetricsQueryResult> load(long executionId, MetricKind kind) {
        return repository.findByExecutionIdAndKind(executionId, kind.name())
                .flatMap(record -> deserialize(record, kind));
    }

    @Transactional
    public void deleteByExecutionId(long executionId) {
        repository.deleteByExecutionId(executionId);
    }

    private void captureSilently(long executionId, MetricKind kind) {
        try {
            TargetMetricsQueryResult result = targetMetricsService.querySeries(executionId, kind, null, null, null);
            if (result == null || result.series() == null || result.series().isEmpty()) {
                return;
            }
            String json = objectMapper.writeValueAsString(result.series());
            repository.findByExecutionIdAndKind(executionId, kind.name()).ifPresent(repository::delete);
            repository.save(new PersistentTargetMetricsSnapshotRecord(
                    executionId,
                    kind.name(),
                    result.unit(),
                    json,
                    Instant.now()
            ));
        } catch (Exception ignored) {
        }
    }

    private Optional<TargetMetricsQueryResult> deserialize(PersistentTargetMetricsSnapshotRecord record, MetricKind kind) {
        try {
            java.util.List<MetricSeries> series = objectMapper.readValue(
                    record.getSeriesJson(),
                    objectMapper.getTypeFactory().constructCollectionType(java.util.List.class, MetricSeries.class)
            );
            return Optional.of(new TargetMetricsQueryResult(kind, record.getUnit(), series));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }
}
