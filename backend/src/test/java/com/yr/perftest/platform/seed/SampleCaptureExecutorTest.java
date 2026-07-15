package com.yr.perftest.platform.seed;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(DatasourceCaptureLeaseService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SampleCaptureExecutorTest {
    @Autowired
    private PersistentSeedDatasourceRepository datasourceRepository;

    @Autowired
    private PersistentSeedCaptureSampleRepository sampleRepository;

    @Autowired
    private PersistentSeedCaptureStrategyRepository strategyRepository;

    @Autowired
    private PersistentSeedCaptureSampleTableRepository tableRepository;

    @Autowired
    private PersistentSeedCaptureChunkRepository chunkRepository;

    @Autowired
    private DatasourceCaptureLeaseService leaseService;

    @TempDir
    Path temporaryDirectory;

    @Test
    void writesBoundedReadyChunksAndPersistsProgress() throws Exception {
        PersistentSeedDatasourceRecord datasource = datasourceRepository.saveAndFlush(
                new PersistentSeedDatasourceRecord(1L, "fake", "fake", 3306, "shop", "u", "p")
        );
        PersistentSeedCaptureStrategyRecord strategy = strategyRepository.saveAndFlush(
                new PersistentSeedCaptureStrategyRecord(
                        1L,
                        "capture",
                        datasource.getId(),
                        "[\"shop.orders\"]",
                        "[]",
                        2,
                        2
                )
        );
        PersistentSeedCaptureSampleRecord sample = sampleRepository.saveAndFlush(
                new PersistentSeedCaptureSampleRecord(
                        1L,
                        strategy.getId(),
                        datasource.getId(),
                        1,
                        "QUEUED",
                        Instant.now(),
                        null,
                        SeedJson.write(Map.of(
                                "strategyId", strategy.getId(),
                                "projectId", 1L,
                                "datasourceId", datasource.getId(),
                                "includes", List.of("shop.orders"),
                                "excludes", List.of(),
                                "threadCount", 2,
                                "batchRows", 2
                        )),
                        strategy.getConfigVersion()
                )
        );
        leaseService.acquire(datasource.getId(), sample.getId());

        FakeSourceFactory factory = new FakeSourceFactory(
                List.of(
                        row("id", 1, "name", "a"),
                        row("id", 2, "name", "b"),
                        row("id", 3, "name", "c"),
                        row("id", 4, "name", "d"),
                        row("id", 5, "name", "e")
                ),
                List.of("shop.orders"),
                new TableMetadata("shop.orders", List.of("id"), Set.of(), Map.of())
        );
        SampleCaptureExecutor executor = executor(factory);

        executor.capture(sample.getId());

        PersistentSeedCaptureSampleRecord completed = sampleRepository.findById(sample.getId()).orElseThrow();
        assertThat(completed.getStatus())
                .withFailMessage("capture failed: %s", completed.getErrorMessage())
                .isEqualTo("SUCCEEDED");
        assertThat(completed.getCompletedTables()).isEqualTo(1);
        assertThat(completed.getTotalTables()).isEqualTo(1);
        assertThat(completed.getCapturedRows()).isEqualTo(5);
        assertThat(completed.getWrittenBytes()).isPositive();
        assertThat(completed.getActiveWorkers()).isZero();
        assertThat(completed.getHeartbeatAt()).isAfterOrEqualTo(completed.getCaptureStartedAt());
        assertThat(chunkRepository.findBySampleIdAndTableNameOrderByChunkSeqAsc(
                sample.getId(), "shop.orders"
        )).extracting(PersistentSeedCaptureChunkRecord::getRowCount)
                .containsExactly(2L, 2L, 1L);
        assertThat(chunkRepository.findBySampleIdAndTableNameOrderByChunkSeqAsc(
                sample.getId(), "shop.orders"
        )).allMatch(chunk -> chunk.getStatus().equals("READY"));
        assertThat(tableRepository.findBySampleIdAndTableName(
                sample.getId(), "shop.orders"
        ).orElseThrow().getRiskyNoPk()).isFalse();
        assertThat(factory.maxBatchSize).isEqualTo(2);
        assertThat(Files.walk(temporaryDirectory)
                .noneMatch(path -> path.getFileName().toString().endsWith(".tmp"))).isTrue();
        assertThat(leaseService.findActiveSampleId(datasource.getId())).isEmpty();
    }

    @Test
    void finishesCurrentBatchThenCancelsAndRetainsReadyChunks() throws Exception {
        PersistentSeedDatasourceRecord datasource = datasourceRepository.saveAndFlush(
                new PersistentSeedDatasourceRecord(1L, "fake", "fake", 3306, "shop", "u", "p")
        );
        PersistentSeedCaptureStrategyRecord strategy = strategyRepository.saveAndFlush(
                new PersistentSeedCaptureStrategyRecord(
                        1L, "capture", datasource.getId(), "[\"shop.orders\"]", "[]", 1, 2
                )
        );
        PersistentSeedCaptureSampleRecord sample = sampleRepository.saveAndFlush(
                new PersistentSeedCaptureSampleRecord(
                        1L, strategy.getId(), datasource.getId(), 1, "QUEUED", Instant.now(), null,
                        SeedJson.write(Map.of(
                                "strategyId", strategy.getId(),
                                "projectId", 1L,
                                "datasourceId", datasource.getId(),
                                "includes", List.of("shop.orders"),
                                "excludes", List.of(),
                                "threadCount", 1,
                                "batchRows", 2
                        )),
                        strategy.getConfigVersion()
                )
        );
        leaseService.acquire(datasource.getId(), sample.getId());

        FakeSourceFactory factory = new FakeSourceFactory(
                List.of(row("id", 1), row("id", 2), row("id", 3), row("id", 4)),
                List.of("shop.orders"),
                new TableMetadata("shop.orders", List.of("id"), Set.of(), Map.of())
        );
        factory.cancelAfterFirstBatch = () -> {
            PersistentSeedCaptureSampleRecord current =
                    sampleRepository.findById(sample.getId()).orElseThrow();
            current.requestCancel();
            sampleRepository.saveAndFlush(current);
        };

        executor(factory).capture(sample.getId());

        PersistentSeedCaptureSampleRecord canceled = sampleRepository.findById(sample.getId()).orElseThrow();
        assertThat(canceled.getStatus())
                .withFailMessage("capture failed: %s", canceled.getErrorMessage())
                .isEqualTo("CANCELED");
        assertThat(canceled.getCapturedRows()).isEqualTo(2);
        assertThat(chunkRepository.findBySampleIdAndTableNameOrderByChunkSeqAsc(
                sample.getId(), "shop.orders"
        )).hasSize(1);
        assertThat(leaseService.findActiveSampleId(datasource.getId())).isEmpty();
    }

    @Test
    void marksNoPrimaryKeyTablesAsRiskyWhileStillCapturingRows() throws Exception {
        PersistentSeedDatasourceRecord datasource = datasourceRepository.saveAndFlush(
                new PersistentSeedDatasourceRecord(1L, "fake", "fake", 3306, "shop", "u", "p")
        );
        PersistentSeedCaptureStrategyRecord strategy = strategyRepository.saveAndFlush(
                new PersistentSeedCaptureStrategyRecord(
                        1L, "capture", datasource.getId(), "[\"shop.audit\"]", "[]", 1, 2
                )
        );
        PersistentSeedCaptureSampleRecord sample = sampleRepository.saveAndFlush(
                new PersistentSeedCaptureSampleRecord(
                        1L, strategy.getId(), datasource.getId(), 1, "QUEUED", Instant.now(), null,
                        SeedJson.write(Map.of(
                                "strategyId", strategy.getId(),
                                "projectId", 1L,
                                "datasourceId", datasource.getId(),
                                "includes", List.of("shop.audit"),
                                "excludes", List.of(),
                                "threadCount", 1,
                                "batchRows", 2
                        )),
                        strategy.getConfigVersion()
                )
        );
        leaseService.acquire(datasource.getId(), sample.getId());

        FakeSourceFactory factory = new FakeSourceFactory(
                List.of(row("message", "one"), row("message", "two"), row("message", "three")),
                List.of("shop.audit"),
                new TableMetadata("shop.audit", List.of(), Set.of(), Map.of())
        );

        executor(factory).capture(sample.getId());

        PersistentSeedCaptureSampleTableRecord table = tableRepository
                .findBySampleIdAndTableName(sample.getId(), "shop.audit")
                .orElseThrow();
        assertThat(table.getRiskyNoPk()).isTrue();
        assertThat(table.getRowCount()).isEqualTo(3);
        assertThat(table.getContentHash()).isNotBlank();
        assertThat(sampleRepository.findById(sample.getId()).orElseThrow().getStatus())
                .isEqualTo("SUCCEEDED");
    }

    private SampleCaptureExecutor executor(FakeSourceFactory factory) {
        return new SampleCaptureExecutor(
                sampleRepository,
                datasourceRepository,
                tableRepository,
                chunkRepository,
                leaseService,
                new CaptureChunkStore(temporaryDirectory),
                new DiskLowWaterGuard(temporaryDirectory, 0),
                factory,
                new CapturePartitionPlanner()
        );
    }

    private static Map<String, Object> row(Object... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            row.put((String) values[i], values[i + 1]);
        }
        return row;
    }

    private static final class FakeSourceFactory implements CaptureRowSourceFactory {
        private final List<Map<String, Object>> rows;
        private final List<String> tables;
        private final TableMetadata metadata;
        private int maxBatchSize;
        private Runnable cancelAfterFirstBatch = () -> {
        };

        private FakeSourceFactory(
                List<Map<String, Object>> rows,
                List<String> tables,
                TableMetadata metadata
        ) {
            this.rows = rows;
            this.tables = tables;
            this.metadata = metadata;
        }

        @Override
        public CaptureRowSource open(PersistentSeedDatasourceRecord datasource) {
            return new CaptureRowSource() {
                @Override
                public List<String> listTables(String database) {
                    return tables;
                }

                @Override
                public TableMetadata readMetadata(String qualifiedTable) {
                    return metadata;
                }

                @Override
                public void readBatches(
                        CapturePartitionPlanner.Partition partition,
                        int batchRows,
                        CaptureBatchConsumer consumer
                ) throws Exception {
                    for (int start = 0; start < rows.size(); start += batchRows) {
                        List<Map<String, Object>> batch = rows.subList(
                                start, Math.min(start + batchRows, rows.size())
                        );
                        maxBatchSize = Math.max(maxBatchSize, batch.size());
                        if (!consumer.accept(batch)) {
                            return;
                        }
                        if (start == 0) {
                            cancelAfterFirstBatch.run();
                        }
                    }
                }
            };
        }
    }
}
