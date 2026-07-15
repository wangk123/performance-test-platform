package com.yr.perftest.platform.seed;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.FileStore;
import java.nio.file.Path;
import java.util.Objects;

public final class DiskLowWaterGuard {
    public static final long DEFAULT_LOW_WATER_BYTES = 1024L * 1024L * 1024L;

    private final Path storageRoot;
    private final long lowWaterBytes;

    public DiskLowWaterGuard(Path storageRoot, long lowWaterBytes) {
        if (lowWaterBytes < 0) {
            throw new IllegalArgumentException("low-water threshold must not be negative");
        }
        this.storageRoot = Objects.requireNonNull(storageRoot, "storageRoot")
                .toAbsolutePath()
                .normalize();
        this.lowWaterBytes = lowWaterBytes;
        try {
            Files.createDirectories(this.storageRoot);
        } catch (IOException ex) {
            throw new IllegalStateException("cannot create storage root", ex);
        }
    }

    public DiskLowWaterGuard(Path storageRoot) {
        this(storageRoot, DEFAULT_LOW_WATER_BYTES);
    }

    public void checkBeforeStart() {
        check("before start");
    }

    public void checkDuringRun() {
        check("during run");
    }

    public long usableBytes() {
        try {
            FileStore fileStore = Files.getFileStore(storageRoot);
            return fileStore.getUsableSpace();
        } catch (IOException ex) {
            throw new LowWaterException("cannot determine storage capacity", ex);
        }
    }

    public long lowWaterBytes() {
        return lowWaterBytes;
    }

    private void check(String phase) {
        long usable = usableBytes();
        if (usable < lowWaterBytes) {
            throw new LowWaterException(
                    "storage low-water threshold reached " + phase
                            + ": usable=" + usable + ", threshold=" + lowWaterBytes
            );
        }
    }

    public static class LowWaterException extends IllegalStateException {
        public LowWaterException(String message) {
            super(message);
        }

        public LowWaterException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
