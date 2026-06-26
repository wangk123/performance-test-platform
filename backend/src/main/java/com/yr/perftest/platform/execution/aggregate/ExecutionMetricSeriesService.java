package com.yr.perftest.platform.execution.aggregate;

import com.yr.perftest.platform.execution.ExecutionEventBroadcaster;
import org.HdrHistogram.Histogram;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

@Service
public class ExecutionMetricSeriesService {
    private static final int LIVE_BUFFER_CAPACITY = 1200;
    private static final Pattern REDIRECT_SUBRESULT = Pattern.compile("^[A-Z]+\\s+/.*");

    private final PersistentExecutionMetricSeriesRepository repository;
    private final ExecutionEventBroadcaster broadcaster;
    private final ConcurrentMap<Long, AggregateSnapshotCodec.ParsedSnapshot> previousSnapshots = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Deque<MetricTick>> liveBuffers = new ConcurrentHashMap<>();

    public ExecutionMetricSeriesService(
            PersistentExecutionMetricSeriesRepository repository,
            ExecutionEventBroadcaster broadcaster
    ) {
        this.repository = repository;
        this.broadcaster = broadcaster;
    }

    @Transactional
    public MetricTick recordSnapshot(long executionId, AggregateSnapshotCodec.ParsedSnapshot current) {
        AggregateSnapshotCodec.ParsedSnapshot previous = previousSnapshots.get(executionId);
        long bucketStart = previous == null ? current.startMs() : previous.endMs();
        long bucketEnd = current.endMs();
        if (bucketEnd <= bucketStart) {
            previousSnapshots.put(executionId, current);
            return null;
        }
        double deltaSeconds = Math.max(0.001, (bucketEnd - bucketStart) / 1000.0);

        List<MetricTick.LabelMetric> labelMetrics = new ArrayList<>();
        long totalSamples = 0;
        long totalErrors = 0;
        long totalSumMs = 0;
        Histogram totalDeltaHist = new Histogram(1L, 3_600_000L, 3);
        List<PersistentExecutionMetricSeriesRecord> records = new ArrayList<>();

        for (Map.Entry<String, AggregateSnapshotCodec.LabelSnapshot> entry : current.labels().entrySet()) {
            String label = entry.getKey();
            if (isRedirectSubresult(label)) {
                continue;
            }
            AggregateSnapshotCodec.LabelSnapshot curr = entry.getValue();
            AggregateSnapshotCodec.LabelSnapshot prev = previous == null ? null : previous.labels().get(label);
            long deltaSamples = curr.count() - (prev == null ? 0 : prev.count());
            if (deltaSamples <= 0) {
                continue;
            }
            long deltaErrors = Math.max(0, curr.errorCount() - (prev == null ? 0 : prev.errorCount()));
            long deltaSumMs = Math.max(0, curr.sumMs() - (prev == null ? 0 : prev.sumMs()));
            Histogram deltaHist = curr.histogram().copy();
            if (prev != null) {
                try {
                    deltaHist.subtract(prev.histogram());
                } catch (Exception ignored) {
                }
            }

            long avgRt = deltaSumMs / Math.max(1, deltaSamples);
            long p95Rt = Math.round(deltaHist.getValueAtPercentile(95.0));
            double throughput = round(deltaSamples / deltaSeconds);

            labelMetrics.add(new MetricTick.LabelMetric(label, deltaSamples, deltaErrors, throughput, avgRt, p95Rt));
            totalSamples += deltaSamples;
            totalErrors += deltaErrors;
            totalSumMs += deltaSumMs;
            try {
                totalDeltaHist.add(deltaHist);
            } catch (Exception ignored) {
            }
            records.add(new PersistentExecutionMetricSeriesRecord(
                    executionId,
                    bucketEnd,
                    label,
                    deltaSamples,
                    deltaErrors,
                    throughput,
                    avgRt,
                    p95Rt
            ));
        }

        if (totalSamples <= 0) {
            previousSnapshots.put(executionId, current);
            return null;
        }
        MetricTick.LabelMetric overall = new MetricTick.LabelMetric(
                "__total__",
                totalSamples,
                totalErrors,
                round(totalSamples / deltaSeconds),
                totalSumMs / Math.max(1, totalSamples),
                Math.round(totalDeltaHist.getValueAtPercentile(95.0))
        );
        MetricTick tick = new MetricTick(bucketEnd, labelMetrics, overall);

        if (!records.isEmpty()) {
            repository.saveAll(records);
        }
        pushLive(executionId, tick);
        broadcaster.publish(executionId, "metric-tick", tick);
        previousSnapshots.put(executionId, current);
        return tick;
    }

    public List<MetricTick> latestLiveTicks(long executionId, int limit) {
        Deque<MetricTick> buffer = liveBuffers.get(executionId);
        if (buffer == null || buffer.isEmpty()) {
            return List.of();
        }
        int safeLimit = Math.max(1, Math.min(limit, LIVE_BUFFER_CAPACITY));
        List<MetricTick> snapshot;
        synchronized (buffer) {
            snapshot = new ArrayList<>(buffer);
        }
        if (snapshot.size() <= safeLimit) {
            return snapshot;
        }
        return snapshot.subList(snapshot.size() - safeLimit, snapshot.size());
    }

    @Transactional(readOnly = true)
    public List<PersistentExecutionMetricSeriesRecord> loadPersisted(long executionId) {
        return repository.findByExecutionIdOrderByBucketTimeMsAsc(executionId);
    }

    @Transactional
    public void deleteByExecutionId(long executionId) {
        repository.deleteByExecutionId(executionId);
        clearLive(executionId);
    }

    public void clearLive(long executionId) {
        liveBuffers.remove(executionId);
        previousSnapshots.remove(executionId);
    }

    private void pushLive(long executionId, MetricTick tick) {
        Deque<MetricTick> buffer = liveBuffers.computeIfAbsent(executionId, ignored -> new ArrayDeque<>());
        synchronized (buffer) {
            buffer.addLast(tick);
            while (buffer.size() > LIVE_BUFFER_CAPACITY) {
                buffer.pollFirst();
            }
        }
    }

    private static boolean isRedirectSubresult(String label) {
        return label != null && REDIRECT_SUBRESULT.matcher(label).matches();
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
