package com.yr.perftest.platform.seed;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdjacentSampleDiffEngineTest {
    @Test
    void ordersSamplesByCaptureTimeThenSequenceAndBuildsAdjacentIntervals() {
        SampleSnapshotView third = sample(30L, 3, "2026-07-15T06:03:00Z", 1);
        SampleSnapshotView first = sample(10L, 1, "2026-07-15T06:01:00Z", 1);
        SampleSnapshotView second = sample(20L, 2, "2026-07-15T06:02:00Z", 1);

        DiffChainResult result = AdjacentSampleDiffEngine.analyze(List.of(third, first, second));

        assertThat(result.orderedSamples())
                .extracting(SampleSnapshotView::sampleId)
                .containsExactly(10L, 20L, 30L);
        assertThat(result.intervals())
                .extracting(AdjacentDiff::beforeSampleId)
                .containsExactly(10L, 20L);
        assertThat(result.intervals())
                .extracting(AdjacentDiff::afterSampleId)
                .containsExactly(20L, 30L);
    }

    @Test
    void usesSampleSequenceAsTieBreakerForEqualCaptureTimes() {
        String sameTime = "2026-07-15T06:01:00Z";

        DiffChainResult result = AdjacentSampleDiffEngine.analyze(List.of(
                sample(3L, 3, "2026-07-15T06:02:00Z", 1),
                sample(2L, 2, sameTime, 1),
                sample(1L, 1, sameTime, 1)
        ));

        assertThat(result.orderedSamples())
                .extracting(SampleSnapshotView::sampleSeq)
                .containsExactly(1, 2, 3);
    }

    @Test
    void rejectsFewerThanThreeTerminalSamples() {
        assertThatThrownBy(() -> AdjacentSampleDiffEngine.analyze(List.of(
                sample(1L, 1, "2026-07-15T06:01:00Z", 1),
                sample(2L, 2, "2026-07-15T06:02:00Z", 1)
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("three");
    }

    @Test
    void warnsOnSequenceGapsAndIncompatibleVersions() {
        DiffChainResult result = AdjacentSampleDiffEngine.analyze(List.of(
                sample(1L, 1, "2026-07-15T06:01:00Z", 1),
                sample(3L, 3, "2026-07-15T06:02:00Z", 2),
                sample(5L, 5, "2026-07-15T06:03:00Z", 2)
        ));

        assertThat(result.warnings())
                .extracting(DiffWarning::code)
                .contains(
                        DiffWarning.Code.SEQUENCE_GAP,
                        DiffWarning.Code.INCOMPATIBLE_STRATEGY_VERSION
                );
    }

    @Test
    void coarseAndFineScreeningMatchesDirectPrimaryKeyDiff() {
        TableMetadata metadata = new TableMetadata(
                "app.orders",
                List.of("id"),
                Set.of("id"),
                Map.of()
        );
        List<Map<String, Object>> beforeRows = List.of(
                row(1, "old"),
                row(2, "same"),
                row(3, "same"),
                row(4, "same")
        );
        List<Map<String, Object>> afterRows = List.of(
                row(1, "new"),
                row(2, "same"),
                row(3, "same"),
                row(4, "same"),
                row(5, "inserted")
        );

        TableSnapshotView beforeTable = TableSnapshotView.fromRows(
                "app.orders",
                metadata,
                beforeRows,
                2
        );
        TableSnapshotView afterTable = TableSnapshotView.fromRows(
                "app.orders",
                metadata,
                afterRows,
                2
        );
        DiffChainResult result = AdjacentSampleDiffEngine.analyze(List.of(
                sampleWithTable(1L, 1, "2026-07-15T06:01:00Z", beforeTable),
                sampleWithTable(2L, 2, "2026-07-15T06:02:00Z", afterTable),
                sampleWithTable(3L, 3, "2026-07-15T06:03:00Z", afterTable)
        ));

        TableDiff firstInterval = result.intervals().get(0).tableDiffs().get(0);
        assertThat(firstInterval.status()).isEqualTo(DiffStatus.CHANGED);
        assertThat(firstInterval.rowDiffs())
                .extracting(SnapshotDiffEngine.RowDiff::kind)
                .containsExactly("UPDATE", "INSERT");
        assertThat(firstInterval.screeningEvidence())
                .extracting(ScreeningEvidence::action)
                .contains(ScreeningEvidence.Action.SKIPPED_CHUNK);

        Map<String, SnapshotDiffEngine.RowDiff> expected = SnapshotDiffEngine.diff(
                stringify(beforeRows),
                stringify(afterRows)
        );
        Map<String, SnapshotDiffEngine.RowDiff> actual = new LinkedHashMap<>();
        for (SnapshotDiffEngine.RowDiff rowDiff : firstInterval.rowDiffs()) {
            actual.put(SnapshotDiffEngine.pkKey(rowDiff.after(), List.of("id")), rowDiff);
        }
        assertThat(actual).containsOnlyKeys(expected.keySet());
        expected.forEach((pk, expectedDiff) -> {
            SnapshotDiffEngine.RowDiff actualDiff = actual.get(pk);
            assertThat(actualDiff.kind()).isEqualTo(expectedDiff.kind());
            assertThat(actualDiff.before()).isEqualTo(expectedDiff.before());
            assertThat(actualDiff.after()).isEqualTo(expectedDiff.after());
        });

        assertThat(result.intervals().get(1).tableDiffs().get(0).status())
                .isEqualTo(DiffStatus.UNCHANGED);
    }

    private static SampleSnapshotView sample(
            long id,
            int sequence,
            String startedAt,
            int configVersion
    ) {
        return new SampleSnapshotView(
                id,
                7L,
                sequence,
                Instant.parse(startedAt),
                configVersion,
                "SUCCEEDED",
                false,
                Map.of()
        );
    }

    private static SampleSnapshotView sampleWithTable(
            long id,
            int sequence,
            String startedAt,
            TableSnapshotView table
    ) {
        return new SampleSnapshotView(
                id,
                7L,
                sequence,
                Instant.parse(startedAt),
                1,
                "SUCCEEDED",
                false,
                Map.of(table.tableName(), table)
        );
    }

    private static Map<String, Object> row(int id, String name) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("name", name);
        return row;
    }

    private static Map<String, Map<String, String>> stringify(
            List<Map<String, Object>> rows
    ) {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Map<String, String> values = new LinkedHashMap<>();
            row.forEach((key, value) -> values.put(key, value == null ? null : String.valueOf(value)));
            result.put(SnapshotDiffEngine.pkKey(values, List.of("id")), values);
        }
        return result;
    }
}
