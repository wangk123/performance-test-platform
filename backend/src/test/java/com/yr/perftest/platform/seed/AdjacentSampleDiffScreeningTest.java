package com.yr.perftest.platform.seed;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AdjacentSampleDiffScreeningTest {
    @Test
    void skipsEqualTableWithoutOpeningSnapshotFilesAndRecordsEvidence() {
        AtomicInteger opens = new AtomicInteger();
        TableMetadata metadata = primaryKeyMetadata();
        ChunkSnapshotView chunk = new ChunkSnapshotView(
                0,
                "1",
                "1",
                1,
                "chunk-same",
                true,
                () -> {
                    opens.incrementAndGet();
                    return List.of(row(1, "same")).stream();
                }
        );
        TableSnapshotView table = new TableSnapshotView(
                "app.orders",
                metadata,
                "schema-same",
                1,
                "table-same",
                true,
                List.of(chunk)
        );

        TableDiff diff = AdjacentSampleDiffEngine.analyze(List.of(
                sample(1L, 1, "2026-07-15T06:01:00Z", table, false),
                sample(2L, 2, "2026-07-15T06:02:00Z", table, false),
                sample(3L, 3, "2026-07-15T06:03:00Z", table, false)
        )).intervals().get(0).tableDiffs().get(0);

        assertThat(diff.status()).isEqualTo(DiffStatus.UNCHANGED);
        assertThat(diff.screeningEvidence())
                .singleElement()
                .satisfies(evidence -> {
                    assertThat(evidence.action()).isEqualTo(ScreeningEvidence.Action.SKIPPED_TABLE);
                    assertThat(evidence.openedSnapshot()).isFalse();
                });
        assertThat(opens).hasValue(0);
    }

    @Test
    void mergesChangedPrimaryKeyChunksAndKeepsDeleteAsDiagnostic() {
        AtomicInteger unchangedChunkOpens = new AtomicInteger();
        AtomicInteger changedBeforeOpens = new AtomicInteger();
        AtomicInteger changedAfterOpens = new AtomicInteger();
        TableMetadata metadata = primaryKeyMetadata();
        ChunkSnapshotView unchangedBefore = trackedChunk(
                0,
                "chunk-unchanged",
                row(1, "same"),
                unchangedChunkOpens
        );
        ChunkSnapshotView unchangedAfter = trackedChunk(
                0,
                "chunk-unchanged",
                row(1, "same"),
                unchangedChunkOpens
        );
        ChunkSnapshotView changedBefore = trackedChunk(
                1,
                "chunk-before",
                row(2, "old"),
                changedBeforeOpens
        );
        ChunkSnapshotView changedAfter = trackedChunk(
                1,
                "chunk-after",
                row(2, "new"),
                changedAfterOpens
        );
        TableSnapshotView before = new TableSnapshotView(
                "app.orders",
                metadata,
                "schema",
                2,
                "table-before",
                true,
                List.of(unchangedBefore, changedBefore)
        );
        TableSnapshotView after = new TableSnapshotView(
                "app.orders",
                metadata,
                "schema",
                2,
                "table-after",
                true,
                List.of(unchangedAfter, changedAfter)
        );

        TableDiff diff = AdjacentSampleDiffEngine.analyze(List.of(
                sample(1L, 1, "2026-07-15T06:01:00Z", before, false),
                sample(2L, 2, "2026-07-15T06:02:00Z", after, false),
                sample(3L, 3, "2026-07-15T06:03:00Z", after, false)
        )).intervals().get(0).tableDiffs().get(0);

        assertThat(diff.rowDiffs())
                .extracting(SnapshotDiffEngine.RowDiff::kind)
                .containsExactly("UPDATE");
        assertThat(unchangedChunkOpens).hasValue(0);
        assertThat(changedBeforeOpens).hasValue(1);
        assertThat(changedAfterOpens).hasValue(1);

        TableSnapshotView afterWithoutRow = new TableSnapshotView(
                "app.orders",
                metadata,
                "schema",
                1,
                "table-after-delete",
                true,
                List.of(ChunkSnapshotView.fromRows(0, "1", "1", List.of(row(1, "same"))))
        );
        TableDiff deleteDiff = AdjacentSampleDiffEngine.analyze(List.of(
                sample(4L, 4, "2026-07-15T06:04:00Z", before, false),
                sample(5L, 5, "2026-07-15T06:05:00Z", afterWithoutRow, false),
                sample(6L, 6, "2026-07-15T06:06:00Z", afterWithoutRow, false)
        )).intervals().get(0).tableDiffs().get(0);
        assertThat(deleteDiff.deleteCount()).isEqualTo(1);
    }

    @Test
    void comparesNoPrimaryKeyTablesAsMultisetsAndNeverInfersUpdate() {
        TableMetadata metadata = new TableMetadata("app.events", List.of(), Set.of(), Map.of());
        TableSnapshotView before = TableSnapshotView.fromRows(
                "app.events",
                metadata,
                List.of(rowWithoutId("a"), rowWithoutId("b")),
                2
        );
        TableSnapshotView sameRowsDifferentOrder = TableSnapshotView.fromRows(
                "app.events",
                metadata,
                List.of(rowWithoutId("b"), rowWithoutId("a")),
                2
        );

        TableDiff unchanged = firstDiff(before, sameRowsDifferentOrder);
        assertThat(unchanged.status()).isEqualTo(DiffStatus.UNCHANGED);
        assertThat(unchanged.riskyNoPk()).isTrue();

        TableSnapshotView changed = TableSnapshotView.fromRows(
                "app.events",
                metadata,
                List.of(rowWithoutId("a"), rowWithoutId("c")),
                2
        );
        TableDiff changedDiff = firstDiff(before, changed);
        assertThat(changedDiff.status()).isEqualTo(DiffStatus.CHANGED);
        assertThat(changedDiff.riskyNoPk()).isTrue();
        assertThat(changedDiff).extracting(TableDiff::updateCount).isEqualTo(0L);
        assertThat(changedDiff.insertCount()).isEqualTo(1);
        assertThat(changedDiff.deleteCount()).isEqualTo(1);
    }

    @Test
    void propagatesUnknownForMissingIncompleteAndCorruptData() {
        TableMetadata metadata = primaryKeyMetadata();
        TableSnapshotView complete = TableSnapshotView.fromRows(
                "app.orders",
                metadata,
                List.of(row(1, "same")),
                1
        );

        TableDiff missing = AdjacentSampleDiffEngine.analyze(List.of(
                sampleWithTables(1L, 1, "2026-07-15T06:01:00Z", Map.of(), false),
                sampleWithTables(2L, 2, "2026-07-15T06:02:00Z", Map.of("app.orders", complete), false),
                sampleWithTables(3L, 3, "2026-07-15T06:03:00Z", Map.of("app.orders", complete), false)
        )).intervals().get(0).tableDiffs().get(0);
        assertUnknown(missing);

        TableDiff incomplete = firstDiff(
                complete,
                complete,
                true
        );
        assertUnknown(incomplete);

        ChunkSnapshotView corruptChunk = new ChunkSnapshotView(
                0,
                null,
                null,
                1,
                "chunk",
                false,
                () -> List.of(row(1, "same")).stream()
        );
        TableSnapshotView corrupt = new TableSnapshotView(
                "app.orders",
                metadata,
                "schema",
                1,
                "different",
                true,
                List.of(corruptChunk)
        );
        TableDiff corruptDiff = firstDiff(complete, corrupt);
        assertUnknown(corruptDiff);
    }

    @Test
    void propagatesUnknownWhenAChunkManifestIsMissing() {
        TableMetadata metadata = primaryKeyMetadata();
        TableSnapshotView complete = TableSnapshotView.fromRows(
                "app.orders",
                metadata,
                List.of(row(1, "one"), row(2, "two")),
                1
        );
        TableSnapshotView missingChunk = new TableSnapshotView(
                "app.orders",
                metadata,
                complete.schemaHash(),
                2,
                "different",
                true,
                List.of(complete.chunks().get(0))
        );

        TableDiff diff = firstDiff(complete, missingChunk);

        assertUnknown(diff);
    }

    private static TableDiff firstDiff(TableSnapshotView before, TableSnapshotView after) {
        return firstDiff(before, after, false);
    }

    private static TableDiff firstDiff(
            TableSnapshotView before,
            TableSnapshotView after,
            boolean incompleteAfter
    ) {
        return AdjacentSampleDiffEngine.analyze(List.of(
                sample(1L, 1, "2026-07-15T06:01:00Z", before, false),
                sample(2L, 2, "2026-07-15T06:02:00Z", after, incompleteAfter),
                sample(3L, 3, "2026-07-15T06:03:00Z", after, incompleteAfter)
        )).intervals().get(0).tableDiffs().get(0);
    }

    private static void assertUnknown(TableDiff diff) {
        assertThat(diff.status()).isEqualTo(DiffStatus.UNKNOWN);
        assertThat(diff.rowDiffs()).isEmpty();
        assertThat(diff.screeningEvidence())
                .anySatisfy(evidence -> assertThat(evidence.action())
                        .isEqualTo(ScreeningEvidence.Action.UNKNOWN));
    }

    private static ChunkSnapshotView trackedChunk(
            int seq,
            String hash,
            Map<String, Object> row,
            AtomicInteger opens
    ) {
        return new ChunkSnapshotView(
                seq,
                String.valueOf(row.get("id")),
                String.valueOf(row.get("id")),
                1,
                hash,
                true,
                () -> {
                    opens.incrementAndGet();
                    return List.of(row).stream();
                }
        );
    }

    private static SampleSnapshotView sample(
            long id,
            int seq,
            String startedAt,
            TableSnapshotView table,
            boolean incomplete
    ) {
        return sampleWithTables(
                id,
                seq,
                startedAt,
                Map.of(table.tableName(), table),
                incomplete
        );
    }

    private static SampleSnapshotView sampleWithTables(
            long id,
            int seq,
            String startedAt,
            Map<String, TableSnapshotView> tables,
            boolean incomplete
    ) {
        return new SampleSnapshotView(
                id,
                7L,
                seq,
                Instant.parse(startedAt),
                1,
                "SUCCEEDED",
                incomplete,
                tables
        );
    }

    private static TableMetadata primaryKeyMetadata() {
        return new TableMetadata("app.orders", List.of("id"), Set.of("id"), Map.of());
    }

    private static Map<String, Object> row(int id, String name) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("name", name);
        return row;
    }

    private static Map<String, Object> rowWithoutId(String value) {
        return Map.of("value", value);
    }
}
