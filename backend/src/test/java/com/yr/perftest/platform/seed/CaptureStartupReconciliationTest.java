package com.yr.perftest.platform.seed;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(DatasourceCaptureLeaseService.class)
class CaptureStartupReconciliationTest {
    @Autowired
    private PersistentSeedDatasourceRepository datasourceRepository;

    @Autowired
    private PersistentSeedCaptureSampleRepository sampleRepository;

    @Autowired
    private PersistentSeedCaptureStrategyRepository strategyRepository;

    @Autowired
    private DatasourceCaptureLeaseService leaseService;

    @TempDir
    Path temporaryDirectory;

    @Test
    void interruptsExpiredCaptureReleasesLeaseAndCleansOrphanTemporaryFiles() throws Exception {
        PersistentSeedDatasourceRecord datasource = datasourceRepository.saveAndFlush(
                new PersistentSeedDatasourceRecord(1L, "fake", "fake", 3306, "shop", "u", "p")
        );
        PersistentSeedCaptureStrategyRecord strategy = strategyRepository.saveAndFlush(
                new PersistentSeedCaptureStrategyRecord(
                        1L, "capture", datasource.getId(), "[\"shop.orders\"]", "[]", 1, 100
                )
        );
        Instant now = Instant.parse("2026-07-15T08:00:00Z");
        PersistentSeedCaptureSampleRecord sample = sampleRepository.saveAndFlush(
                new PersistentSeedCaptureSampleRecord(
                        1L,
                        strategy.getId(),
                        datasource.getId(),
                        1,
                        "CAPTURING",
                        now.minus(Duration.ofMinutes(10)),
                        null,
                        SeedJson.write(Map.of(
                                "includes", List.of("shop.orders"),
                                "excludes", List.of(),
                                "threadCount", 1,
                                "batchRows", 100
                        )),
                        strategy.getConfigVersion()
                )
        );
        Path orphan = temporaryDirectory.resolve(
                "seed-captures/project-1/strategy-" + strategy.getId()
                        + "/sample-" + sample.getId() + "/shop.orders/chunk-1.jsonl.gz.tmp"
        );
        Files.createDirectories(orphan.getParent());
        Files.writeString(orphan, "orphan");
        leaseService.acquire(datasource.getId(), sample.getId());

        new CaptureStartupReconciliation(
                sampleRepository,
                leaseService,
                new CaptureChunkStore(temporaryDirectory),
                Duration.ofMinutes(5)
        ).reconcile(now);

        PersistentSeedCaptureSampleRecord interrupted = sampleRepository.findById(sample.getId()).orElseThrow();
        assertThat(interrupted.getStatus()).isEqualTo("INTERRUPTED");
        assertThat(interrupted.getIncomplete()).isTrue();
        assertThat(interrupted.getErrorMessage()).contains("heartbeat");
        assertThat(leaseService.findActiveSampleId(datasource.getId())).isEmpty();
        assertThat(orphan).doesNotExist();
    }
}
