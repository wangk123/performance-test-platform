package com.yr.perftest.platform.seed;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record TableSnapshotView(
        String tableName,
        TableMetadata metadata,
        String schemaHash,
        long rowCount,
        String contentHash,
        boolean complete,
        List<ChunkSnapshotView> chunks
) {
    public TableSnapshotView {
        tableName = Objects.requireNonNull(tableName, "tableName");
        if (rowCount < 0) {
            throw new IllegalArgumentException("table row count must not be negative");
        }
        List<ChunkSnapshotView> copy = List.copyOf(Objects.requireNonNull(chunks, "chunks"));
        chunks = Collections.unmodifiableList(copy);
    }

    public TableSnapshotView(
            String tableName,
            TableMetadata metadata,
            String schemaHash,
            long rowCount,
            String contentHash,
            List<ChunkSnapshotView> chunks
    ) {
        this(tableName, metadata, schemaHash, rowCount, contentHash, true, chunks);
    }

    public static TableSnapshotView fromRows(
            String tableName,
            TableMetadata metadata,
            List<? extends Map<String, ?>> rows,
            int rowsPerChunk
    ) {
        if (rowsPerChunk <= 0) {
            throw new IllegalArgumentException("rowsPerChunk must be positive");
        }
        List<ChunkSnapshotView> chunks = new java.util.ArrayList<>();
        for (int start = 0, seq = 0; start < rows.size(); start += rowsPerChunk, seq++) {
            int end = Math.min(start + rowsPerChunk, rows.size());
            chunks.add(ChunkSnapshotView.fromRows(seq, null, null, rows.subList(start, end)));
        }
        String schemaHash = LogicalFingerprint.schemaFingerprint(
                metadata == null ? List.of() : metadata.primaryKeyColumns()
        );
        List<Map<String, Object>> copiedRows = rows.stream()
                .map(TableSnapshotView::copyRow)
                .toList();
        String contentHash = metadata != null
                && metadata.primaryKeyColumns() != null
                && metadata.primaryKeyColumns().isEmpty()
                ? LogicalFingerprint.multisetFingerprint(copiedRows)
                : LogicalFingerprint.chunkFingerprint(copiedRows);
        return new TableSnapshotView(
                tableName,
                metadata,
                schemaHash,
                rows.size(),
                LogicalFingerprint.tableFingerprint(schemaHash, rows.size(), contentHash),
                true,
                chunks
        );
    }

    private static Map<String, Object> copyRow(Map<String, ?> row) {
        Map<String, Object> copy = new LinkedHashMap<>();
        row.forEach(copy::put);
        return copy;
    }
}
