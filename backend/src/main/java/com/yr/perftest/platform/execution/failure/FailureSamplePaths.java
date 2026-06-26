package com.yr.perftest.platform.execution.failure;

import java.nio.file.Files;
import java.nio.file.Path;

public final class FailureSamplePaths {
    public static final String JSONL_FILE = "failure-samples.jsonl";
    public static final String SQLITE_FILE = "failure-samples.db";

    private FailureSamplePaths() {
    }

    public static Path jsonl(Path executionDirectory) {
        return executionDirectory.resolve(JSONL_FILE);
    }

    public static Path sqlite(Path executionDirectory) {
        return executionDirectory.resolve(SQLITE_FILE);
    }

    public static Path executionDirectory(Path logFilePath) {
        return logFilePath.getParent();
    }

    public static void deleteArtifacts(Path logFilePath) {
        if (logFilePath == null) {
            return;
        }
        Path directory = executionDirectory(logFilePath);
        deleteIfExists(jsonl(directory));
        deleteIfExists(sqlite(directory));
    }

    private static void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
        }
    }
}
