package com.yr.perftest.platform.seed;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CaptureChunkStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void cleansUncommittedTemporaryFiles() throws Exception {
        CaptureChunkStore store = new CaptureChunkStore(temporaryDirectory);
        CaptureChunkStore.ChunkWriter writer = store.openChunk(1L, 2L, 3L, "orders", 1);
        Path temporaryPath = writer.temporaryPath();
        writer.writeRow(row("id", 1));

        writer.close();
        assertThat(temporaryPath).doesNotExist();

        Path orphan = store.resolveRelativePath("seed-captures/project-1/strategy-2/sample-3/"
                + "orders/chunk-2.jsonl.gz.tmp");
        Files.createDirectories(orphan.getParent());
        Files.writeString(orphan, "orphan");
        store.cleanupTemporaryFiles();

        assertThat(orphan).doesNotExist();
    }

    @Test
    void publishesOnlyAfterTemporaryFileIsChecksummed() throws Exception {
        CaptureChunkStore store = new CaptureChunkStore(temporaryDirectory);
        CaptureChunkStore.ChunkWriter writer = store.openChunk(7L, 8L, 9L, "orders", 4);
        Path finalPath = store.resolveChunkPath(7L, 8L, 9L, "orders", 4);

        writer.writeRow(row("id", 1, "name", "alice"));
        assertThat(writer.temporaryPath()).exists();
        assertThat(finalPath).doesNotExist();

        CaptureChunkStore.ChunkManifest manifest = writer.commit();

        assertThat(writer.temporaryPath()).doesNotExist();
        assertThat(finalPath).exists();
        assertThat(manifest.relativePath())
                .isEqualTo("seed-captures/project-7/strategy-8/sample-9/orders/chunk-4.jsonl.gz");
        assertThat(manifest.fileChecksum()).isNotBlank();
        assertThat(manifest.contentHash()).isEqualTo(
                LogicalFingerprint.chunkFingerprint(java.util.List.of(row("id", 1, "name", "alice")))
        );
    }

    @Test
    void storesAnalysisChunksUnderTheAnalysisLayout() {
        CaptureChunkStore store = new CaptureChunkStore(temporaryDirectory);
        CaptureChunkStore.ChunkWriter writer = store.openAnalysisChunk(7L, 8L, 10L, "orders", 2);

        writer.writeRow(row("id", 1));
        CaptureChunkStore.ChunkManifest manifest = writer.commit();

        assertThat(manifest.relativePath())
                .isEqualTo("seed-captures/project-7/strategy-8/analysis-10/orders/chunk-2.jsonl.gz");
        assertThat(store.resolveAnalysisChunkPath(7L, 8L, 10L, "orders", 2)).exists();
    }

    @Test
    void detectsChecksumTamperingBeforeReadingRows() throws Exception {
        CaptureChunkStore store = new CaptureChunkStore(temporaryDirectory);
        CaptureChunkStore.ChunkWriter writer = store.openChunk(1L, 2L, 3L, "orders", 1);
        writer.writeRow(row("id", 1));
        CaptureChunkStore.ChunkManifest manifest = writer.commit();
        Path finalPath = store.resolveRelativePath(manifest.relativePath());
        Files.write(finalPath, new byte[]{1, 2, 3});

        assertThat(store.verifyChecksum(manifest)).isFalse();
        assertThatThrownBy(() -> store.readRows(manifest).count())
                .isInstanceOf(CaptureChunkStore.ChecksumMismatchException.class);
    }

    @Test
    void readsJsonlGzipRowsAsAClosedBoundedStream() throws Exception {
        CaptureChunkStore store = new CaptureChunkStore(temporaryDirectory);
        CaptureChunkStore.ChunkWriter writer = store.openChunk(1L, 2L, 3L, "orders", 1);
        for (int i = 0; i < 100; i++) {
            writer.writeRow(row("id", i, "name", "user-" + i));
        }
        CaptureChunkStore.ChunkManifest manifest = writer.commit();

        try (Stream<Map<String, Object>> rows = store.readRows(manifest)) {
            assertThat(rows.limit(3).map(row -> row.get("id")).toList())
                    .containsExactly(0, 1, 2);
        }
    }

    @Test
    void rejectsRelativePathsThatEscapeStorageRoot() {
        CaptureChunkStore store = new CaptureChunkStore(temporaryDirectory);

        assertThatThrownBy(() -> store.resolveRelativePath("../outside.jsonl.gz"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.resolveRelativePath("/outside.jsonl.gz"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.openChunk(1L, 2L, 3L, "../orders", 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Map<String, Object> row(Object... values) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            row.put((String) values[i], values[i + 1]);
        }
        return row;
    }
}
