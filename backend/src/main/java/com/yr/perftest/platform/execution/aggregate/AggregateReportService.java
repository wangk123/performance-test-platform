package com.yr.perftest.platform.execution.aggregate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.execution.TaskExecutionResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class AggregateReportService {
    public static final String ACCURACY_FINAL = "final";
    public static final String ACCURACY_FINAL_PARTIAL = "final_partial";
    public static final String ACCURACY_LIVE = "live";

    private final PersistentAggregateReportRepository repository;
    private final ExecutionMetricSeriesService metricSeriesService;
    private final com.yr.perftest.platform.execution.ExecutionEventBroadcaster broadcaster;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<Long, LatestSnapshot> liveSnapshots = new ConcurrentHashMap<>();

    public AggregateReportService(
            PersistentAggregateReportRepository repository,
            ExecutionMetricSeriesService metricSeriesService,
            com.yr.perftest.platform.execution.ExecutionEventBroadcaster broadcaster,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.metricSeriesService = metricSeriesService;
        this.broadcaster = broadcaster;
        this.objectMapper = objectMapper;
    }

    public MetricTick cacheLive(long executionId, List<byte[]> snapshots, long snapshotMtime) {
        if (snapshots == null || snapshots.isEmpty()) {
            return null;
        }
        try {
            AggregateSnapshotCodec.ParsedSnapshot parsed = AggregateSnapshotCodec.decodeAndMerge(snapshots);
            byte[] primary = snapshots.get(0);
            liveSnapshots.put(executionId, new LatestSnapshot(primary, snapshotMtime, parsed, Instant.now()));
            return metricSeriesService.recordSnapshot(executionId, parsed);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Transactional
    public void persistFinal(long executionId, double durationSeconds, boolean partial) {
        LatestSnapshot latest = liveSnapshots.get(executionId);
        if (latest == null || latest.snapshotBytes() == null || latest.snapshotBytes().length == 0) {
            return;
        }
        String accuracy = partial ? ACCURACY_FINAL_PARTIAL : ACCURACY_FINAL;
        try {
            TaskExecutionResult result = AggregateSnapshotCodec.toResult(
                    latest.parsed(),
                    durationSeconds,
                    accuracy
            );
            String summaryJson = objectMapper.writeValueAsString(result.summary());
            String rowsJson = objectMapper.writeValueAsString(result.aggregateRows());
            repository.deleteByExecutionId(executionId);
            repository.save(new PersistentAggregateReportRecord(
                    executionId,
                    accuracy,
                    latest.parsed().startMs(),
                    latest.parsed().endMs(),
                    durationSeconds,
                    summaryJson,
                    rowsJson,
                    latest.snapshotBytes(),
                    Instant.now(),
                    AggregateSnapshotCodec.BUILDER_VERSION
            ));
        } catch (Exception ignored) {
        }
    }

    public Optional<TaskExecutionResult> loadPersisted(long executionId) {
        return repository.findByExecutionId(executionId).flatMap(this::toResult);
    }

    public Optional<TaskExecutionResult> loadLive(long executionId, double durationSeconds) {
        LatestSnapshot latest = liveSnapshots.get(executionId);
        if (latest == null) {
            return Optional.empty();
        }
        return Optional.of(AggregateSnapshotCodec.toResult(latest.parsed(), durationSeconds, ACCURACY_LIVE));
    }

    public long lastSnapshotMtime(long executionId) {
        LatestSnapshot latest = liveSnapshots.get(executionId);
        return latest == null ? 0L : latest.snapshotMtime();
    }

    public void clearLive(long executionId) {
        liveSnapshots.remove(executionId);
        metricSeriesService.clearLive(executionId);
    }

    @Transactional
    public void deleteByExecutionId(long executionId) {
        repository.deleteByExecutionId(executionId);
        metricSeriesService.deleteByExecutionId(executionId);
        clearLive(executionId);
    }

    private Optional<TaskExecutionResult> toResult(PersistentAggregateReportRecord record) {
        try {
            TaskExecutionResult.Summary summary = objectMapper.readValue(
                    record.getSummaryJson(),
                    TaskExecutionResult.Summary.class
            );
            List<TaskExecutionResult.AggregateRow> rows = objectMapper.readValue(
                    record.getRowsJson(),
                    new TypeReference<>() {
                    }
            );
            return Optional.of(new TaskExecutionResult(summary, rows, List.of()));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private record LatestSnapshot(
            byte[] snapshotBytes,
            long snapshotMtime,
            AggregateSnapshotCodec.ParsedSnapshot parsed,
            Instant cachedAt
    ) {
    }
}
