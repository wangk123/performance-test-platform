package com.yr.perftest.platform.seed;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Stream;

final class AdjacentTableDiffEngine {
    private AdjacentTableDiffEngine() {
    }

    static List<TableDiff> diffTables(
            SampleSnapshotView before,
            SampleSnapshotView after
    ) {
        SortedSet<String> tableNames = new TreeSet<>();
        tableNames.addAll(before.tables().keySet());
        tableNames.addAll(after.tables().keySet());
        List<TableDiff> result = new ArrayList<>(tableNames.size());
        for (String tableName : tableNames) {
            result.add(diffTable(
                    tableName,
                    before,
                    before.tables().get(tableName),
                    after,
                    after.tables().get(tableName)
            ));
        }
        return result;
    }

    private static TableDiff diffTable(
            String tableName,
            SampleSnapshotView beforeSample,
            TableSnapshotView before,
            SampleSnapshotView afterSample,
            TableSnapshotView after
    ) {
        boolean riskyNoPk = (before != null && isNoPk(before.metadata()))
                || (after != null && isNoPk(after.metadata()));
        if (before == null || after == null) {
            return unknown(
                    tableName,
                    riskyNoPk,
                    "missing table data; absence is not an empty snapshot"
            );
        }
        if (beforeSample.incomplete() || afterSample.incomplete()
                || !before.complete() || !after.complete()) {
            return unknown(tableName, riskyNoPk, "incomplete sample or table data");
        }
        if (!schemaCompatible(before, after)) {
            return unknown(tableName, riskyNoPk, "incompatible schema between snapshots");
        }
        if (hasUnverifiedChunk(before) || hasUnverifiedChunk(after)) {
            return unknown(tableName, riskyNoPk, "missing or unverified chunk checksum");
        }
        if (sameTableManifest(beforeSample, afterSample, before, after)) {
            return new TableDiff(
                    tableName,
                    DiffStatus.UNCHANGED,
                    List.of(),
                    List.of(new ScreeningEvidence(
                            tableName,
                            ScreeningEvidence.Action.SKIPPED_TABLE,
                            "schema hash, row count, and content hash matched",
                            false
                    )),
                    0,
                    before.rowCount(),
                    riskyNoPk,
                    List.of()
            );
        }
        if (isNoPk(before.metadata())) {
            return diffNoPrimaryKeyTable(tableName, before, after);
        }
        return diffPrimaryKeyTable(tableName, before, after);
    }

    private static boolean sameTableManifest(
            SampleSnapshotView beforeSample,
            SampleSnapshotView afterSample,
            TableSnapshotView before,
            TableSnapshotView after
    ) {
        return beforeSample.configVersion() == afterSample.configVersion()
                && before.schemaHash() != null
                && before.schemaHash().equals(after.schemaHash())
                && before.contentHash() != null
                && before.contentHash().equals(after.contentHash())
                && before.rowCount() == after.rowCount();
    }

    private static boolean schemaCompatible(TableSnapshotView before, TableSnapshotView after) {
        if (before.schemaHash() != null && after.schemaHash() != null
                && !before.schemaHash().equals(after.schemaHash())) {
            return false;
        }
        if (before.metadata() == null || after.metadata() == null) {
            return false;
        }
        return before.metadata().primaryKeyColumns().equals(after.metadata().primaryKeyColumns());
    }

    private static boolean hasUnverifiedChunk(TableSnapshotView table) {
        if (table.rowCount() > 0 && table.chunks().isEmpty()) {
            return true;
        }
        long manifestRows = 0;
        int expectedSequence = 0;
        List<ChunkSnapshotView> ordered = table.chunks().stream()
                .sorted(Comparator.comparingInt(ChunkSnapshotView::chunkSeq))
                .toList();
        for (ChunkSnapshotView chunk : ordered) {
            if (!chunk.checksumVerified()
                    || chunk.contentHash() == null
                    || chunk.chunkSeq() != expectedSequence) {
                return true;
            }
            manifestRows += chunk.rowCount();
            expectedSequence++;
        }
        return manifestRows != table.rowCount();
    }

    private static TableDiff diffPrimaryKeyTable(
            String tableName,
            TableSnapshotView before,
            TableSnapshotView after
    ) {
        List<ScreeningEvidence> evidence = new ArrayList<>();
        Map<ChunkIdentity, ChunkSnapshotView> beforeChunks = indexChunks(before.chunks());
        Map<ChunkIdentity, ChunkSnapshotView> afterChunks = indexChunks(after.chunks());
        if (beforeChunks == null || afterChunks == null) {
            return unknown(tableName, false, "duplicate or invalid chunk identity");
        }

        List<ChunkSnapshotView> fineBefore = new ArrayList<>();
        List<ChunkSnapshotView> fineAfter = new ArrayList<>();
        long skippedRows = 0;
        Set<ChunkIdentity> identities = new HashSet<>();
        identities.addAll(beforeChunks.keySet());
        identities.addAll(afterChunks.keySet());
        for (ChunkIdentity identity : identities) {
            ChunkSnapshotView beforeChunk = beforeChunks.get(identity);
            ChunkSnapshotView afterChunk = afterChunks.get(identity);
            if (canSkipChunk(beforeChunk, afterChunk)) {
                evidence.add(new ScreeningEvidence(
                        tableName + "#chunk-" + identity.chunkSeq(),
                        ScreeningEvidence.Action.SKIPPED_CHUNK,
                        "compatible chunk fingerprint matched",
                        false
                ));
                skippedRows += beforeChunk.rowCount();
            } else {
                if (beforeChunk != null) {
                    fineBefore.add(beforeChunk);
                }
                if (afterChunk != null) {
                    fineAfter.add(afterChunk);
                }
            }
        }
        if (fineBefore.isEmpty() && fineAfter.isEmpty()) {
            return new TableDiff(
                    tableName,
                    DiffStatus.CHANGED,
                    List.of(),
                    evidence,
                    0,
                    skippedRows,
                    false,
                    List.of("table manifest changed but all compatible chunks matched")
            );
        }

        evidence.add(new ScreeningEvidence(
                tableName,
                ScreeningEvidence.Action.FINE_COMPARE,
                "changed or unmatched chunks were merged by primary key",
                true
        ));
        try {
            PrimaryKeyRowMerger.Result merged = PrimaryKeyRowMerger.merge(
                    fineBefore,
                    fineAfter,
                    before.metadata()
            );
            return new TableDiff(
                    tableName,
                    DiffStatus.CHANGED,
                    merged.rowDiffs(),
                    evidence,
                    merged.comparedRows(),
                    skippedRows,
                    false,
                    merged.diagnostics()
            );
        } catch (RuntimeException ex) {
            return unknown(tableName, false, "unable to read or merge changed chunks: " + message(ex));
        }
    }

    private static boolean canSkipChunk(
            ChunkSnapshotView before,
            ChunkSnapshotView after
    ) {
        return before != null
                && after != null
                && before.checksumVerified()
                && after.checksumVerified()
                && before.rowCount() == after.rowCount()
                && before.contentHash() != null
                && before.contentHash().equals(after.contentHash());
    }

    private static TableDiff diffNoPrimaryKeyTable(
            String tableName,
            TableSnapshotView before,
            TableSnapshotView after
    ) {
        List<ScreeningEvidence> evidence = new ArrayList<>();
        Map<ChunkIdentity, ChunkSnapshotView> beforeChunks = indexChunks(before.chunks());
        Map<ChunkIdentity, ChunkSnapshotView> afterChunks = indexChunks(after.chunks());
        if (beforeChunks == null || afterChunks == null) {
            return unknown(tableName, true, "duplicate or invalid chunk identity");
        }
        List<SnapshotDiffEngine.RowDiff> rowDiffs = new ArrayList<>();
        Set<ChunkIdentity> identities = new HashSet<>();
        identities.addAll(beforeChunks.keySet());
        identities.addAll(afterChunks.keySet());
        long skippedRows = 0;
        long comparedRows = 0;
        for (ChunkIdentity identity : identities) {
            ChunkSnapshotView beforeChunk = beforeChunks.get(identity);
            ChunkSnapshotView afterChunk = afterChunks.get(identity);
            if (canSkipChunk(beforeChunk, afterChunk)) {
                evidence.add(new ScreeningEvidence(
                        tableName + "#chunk-" + identity.chunkSeq(),
                        ScreeningEvidence.Action.SKIPPED_CHUNK,
                        "compatible no-primary-key multiset chunk matched",
                        false
                ));
                skippedRows += beforeChunk.rowCount();
                continue;
            }
            evidence.add(new ScreeningEvidence(
                    tableName + "#chunk-" + identity.chunkSeq(),
                    ScreeningEvidence.Action.FINE_COMPARE,
                    "no-primary-key multiset comparison",
                    true
            ));
            try {
                Map<String, RowBucket> beforeRows = collectMultiset(beforeChunk);
                Map<String, RowBucket> afterRows = collectMultiset(afterChunk);
                comparedRows += countRows(beforeRows) + countRows(afterRows);
                appendMultisetDiffs(rowDiffs, beforeRows, afterRows);
            } catch (RuntimeException ex) {
                return unknown(tableName, true, "unable to compare no-primary-key rows: " + message(ex));
            }
        }
        return new TableDiff(
                tableName,
                DiffStatus.CHANGED,
                rowDiffs,
                evidence,
                comparedRows,
                skippedRows,
                true,
                List.of(
                        "no primary key; row-set additions/removals are diagnostic only",
                        "reliable UPDATE identity is unavailable"
                )
        );
    }

    private static Map<ChunkIdentity, ChunkSnapshotView> indexChunks(
            List<ChunkSnapshotView> chunks
    ) {
        Map<ChunkIdentity, ChunkSnapshotView> result = new LinkedHashMap<>();
        for (ChunkSnapshotView chunk : chunks) {
            ChunkIdentity identity = new ChunkIdentity(
                    chunk.chunkSeq(),
                    chunk.pkRangeStart(),
                    chunk.pkRangeEnd()
            );
            if (result.put(identity, chunk) != null) {
                return null;
            }
        }
        return result;
    }

    private static Map<String, RowBucket> collectMultiset(ChunkSnapshotView chunk) {
        Map<String, RowBucket> rows = new HashMap<>();
        if (chunk == null) {
            return rows;
        }
        try (Stream<Map<String, Object>> stream = chunk.rowSource().get()) {
            stream.forEach(row -> {
                String key = Base64.getEncoder().encodeToString(
                        CanonicalRowEncoding.encodeRow(row)
                );
                RowBucket bucket = rows.get(key);
                if (bucket == null) {
                    rows.put(key, new RowBucket(copyRow(row), 1));
                } else {
                    bucket.count++;
                }
            });
        }
        return rows;
    }

    private static void appendMultisetDiffs(
            List<SnapshotDiffEngine.RowDiff> output,
            Map<String, RowBucket> before,
            Map<String, RowBucket> after
    ) {
        Set<String> keys = new HashSet<>();
        keys.addAll(before.keySet());
        keys.addAll(after.keySet());
        for (String key : keys) {
            RowBucket beforeBucket = before.get(key);
            RowBucket afterBucket = after.get(key);
            int beforeCount = beforeBucket == null ? 0 : beforeBucket.count;
            int afterCount = afterBucket == null ? 0 : afterBucket.count;
            if (afterCount > beforeCount) {
                for (int i = 0; i < afterCount - beforeCount; i++) {
                    output.add(new SnapshotDiffEngine.RowDiff(
                            "INSERT",
                            Map.of(),
                            Map.of(),
                            PrimaryKeyRowMerger.stringify(afterBucket.row)
                    ));
                }
            } else if (beforeCount > afterCount) {
                for (int i = 0; i < beforeCount - afterCount; i++) {
                    output.add(new SnapshotDiffEngine.RowDiff(
                            "DELETE",
                            Map.of(),
                            PrimaryKeyRowMerger.stringify(beforeBucket.row),
                            Map.of()
                    ));
                }
            }
        }
    }

    private static long countRows(Map<String, RowBucket> rows) {
        return rows.values().stream().mapToLong(bucket -> bucket.count).sum();
    }

    private static TableDiff unknown(String tableName, boolean riskyNoPk, String reason) {
        return new TableDiff(
                tableName,
                DiffStatus.UNKNOWN,
                List.of(),
                List.of(new ScreeningEvidence(
                        tableName,
                        ScreeningEvidence.Action.UNKNOWN,
                        reason,
                        false
                )),
                0,
                0,
                riskyNoPk,
                List.of(reason)
        );
    }

    private static boolean isNoPk(TableMetadata metadata) {
        return metadata != null
                && (metadata.primaryKeyColumns() == null || metadata.primaryKeyColumns().isEmpty());
    }

    private static Map<String, Object> copyRow(Map<String, ?> row) {
        Map<String, Object> copy = new LinkedHashMap<>();
        row.forEach(copy::put);
        return copy;
    }

    private static String message(RuntimeException ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    private record ChunkIdentity(int chunkSeq, String pkRangeStart, String pkRangeEnd) {
    }

    private static final class RowBucket {
        private final Map<String, Object> row;
        private int count;

        private RowBucket(Map<String, Object> row, int count) {
            this.row = row;
            this.count = count;
        }
    }
}
