package com.yr.perftest.platform.seed;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiskLowWaterGuardTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void rejectsBeforeStartWithoutDeletingHistoricalFiles() throws Exception {
        Path historicalFile = createHistoricalFile();
        DiskLowWaterGuard guard = new DiskLowWaterGuard(temporaryDirectory, Long.MAX_VALUE);

        assertThatThrownBy(guard::checkBeforeStart)
                .isInstanceOf(DiskLowWaterGuard.LowWaterException.class);

        assertThat(historicalFile).exists();
        assertThat(Files.readString(historicalFile)).isEqualTo("historical");
    }

    @Test
    void failsDuringRunWithoutDeletingHistoricalFiles() throws Exception {
        Path historicalFile = createHistoricalFile();
        DiskLowWaterGuard guard = new DiskLowWaterGuard(temporaryDirectory, Long.MAX_VALUE);

        assertThatThrownBy(guard::checkDuringRun)
                .isInstanceOf(DiskLowWaterGuard.LowWaterException.class);

        assertThat(historicalFile).exists();
        assertThat(Files.readString(historicalFile)).isEqualTo("historical");
    }

    @Test
    void permitsExecutionWhenTheConfiguredLowWaterThresholdIsAvailable() {
        DiskLowWaterGuard guard = new DiskLowWaterGuard(temporaryDirectory, 0);

        guard.checkBeforeStart();
        guard.checkDuringRun();
        assertThat(guard.usableBytes()).isGreaterThanOrEqualTo(0);
    }

    private Path createHistoricalFile() throws Exception {
        Path file = temporaryDirectory.resolve(
                "seed-captures/project-1/strategy-2/sample-3/orders/chunk-1.jsonl.gz"
        );
        Files.createDirectories(file.getParent());
        Files.writeString(file, "historical");
        return file;
    }
}
