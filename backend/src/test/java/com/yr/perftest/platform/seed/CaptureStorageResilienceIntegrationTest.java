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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(DatasourceCaptureLeaseService.class)
class CaptureStorageResilienceIntegrationTest {
    @Autowired
    private PersistentSeedDatasourceRepository datasourceRepository;

    @Autowired
    private PersistentSeedCaptureStrategyRepository strategyRepository;

    @Autowired
    private PersistentSeedCaptureSampleRepository sampleRepository;

    @Autowired
    private PersistentSeedCaptureSampleTableRepository tableRepository;

    @Autowired
    private PersistentSeedCaptureChunkRepository chunkRepository;

    @Autowired
    private PersistentSeedCaptureAnalysisRepository analysisRepository;

    @Autowired
    private PersistentSeedCaptureAnalysisInputLockRepository inputLockRepository;

    @Autowired
    private PersistentSeedCaptureDatasourceLeaseRepository leaseRepository;

    @Autowired
    private DatasourceCaptureLeaseService leaseService;

    @TempDir
    Path storage;

    @Test
    void persistsLeaseHeartbeatAndReleasesItAfterRestartReconciliation() {
        DatasourceCaptureLease acquired = leaseService.acquire(11L, 101L);
        Instant heartbeat = Instant.parse("2026-07-15T08:30:00Z");

        leaseService.heartbeat(acquired.datasourceId(), acquired.sampleId(), heartbeat);

        assertThat(leaseRepository.findByDatasourceId(11L).orElseThrow().getHeartbeatAt())
                .isEqualTo(heartbeat);
        leaseService.release(acquired);
        assertThat(leaseService.findActiveSampleId(11L)).isEmpty();
    }

    @Test
    void lowWaterCaptureFailsWithoutDeletingHistoricalChunks() throws Exception {
        PersistentSeedDatasourceRecord datasource = datasource();
        PersistentSeedCaptureStrategyRecord strategy = strategy(datasource);
        PersistentSeedCaptureSampleRecord sample = sample(strategy, "QUEUED", Instant.now());
        Path historical = storage.resolve("seed-captures/project-1/history/chunk-0.jsonl.gz");
        Files.createDirectories(historical.getParent());
        Files.writeString(historical, "historical");
        leaseService.acquire(datasource.getId(), sample.getId());

        SampleCaptureExecutor executor = new SampleCaptureExecutor(
                sampleRepository,
                datasourceRepository,
                tableRepository,
                chunkRepository,
                leaseService,
                new CaptureChunkStore(storage),
                new DiskLowWaterGuard(storage, Long.MAX_VALUE),
                ignored -> {
                    throw new AssertionError("row source must not open below low-water threshold");
                },
                new CapturePartitionPlanner()
        );

        executor.capture(sample.getId());

        assertThat(sampleRepository.findById(sample.getId()).orElseThrow().getStatus())
                .isEqualTo("FAILED");
        assertThat(sampleRepository.findById(sample.getId()).orElseThrow().getErrorMessage())
                .contains("low-water");
        assertThat(leaseService.findActiveSampleId(datasource.getId())).isEmpty();
        assertThat(historical).exists();
        assertThat(Files.readString(historical)).isEqualTo("historical");
    }

    @Test
    void marksDamagedChunkIncompleteInsteadOfTreatingItAsEmpty() throws Exception {
        PersistentSeedDatasourceRecord datasource = datasource();
        PersistentSeedCaptureStrategyRecord strategy = strategy(datasource);
        PersistentSeedCaptureSampleRecord sample = sample(
                strategy,
                "SUCCEEDED",
                Instant.parse("2026-07-15T08:00:00Z")
        );
        TableMetadata metadata = new TableMetadata(
                "shop.orders",
                List.of("id"),
                Set.of("id"),
                Map.of()
        );
        CaptureChunkStore store = new CaptureChunkStore(storage);
        CaptureChunkStore.ChunkManifest manifest;
        try (CaptureChunkStore.ChunkWriter writer = store.openChunk(
                sample.getProjectId(),
                strategy.getId(),
                sample.getId(),
                "shop.orders",
                0
        )) {
            writer.writeRow(row(1, "one"));
            manifest = writer.commit();
        }
        Files.write(store.resolveRelativePath(manifest.relativePath()), new byte[]{1, 2, 3});
        tableRepository.saveAndFlush(new PersistentSeedCaptureSampleTableRecord(
                sample.getId(),
                "shop.orders",
                SeedJson.write(metadata),
                "schema",
                1,
                "table",
                false,
                "SUCCEEDED"
        ));
        chunkRepository.saveAndFlush(new PersistentSeedCaptureChunkRecord(
                sample.getId(),
                "shop.orders",
                0,
                null,
                null,
                1,
                manifest.contentHash(),
                manifest.relativePath(),
                manifest.fileChecksum(),
                "READY",
                manifest.byteSize()
        ));

        Map<String, Object> page = new SeedCaptureSampleService(
                sampleRepository,
                tableRepository,
                chunkRepository,
                null,
                null,
                store
        ).readRows(1L, sample.getId(), "shop.orders", null, 10);

        assertThat(page.get("rows")).isEqualTo(List.of());
        assertThat(page.get("incomplete")).isEqualTo(true);
        assertThat(page.get("checksumValid")).isEqualTo(false);
    }

    @Test
    void retriesDeletionAfterAFileCleanupFailure() throws Exception {
        PersistentSeedDatasourceRecord datasource = datasource();
        PersistentSeedCaptureStrategyRecord strategy = strategy(datasource);
        PersistentSeedCaptureSampleRecord sample = sample(
                strategy,
                "SUCCEEDED",
                Instant.parse("2026-07-15T08:00:00Z")
        );
        CaptureChunkStore store = new CaptureChunkStore(storage);
        String blockedPath = "seed-captures/project-1/strategy-" + strategy.getId()
                + "/sample-" + sample.getId() + "/shop.orders/blocked";
        Path blocked = store.resolveRelativePath(blockedPath);
        Files.createDirectories(blocked);
        Path child = blocked.resolve("keep");
        Files.writeString(child, "keep");
        tableRepository.saveAndFlush(new PersistentSeedCaptureSampleTableRecord(
                sample.getId(),
                "shop.orders",
                "{}",
                "schema",
                0,
                "table",
                false,
                "SUCCEEDED"
        ));
        chunkRepository.saveAndFlush(new PersistentSeedCaptureChunkRecord(
                sample.getId(),
                "shop.orders",
                0,
                null,
                null,
                0,
                "content",
                blockedPath,
                "checksum",
                "READY",
                0
        ));
        SeedCaptureSampleService service = new SeedCaptureSampleService(
                sampleRepository,
                tableRepository,
                chunkRepository,
                analysisRepository,
                inputLockRepository,
                store
        );

        Map<String, Object> first = service.delete(1L, sample.getId());

        assertThat(first.get("status")).isEqualTo("DELETING");
        assertThat(sampleRepository.findById(sample.getId()).orElseThrow().getErrorMessage())
                .isNotBlank();
        Files.delete(child);

        assertThat(service.delete(1L, sample.getId()).get("status")).isEqualTo("DELETED");
        assertThat(sampleRepository.findById(sample.getId())).isEmpty();
        assertThat(blocked).doesNotExist();
    }

    @Test
    void restartMarksExpiredCaptureInterruptedRetainsCommittedChunkAndRemovesOrphanTmp() throws Exception {
        PersistentSeedDatasourceRecord datasource = datasource();
        PersistentSeedCaptureStrategyRecord strategy = strategy(datasource);
        Instant now = Instant.parse("2026-07-15T09:00:00Z");
        PersistentSeedCaptureSampleRecord sample = sample(
                strategy,
                "CAPTURING",
                now.minus(Duration.ofMinutes(10))
        );
        CaptureChunkStore store = new CaptureChunkStore(storage);
        CaptureChunkStore.ChunkWriter writer = store.openChunk(
                sample.getProjectId(),
                strategy.getId(),
                sample.getId(),
                "shop.orders",
                0
        );
        writer.writeRow(row(1, "ready"));
        CaptureChunkStore.ChunkManifest manifest = writer.commit();
        Path orphan = store.resolveRelativePath(
                "seed-captures/project-1/strategy-" + strategy.getId()
                        + "/sample-" + sample.getId()
                        + "/shop.orders/chunk-1.jsonl.gz.tmp"
        );
        Files.createDirectories(orphan.getParent());
        Files.writeString(orphan, "orphan");
        leaseService.acquire(datasource.getId(), sample.getId());

        new CaptureStartupReconciliation(
                sampleRepository,
                leaseService,
                store,
                Duration.ofMinutes(5)
        ).reconcile(now);

        assertThat(sampleRepository.findById(sample.getId()).orElseThrow().getStatus())
                .isEqualTo("INTERRUPTED");
        assertThat(store.resolveRelativePath(manifest.relativePath())).exists();
        assertThat(orphan).doesNotExist();
        assertThat(leaseService.findActiveSampleId(datasource.getId())).isEmpty();
    }

    private PersistentSeedDatasourceRecord datasource() {
        return datasourceRepository.saveAndFlush(
                new PersistentSeedDatasourceRecord(1L, "fake", "fake", 3306, "shop", "u", "p")
        );
    }

    private PersistentSeedCaptureStrategyRecord strategy(
            PersistentSeedDatasourceRecord datasource
    ) {
        return strategyRepository.saveAndFlush(new PersistentSeedCaptureStrategyRecord(
                1L,
                "capture",
                datasource.getId(),
                "[\"shop.orders\"]",
                "[]",
                1,
                100
        ));
    }

    private PersistentSeedCaptureSampleRecord sample(
            PersistentSeedCaptureStrategyRecord strategy,
            String status,
            Instant startedAt
    ) {
        return sampleRepository.saveAndFlush(new PersistentSeedCaptureSampleRecord(
                1L,
                strategy.getId(),
                strategy.getDatasourceId(),
                sampleRepository.findNextSampleSeq(strategy.getId()),
                status,
                startedAt,
                "SUCCEEDED".equals(status) ? startedAt.plusSeconds(10) : null,
                SeedJson.write(Map.of(
                        "includes", List.of("shop.orders"),
                        "excludes", List.of(),
                        "threadCount", 1,
                        "batchRows", 100
                )),
                strategy.getConfigVersion()
        ));
    }

    private static Map<String, Object> row(int id, String name) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("name", name);
        return row;
    }
}
