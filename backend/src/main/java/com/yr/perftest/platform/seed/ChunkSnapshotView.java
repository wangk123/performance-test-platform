package com.yr.perftest.platform.seed;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;

public record ChunkSnapshotView(
        int chunkSeq,
        String pkRangeStart,
        String pkRangeEnd,
        long rowCount,
        String contentHash,
        boolean checksumVerified,
        Supplier<Stream<Map<String, Object>>> rowSource
) {
    public ChunkSnapshotView {
        if (chunkSeq < 0 || rowCount < 0) {
            throw new IllegalArgumentException("chunk sequence and row count must not be negative");
        }
        rowSource = Objects.requireNonNull(rowSource, "rowSource");
    }

    public static ChunkSnapshotView fromRows(
            int chunkSeq,
            String pkRangeStart,
            String pkRangeEnd,
            List<? extends Map<String, ?>> rows
    ) {
        List<Map<String, Object>> copy = rows.stream()
                .map(ChunkSnapshotView::copyRow)
                .toList();
        String contentHash = LogicalFingerprint.chunkFingerprint(copy);
        return new ChunkSnapshotView(
                chunkSeq,
                pkRangeStart,
                pkRangeEnd,
                copy.size(),
                contentHash,
                true,
                () -> copy.stream().map(ChunkSnapshotView::copyRow)
        );
    }

    public static ChunkSnapshotView fromManifest(
            int chunkSeq,
            String pkRangeStart,
            String pkRangeEnd,
            CaptureChunkStore store,
            CaptureChunkStore.ChunkManifest manifest
    ) {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(manifest, "manifest");
        return new ChunkSnapshotView(
                chunkSeq,
                pkRangeStart,
                pkRangeEnd,
                manifest.rowCount(),
                manifest.contentHash(),
                manifest.fileChecksum() != null,
                () -> store.readRows(manifest)
        );
    }

    private static Map<String, Object> copyRow(Map<String, ?> row) {
        return row.entrySet().stream().collect(
                java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> right,
                        java.util.LinkedHashMap::new
                )
        );
    }
}
