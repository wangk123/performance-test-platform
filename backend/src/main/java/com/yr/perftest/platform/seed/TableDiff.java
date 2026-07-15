package com.yr.perftest.platform.seed;

import java.util.List;
import java.util.Objects;

public record TableDiff(
        String tableName,
        DiffStatus status,
        List<SnapshotDiffEngine.RowDiff> rowDiffs,
        List<ScreeningEvidence> screeningEvidence,
        long comparedRows,
        long skippedRows,
        boolean riskyNoPk,
        List<String> diagnostics
) {
    public TableDiff {
        tableName = Objects.requireNonNull(tableName, "tableName");
        status = Objects.requireNonNull(status, "status");
        rowDiffs = List.copyOf(Objects.requireNonNull(rowDiffs, "rowDiffs"));
        screeningEvidence = List.copyOf(
                Objects.requireNonNull(screeningEvidence, "screeningEvidence")
        );
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        if (comparedRows < 0 || skippedRows < 0) {
            throw new IllegalArgumentException("row counters must not be negative");
        }
    }

    public long insertCount() {
        return rowDiffs.stream().filter(diff -> "INSERT".equals(diff.kind())).count();
    }

    public long updateCount() {
        return rowDiffs.stream().filter(diff -> "UPDATE".equals(diff.kind())).count();
    }

    public long deleteCount() {
        return rowDiffs.stream().filter(diff -> "DELETE".equals(diff.kind())).count();
    }
}
