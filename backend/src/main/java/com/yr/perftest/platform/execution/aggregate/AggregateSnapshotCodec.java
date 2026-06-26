package com.yr.perftest.platform.execution.aggregate;

import com.yr.perftest.platform.execution.TaskExecutionResult;
import org.HdrHistogram.Histogram;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;

public final class AggregateSnapshotCodec {
    public static final String BUILDER_VERSION = "hdr-v1";
    private static final java.util.regex.Pattern REDIRECT_SUBRESULT =
            java.util.regex.Pattern.compile("^[A-Z]+\\s+/.*");

    private AggregateSnapshotCodec() {
    }

    public static ParsedSnapshot decodeAndMerge(java.util.List<byte[]> snapshots) throws Exception {
        if (snapshots == null || snapshots.isEmpty()) {
            return new ParsedSnapshot(0L, 0L, new LinkedHashMap<>());
        }
        if (snapshots.size() == 1) {
            return decode(snapshots.get(0));
        }
        long start = Long.MAX_VALUE;
        long end = 0L;
        Map<String, LabelSnapshot> merged = new LinkedHashMap<>();
        for (byte[] data : snapshots) {
            if (data == null || data.length < 20) {
                continue;
            }
            ParsedSnapshot parsed = decode(data);
            if (parsed.startMs() > 0 && parsed.startMs() < start) {
                start = parsed.startMs();
            }
            if (parsed.endMs() > end) {
                end = parsed.endMs();
            }
            for (Map.Entry<String, LabelSnapshot> entry : parsed.labels().entrySet()) {
                LabelSnapshot incoming = entry.getValue();
                LabelSnapshot current = merged.get(entry.getKey());
                if (current == null) {
                    Histogram histogram = new Histogram(1L, 3_600_000L, 3);
                    if (incoming.histogram() != null) {
                        histogram.add(incoming.histogram());
                    }
                    merged.put(entry.getKey(), new LabelSnapshot(
                            incoming.count(),
                            incoming.errorCount(),
                            incoming.sumMs(),
                            incoming.minMs(),
                            incoming.maxMs(),
                            histogram
                    ));
                } else {
                    Histogram histogram = current.histogram();
                    if (incoming.histogram() != null) {
                        histogram.add(incoming.histogram());
                    }
                    merged.put(entry.getKey(), new LabelSnapshot(
                            current.count() + incoming.count(),
                            current.errorCount() + incoming.errorCount(),
                            current.sumMs() + incoming.sumMs(),
                            Math.min(current.minMs(), incoming.minMs()),
                            Math.max(current.maxMs(), incoming.maxMs()),
                            histogram
                    ));
                }
            }
        }
        return new ParsedSnapshot(start == Long.MAX_VALUE ? 0L : start, end, merged);
    }

    public static ParsedSnapshot decode(byte[] data) throws Exception {
        if (data == null || data.length < 20) {
            throw new IllegalArgumentException("aggregate snapshot is empty");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(data))) {
            int version = input.readInt();
            if (version != 1) {
                throw new IllegalArgumentException("unsupported aggregate snapshot version: " + version);
            }
            long startMs = input.readLong();
            long endMs = input.readLong();
            int labelCount = input.readInt();
            Map<String, LabelSnapshot> labels = new LinkedHashMap<>();
            for (int index = 0; index < labelCount; index++) {
                String label = input.readUTF();
                long count = input.readLong();
                long errorCount = input.readLong();
                long sumMs = input.readLong();
                long minMs = input.readLong();
                long maxMs = input.readLong();
                int histLen = input.readInt();
                byte[] histBytes = input.readNBytes(histLen);
                Histogram histogram = decodeHistogram(histBytes);
                labels.put(label, new LabelSnapshot(count, errorCount, sumMs, minMs, maxMs, histogram));
            }
            return new ParsedSnapshot(startMs, endMs, labels);
        }
    }

    public static TaskExecutionResult toResult(ParsedSnapshot snapshot, double durationSeconds, String accuracy) {
        double safeDuration = Math.max(1, durationSeconds);
        List<TaskExecutionResult.AggregateRow> rows = new ArrayList<>();
        Accumulator total = new Accumulator();
        snapshot.labels().entrySet().stream()
                .filter(entry -> !isRedirectSubresult(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    LabelSnapshot label = entry.getValue();
                    if (label.count() <= 0) {
                        return;
                    }
                    TaskExecutionResult.AggregateRow row = label.toRow(entry.getKey(), safeDuration);
                    rows.add(row);
                    total.merge(label);
                });
        if (rows.isEmpty()) {
            return TaskExecutionResult.empty().withAccuracy(accuracy);
        }
        return new TaskExecutionResult(
                total.toSummary(safeDuration, accuracy),
                rows,
                List.of()
        );
    }

    private static boolean isRedirectSubresult(String label) {
        return label != null && REDIRECT_SUBRESULT.matcher(label).matches();
    }

    private static Histogram decodeHistogram(byte[] bytes) throws DataFormatException {
        if (bytes == null || bytes.length == 0) {
            return new Histogram(1L, 3_600_000L, 3);
        }
        return Histogram.decodeFromCompressedByteBuffer(ByteBuffer.wrap(bytes), 0);
    }

    public record ParsedSnapshot(long startMs, long endMs, Map<String, LabelSnapshot> labels) {
    }

    public record LabelSnapshot(
            long count,
            long errorCount,
            long sumMs,
            long minMs,
            long maxMs,
            Histogram histogram
    ) {
        TaskExecutionResult.AggregateRow toRow(String label, double durationSeconds) {
            double divisor = Math.max(1, count);
            return new TaskExecutionResult.AggregateRow(
                    label,
                    "全部节点",
                    (int) Math.min(Integer.MAX_VALUE, count),
                    Math.round(sumMs / divisor),
                    percentile(50),
                    percentile(90),
                    percentile(95),
                    percentile(99),
                    normalizeMin(minMs),
                    Math.max(0, maxMs),
                    round(errorCount * 100.0 / divisor),
                    round(count / durationSeconds)
            );
        }

        private long percentile(double percentile) {
            if (histogram == null || count <= 0) {
                return 0;
            }
            return Math.round(histogram.getValueAtPercentile(percentile));
        }

        private static long normalizeMin(long minMs) {
            if (minMs == Long.MAX_VALUE || minMs <= 0) {
                return 0;
            }
            return minMs;
        }

        private static double round(double value) {
            return Math.round(value * 100.0) / 100.0;
        }
    }

    private static final class Accumulator {
        private long count;
        private long errorCount;
        private long sumMs;
        private long minMs = Long.MAX_VALUE;
        private long maxMs;
        private final Histogram histogram = new Histogram(1L, 3_600_000L, 3);

        private void merge(LabelSnapshot label) {
            count += label.count();
            errorCount += label.errorCount();
            sumMs += label.sumMs();
            if (label.minMs() < minMs) {
                minMs = label.minMs();
            }
            if (label.maxMs() > maxMs) {
                maxMs = label.maxMs();
            }
            if (label.histogram() != null) {
                histogram.add(label.histogram());
            }
        }

        private TaskExecutionResult.Summary toSummary(double durationSeconds, String accuracy) {
            double divisor = Math.max(1, count);
            return new TaskExecutionResult.Summary(
                    (int) Math.min(Integer.MAX_VALUE, count),
                    LabelSnapshot.round(count / durationSeconds),
                    Math.round(sumMs / divisor),
                    Math.round(histogram.getValueAtPercentile(95.0)),
                    LabelSnapshot.round(errorCount * 100.0 / divisor),
                    accuracy
            );
        }
    }
}
