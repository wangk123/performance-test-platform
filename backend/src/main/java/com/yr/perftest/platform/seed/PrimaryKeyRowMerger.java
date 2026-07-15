package com.yr.perftest.platform.seed;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.stream.Stream;

final class PrimaryKeyRowMerger {
    private PrimaryKeyRowMerger() {
    }

    static Result merge(
            List<ChunkSnapshotView> beforeChunks,
            List<ChunkSnapshotView> afterChunks,
            TableMetadata metadata
    ) {
        List<SnapshotDiffEngine.RowDiff> rowDiffs = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();
        long comparedRows = 0;
        try (MergedRowCursor before = new MergedRowCursor(beforeChunks, metadata);
             MergedRowCursor after = new MergedRowCursor(afterChunks, metadata)) {
            Map<String, Object> beforeRow = before.next();
            Map<String, Object> afterRow = after.next();
            while (beforeRow != null || afterRow != null) {
                if (beforeRow == null) {
                    rowDiffs.add(new SnapshotDiffEngine.RowDiff(
                            "INSERT",
                            primaryKey(afterRow, metadata),
                            Map.of(),
                            stringify(afterRow)
                    ));
                    comparedRows++;
                    afterRow = after.next();
                    continue;
                }
                if (afterRow == null) {
                    rowDiffs.add(new SnapshotDiffEngine.RowDiff(
                            "DELETE",
                            primaryKey(beforeRow, metadata),
                            stringify(beforeRow),
                            Map.of()
                    ));
                    comparedRows++;
                    beforeRow = before.next();
                    continue;
                }
                int keyOrder = comparePrimaryKey(beforeRow, afterRow, metadata);
                if (keyOrder < 0) {
                    rowDiffs.add(new SnapshotDiffEngine.RowDiff(
                            "DELETE",
                            primaryKey(beforeRow, metadata),
                            stringify(beforeRow),
                            Map.of()
                    ));
                    comparedRows++;
                    beforeRow = before.next();
                } else if (keyOrder > 0) {
                    rowDiffs.add(new SnapshotDiffEngine.RowDiff(
                            "INSERT",
                            primaryKey(afterRow, metadata),
                            Map.of(),
                            stringify(afterRow)
                    ));
                    comparedRows++;
                    afterRow = after.next();
                } else {
                    Map<String, String> beforeValues = stringify(beforeRow);
                    Map<String, String> afterValues = stringify(afterRow);
                    if (!java.util.Arrays.equals(
                            CanonicalRowEncoding.encodeRow(beforeRow),
                            CanonicalRowEncoding.encodeRow(afterRow)
                    )) {
                        rowDiffs.add(new SnapshotDiffEngine.RowDiff(
                                "UPDATE",
                                primaryKey(afterRow, metadata),
                                beforeValues,
                                afterValues
                        ));
                    }
                    comparedRows += 2;
                    beforeRow = before.next();
                    afterRow = after.next();
                }
            }
        }
        return new Result(rowDiffs, comparedRows, diagnostics);
    }

    static Map<String, String> stringify(Map<String, ?> row) {
        Map<String, String> result = new LinkedHashMap<>();
        row.forEach((key, value) -> result.put(key, value == null ? null : String.valueOf(value)));
        return result;
    }

    static Map<String, String> primaryKey(Map<String, ?> row, TableMetadata metadata) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String column : metadata.primaryKeyColumns()) {
            Object value = row.get(column);
            result.put(column, value == null ? null : String.valueOf(value));
        }
        return result;
    }

    private static int comparePrimaryKey(
            Map<String, ?> left,
            Map<String, ?> right,
            TableMetadata metadata
    ) {
        for (String column : metadata.primaryKeyColumns()) {
            int comparison = compareValues(left.get(column), right.get(column));
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    private static int compareValues(Object left, Object right) {
        if (left == right) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        if (left instanceof Number || right instanceof Number) {
            try {
                return new BigDecimal(String.valueOf(left))
                        .compareTo(new BigDecimal(String.valueOf(right)));
            } catch (NumberFormatException ignored) {
                // Fall through to a stable textual comparison.
            }
        }
        if (left instanceof byte[] leftBytes && right instanceof byte[] rightBytes) {
            return compareBytes(leftBytes, rightBytes);
        }
        return String.valueOf(left).compareTo(String.valueOf(right));
    }

    private static int compareBytes(byte[] left, byte[] right) {
        int length = Math.min(left.length, right.length);
        for (int i = 0; i < length; i++) {
            int comparison = Byte.compare(left[i], right[i]);
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(left.length, right.length);
    }

    record Result(
            List<SnapshotDiffEngine.RowDiff> rowDiffs,
            long comparedRows,
            List<String> diagnostics
    ) {
    }

    private static final class MergedRowCursor implements AutoCloseable {
        private final TableMetadata metadata;
        private final List<Stream<Map<String, Object>>> streams = new ArrayList<>();
        private final PriorityQueue<StreamHead> heads;
        private Map<String, Object> last;

        private MergedRowCursor(
                List<ChunkSnapshotView> chunks,
                TableMetadata metadata
        ) {
            this.metadata = metadata;
            this.heads = new PriorityQueue<>(
                    Comparator.comparing(StreamHead::row, (left, right) ->
                            comparePrimaryKey(left, right, metadata))
            );
            for (ChunkSnapshotView chunk : chunks) {
                Stream<Map<String, Object>> stream = Objects.requireNonNull(
                        chunk.rowSource().get(),
                        "chunk row source returned null"
                );
                streams.add(stream);
                Iterator<Map<String, Object>> iterator = stream.iterator();
                if (iterator.hasNext()) {
                    heads.add(new StreamHead(iterator, iterator.next()));
                }
            }
        }

        private Map<String, Object> next() {
            StreamHead head = heads.poll();
            if (head == null) {
                return null;
            }
            Map<String, Object> row = head.row();
            if (last != null && comparePrimaryKey(last, row, metadata) > 0) {
                throw new IllegalStateException("primary-key rows are not ordered");
            }
            last = row;
            if (head.iterator().hasNext()) {
                heads.add(new StreamHead(head.iterator(), head.iterator().next()));
            }
            return row;
        }

        @Override
        public void close() {
            RuntimeException failure = null;
            for (Stream<Map<String, Object>> stream : streams) {
                try {
                    stream.close();
                } catch (RuntimeException ex) {
                    failure = ex;
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }

    private record StreamHead(
            Iterator<Map<String, Object>> iterator,
            Map<String, Object> row
    ) {
    }
}
