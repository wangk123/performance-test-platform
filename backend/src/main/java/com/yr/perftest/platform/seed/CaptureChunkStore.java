package com.yr.perftest.platform.seed;

import com.fasterxml.jackson.core.type.TypeReference;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class CaptureChunkStore {
    private static final TypeReference<Map<String, Object>> ROW_TYPE = new TypeReference<>() {
    };

    private final Path root;

    public CaptureChunkStore(Path storageRoot) {
        this.root = Objects.requireNonNull(storageRoot, "storageRoot").toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new IllegalStateException("cannot create capture storage root", ex);
        }
    }

    public CaptureChunkStore(String storageRoot) {
        this(Path.of(storageRoot));
    }

    public Path resolveChunkPath(long projectId, long strategyId, long sampleId, String tableName, int chunkSeq) {
        return resolveRelativePath(relativeChunkPath(projectId, strategyId, sampleId, tableName, chunkSeq));
    }

    public Path resolveAnalysisChunkPath(
            long projectId,
            long strategyId,
            long analysisId,
            String tableName,
            int chunkSeq
    ) {
        return resolveRelativePath(
                relativeAnalysisChunkPath(projectId, strategyId, analysisId, tableName, chunkSeq)
        );
    }

    public String relativeChunkPath(long projectId, long strategyId, long sampleId, String tableName, int chunkSeq) {
        validateIdentifiers(projectId, strategyId, sampleId, chunkSeq);
        return chunkPath(projectId, strategyId, "sample-" + sampleId, tableName, chunkSeq);
    }

    public String relativeAnalysisChunkPath(
            long projectId,
            long strategyId,
            long analysisId,
            String tableName,
            int chunkSeq
    ) {
        validateIdentifiers(projectId, strategyId, analysisId, chunkSeq);
        return chunkPath(projectId, strategyId, "analysis-" + analysisId, tableName, chunkSeq);
    }

    private String chunkPath(
            long projectId,
            long strategyId,
            String artifactDirectory,
            String tableName,
            int chunkSeq
    ) {
        validateSegment(tableName, "table name");
        return Path.of(
                "seed-captures",
                "project-" + projectId,
                "strategy-" + strategyId,
                artifactDirectory,
                tableName,
                "chunk-" + chunkSeq + ".jsonl.gz"
        ).toString();
    }

    public Path resolveRelativePath(String relativePath) {
        try {
            String pathValue = Objects.requireNonNull(relativePath, "relativePath");
            if (pathValue.isBlank()) {
                throw new IllegalArgumentException("storage path is empty");
            }
            Path relative = Path.of(pathValue);
            if (relative.isAbsolute()) {
                throw new IllegalArgumentException("absolute storage paths are not allowed");
            }
            Path normalized = relative.normalize();
            if (normalized.getNameCount() == 0 || normalized.startsWith(Path.of(".."))) {
                throw new IllegalArgumentException("storage path escapes the root");
            }
            Path resolved = root.resolve(normalized).normalize();
            if (!resolved.startsWith(root)) {
                throw new IllegalArgumentException("storage path escapes the root");
            }
            return resolved;
        } catch (InvalidPathException ex) {
            throw new IllegalArgumentException("invalid storage path", ex);
        }
    }

    public ChunkWriter openChunk(
            long projectId,
            long strategyId,
            long sampleId,
            String tableName,
            int chunkSeq
    ) {
        return openChunk(relativeChunkPath(projectId, strategyId, sampleId, tableName, chunkSeq));
    }

    public ChunkWriter openAnalysisChunk(
            long projectId,
            long strategyId,
            long analysisId,
            String tableName,
            int chunkSeq
    ) {
        return openChunk(relativeAnalysisChunkPath(projectId, strategyId, analysisId, tableName, chunkSeq));
    }

    private ChunkWriter openChunk(String relativePath) {
        Path finalPath = resolveRelativePath(relativePath);
        Path temporaryPath = finalPath.resolveSibling(finalPath.getFileName() + ".tmp");
        try {
            Files.createDirectories(finalPath.getParent());
            if (Files.exists(finalPath)) {
                throw new IllegalStateException("capture chunk already exists: " + relativePath);
            }
            Files.deleteIfExists(temporaryPath);
            OutputStream output = Files.newOutputStream(
                    temporaryPath,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            );
            GZIPOutputStream gzip = new GZIPOutputStream(new java.io.BufferedOutputStream(output));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(gzip, StandardCharsets.UTF_8));
            return new ChunkWriter(relativePath, finalPath, temporaryPath, writer);
        } catch (IOException ex) {
            throw new IllegalStateException("cannot open capture chunk", ex);
        }
    }

    private static void validateIdentifiers(long projectId, long strategyId, long artifactId, int chunkSeq) {
        if (projectId < 0 || strategyId < 0 || artifactId < 0 || chunkSeq < 0) {
            throw new IllegalArgumentException("capture identifiers must not be negative");
        }
    }

    public void cleanupTemporaryFiles() {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".tmp"))
                    .forEach(this::deleteTemporaryFile);
        } catch (IOException ex) {
            throw new IllegalStateException("cannot clean temporary capture files", ex);
        }
    }

    public boolean verifyChecksum(ChunkManifest manifest) {
        Objects.requireNonNull(manifest, "manifest");
        Path path = resolveRelativePath(manifest.relativePath());
        if (!Files.isRegularFile(path) || manifest.fileChecksum() == null) {
            return false;
        }
        return manifest.fileChecksum().equals(checksum(path));
    }

    public String checksum(Path path) {
        Path containedPath = containedPath(path);
        try (InputStream input = new BufferedInputStream(Files.newInputStream(containedPath))) {
            MessageDigest digest = sha256Digest();
            try (DigestInputStream digestInput = new DigestInputStream(input, digest)) {
                byte[] buffer = new byte[8192];
                while (digestInput.read(buffer) != -1) {
                }
            }
            return hex(digest.digest());
        } catch (IOException ex) {
            throw new IllegalStateException("cannot checksum capture chunk", ex);
        }
    }

    public Stream<Map<String, Object>> readRows(ChunkManifest manifest) {
        Objects.requireNonNull(manifest, "manifest");
        if (!verifyChecksum(manifest)) {
            throw new ChecksumMismatchException(manifest.relativePath());
        }
        Path path = resolveRelativePath(manifest.relativePath());
        try {
            InputStream input = Files.newInputStream(path);
            GZIPInputStream gzip = new GZIPInputStream(new BufferedInputStream(input));
            BufferedReader reader = new BufferedReader(new InputStreamReader(gzip, StandardCharsets.UTF_8));
            return reader.lines()
                    .filter(line -> !line.isEmpty())
                    .map(line -> SeedJson.read(line, ROW_TYPE))
                    .onClose(() -> closeReader(reader));
        } catch (IOException ex) {
            throw new IllegalStateException("cannot read capture chunk", ex);
        }
    }

    private Path containedPath(Path path) {
        Path normalized = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) {
            throw new IllegalArgumentException("path escapes storage root");
        }
        return normalized;
    }

    private void deleteTemporaryFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            throw new IllegalStateException("cannot delete temporary capture file", ex);
        }
    }

    private static void closeReader(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException ex) {
            throw new IllegalStateException("cannot close capture chunk", ex);
        }
    }

    private static void validateSegment(String value, String label) {
        if (value == null || value.isBlank() || value.equals(".") || value.equals("..")
                || value.indexOf('/') >= 0 || value.indexOf('\\') >= 0) {
            throw new IllegalArgumentException(label + " is invalid");
        }
        try {
            Path segment = Path.of(value);
            if (segment.isAbsolute() || segment.getNameCount() != 1) {
                throw new IllegalArgumentException(label + " must be one path segment");
            }
        } catch (InvalidPathException ex) {
            throw new IllegalArgumentException(label + " is invalid", ex);
        }
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(Character.forDigit((value >>> 4) & 0xF, 16));
            result.append(Character.forDigit(value & 0xF, 16));
        }
        return result.toString();
    }

    public final class ChunkWriter implements AutoCloseable {
        private final String relativePath;
        private final Path finalPath;
        private final Path temporaryPath;
        private final BufferedWriter writer;
        private final LogicalFingerprint.ChunkAccumulator fingerprint =
                LogicalFingerprint.newChunkAccumulator();
        private boolean closed;
        private boolean committed;

        private ChunkWriter(
                String relativePath,
                Path finalPath,
                Path temporaryPath,
                BufferedWriter writer
        ) {
            this.relativePath = relativePath;
            this.finalPath = finalPath;
            this.temporaryPath = temporaryPath;
            this.writer = writer;
        }

        public void writeRow(Map<String, ?> row) {
            ensureOpen();
            String json = SeedJson.write(row);
            fingerprint.addRow(row);
            try {
                writer.write(json);
                writer.newLine();
            } catch (IOException ex) {
                throw new IllegalStateException("cannot write capture chunk", ex);
            }
        }

        public ChunkManifest commit() {
            ensureOpen();
            try {
                writer.flush();
                writer.close();
                closed = true;
                String fileChecksum = checksum(temporaryPath);
                long byteSize = Files.size(temporaryPath);
                String contentHash = fingerprint.finish();
                try {
                    Files.move(temporaryPath, finalPath, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(temporaryPath, finalPath);
                }
                committed = true;
                return new ChunkManifest(
                        relativePath,
                        fingerprint.rowCount(),
                        contentHash,
                        fileChecksum,
                        byteSize
                );
            } catch (IOException ex) {
                deleteTemporaryFile(temporaryPath);
                throw new IllegalStateException("cannot publish capture chunk", ex);
            } catch (RuntimeException ex) {
                deleteTemporaryFile(temporaryPath);
                throw ex;
            }
        }

        public Path temporaryPath() {
            return temporaryPath;
        }

        public String relativePath() {
            return relativePath;
        }

        @Override
        public void close() {
            RuntimeException closeFailure = null;
            if (!closed) {
                try {
                    writer.close();
                } catch (IOException ex) {
                    closeFailure = new IllegalStateException("cannot close capture chunk", ex);
                } finally {
                    closed = true;
                }
            }
            if (!committed) {
                deleteTemporaryFile(temporaryPath);
            }
            if (closeFailure != null) {
                throw closeFailure;
            }
        }

        private void ensureOpen() {
            if (closed || committed) {
                throw new IllegalStateException("capture chunk writer is closed");
            }
        }
    }

    public record ChunkManifest(
            String relativePath,
            long rowCount,
            String contentHash,
            String fileChecksum,
            long byteSize
    ) {
    }

    public static final class ChecksumMismatchException extends IllegalStateException {
        public ChecksumMismatchException(String relativePath) {
            super("capture chunk checksum mismatch: " + relativePath);
        }
    }
}
