package com.yr.perftest.platform.api;

import com.yr.perftest.platform.seed.DatasourceCaptureLeaseService;
import com.yr.perftest.platform.seed.CaptureChunkStore;
import com.yr.perftest.platform.seed.PersistentSeedCaptureChunkRecord;
import com.yr.perftest.platform.seed.PersistentSeedCaptureAnalysisInputLockRecord;
import com.yr.perftest.platform.seed.PersistentSeedCaptureAnalysisInputLockRepository;
import com.yr.perftest.platform.seed.PersistentSeedCaptureAnalysisRecord;
import com.yr.perftest.platform.seed.PersistentSeedCaptureAnalysisRepository;
import com.yr.perftest.platform.seed.PersistentSeedCaptureSampleRecord;
import com.yr.perftest.platform.seed.PersistentSeedCaptureSampleTableRecord;
import com.yr.perftest.platform.seed.PersistentSeedCaptureSampleTableRepository;
import com.yr.perftest.platform.seed.PersistentSeedCaptureSampleRepository;
import com.yr.perftest.platform.seed.PersistentSeedCaptureStrategyRecord;
import com.yr.perftest.platform.seed.PersistentSeedCaptureStrategyRepository;
import com.yr.perftest.platform.seed.PersistentSeedCaptureChunkRepository;
import com.yr.perftest.platform.seed.SeedJson;
import com.yr.perftest.platform.seed.TableMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:capture-sample-api-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false",
        "platform.storage.root=./build/test-storage/capture-sample-api",
        "platform.seed.disk-low-water-bytes=0"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class CaptureSampleApiBehaviorTest {
    @BeforeEach
    void cleanStorage() throws IOException {
        Path root = Path.of("build/test-storage/capture-sample-api");
        if (Files.exists(root)) {
            try (Stream<Path> paths = Files.walk(root)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ex) {
                        throw new IllegalStateException("failed to clean test storage", ex);
                    }
                });
            }
        }
    }

    @Autowired
    private MockMvc mockMvc;

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
    private CaptureChunkStore chunkStore;

    @Autowired
    private DatasourceCaptureLeaseService leaseService;

    @Test
    void returnsPersistedProgressAndRequestsCooperativeCancellation() throws Exception {
        createProject();
        createDatasource();
        createStrategy();
        PersistentSeedCaptureStrategyRecord strategy = strategyRepository.findById(1L).orElseThrow();
        PersistentSeedCaptureSampleRecord sample = sampleRepository.saveAndFlush(
                new PersistentSeedCaptureSampleRecord(
                        1L,
                        strategy.getId(),
                        strategy.getDatasourceId(),
                        1,
                        "CAPTURING",
                        Instant.now(),
                        null,
                        "{}",
                        strategy.getConfigVersion()
                )
        );
        sample.updateProgress(
                "CAPTURING",
                2,
                4,
                "[\"shop.orders\"]",
                30,
                4096,
                1,
                Instant.now()
        );
        sampleRepository.saveAndFlush(sample);
        leaseService.acquire(strategy.getDatasourceId(), sample.getId());

        mockMvc.perform(get("/api/projects/1/seed/samples/" + sample.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CAPTURING")))
                .andExpect(jsonPath("$.completedTables", is(2)))
                .andExpect(jsonPath("$.capturedRows", is(30)))
                .andExpect(jsonPath("$.activeWorkers", is(1)));

        mockMvc.perform(post("/api/projects/1/seed/samples/" + sample.getId() + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", is("CANCEL_REQUESTED")));
    }

    @Test
    void pagesSampleHistoryByCaptureTimeStatusAndTimeAndKeepsProgressAfterReload() throws Exception {
        createProject();
        createDatasource();
        createStrategy();
        PersistentSeedCaptureStrategyRecord strategy = strategyRepository.findById(1L).orElseThrow();
        Instant sameStart = Instant.parse("2026-07-15T06:00:00Z");

        PersistentSeedCaptureSampleRecord first = saveSample(strategy, 1, "SUCCEEDED", sameStart);
        first.updateProgress("CAPTURING", 2, 4, "[\"shop.orders\"]", 30, 4096, 1, sameStart);
        sampleRepository.saveAndFlush(first);
        saveSample(strategy, 2, "SUCCEEDED", sameStart);
        saveSample(strategy, 3, "FAILED", sameStart.plusSeconds(60));

        mockMvc.perform(get("/api/projects/1/seed/capture-strategies/1/samples")
                        .param("status", "SUCCEEDED")
                        .param("from", sameStart.toString())
                        .param("to", sameStart.toString())
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements", is(2)))
                .andExpect(jsonPath("$.content[0].sampleSeq", is(2)));

        mockMvc.perform(get("/api/projects/1/seed/capture-strategies/1/samples")
                        .param("status", "SUCCEEDED")
                        .param("from", sameStart.toString())
                        .param("to", sameStart.toString())
                        .param("page", "1")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sampleSeq", is(1)))
                .andExpect(jsonPath("$.content[0].capturedRows", is(30)))
                .andExpect(jsonPath("$.content[0].completedTables", is(2)));
    }

    @Test
    void returnsTableSummaryWithoutOpeningRowFiles() throws Exception {
        createProject();
        createDatasource();
        createStrategy();
        PersistentSeedCaptureStrategyRecord strategy = strategyRepository.findById(1L).orElseThrow();
        PersistentSeedCaptureSampleRecord sample = saveSample(
                strategy,
                1,
                "SUCCEEDED",
                Instant.parse("2026-07-15T06:00:00Z")
        );
        tableRepository.saveAndFlush(new PersistentSeedCaptureSampleTableRecord(
                sample.getId(),
                "shop.orders",
                SeedJson.write(new TableMetadata("shop.orders", List.of("id"), java.util.Set.of("id"), Map.of())),
                "schema-hash",
                2L,
                "content-hash",
                false,
                "SUCCEEDED"
        ));
        chunkRepository.saveAndFlush(new PersistentSeedCaptureChunkRecord(
                sample.getId(),
                "shop.orders",
                0,
                null,
                null,
                2L,
                "content-hash",
                "seed-captures/missing.jsonl.gz",
                "file-checksum",
                "READY",
                100L
        ));

        mockMvc.perform(get("/api/projects/1/seed/capture-samples/" + sample.getId() + "/tables"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tables", hasSize(1)))
                .andExpect(jsonPath("$.tables[0].tableName", is("shop.orders")))
                .andExpect(jsonPath("$.tables[0].schema.primaryKeyColumns[0]", is("id")))
                .andExpect(jsonPath("$.tables[0].chunks[0].status", is("READY")));
    }

    @Test
    void paginatesRowsAcrossChunksAndReturnsStableCursorDiagnostics() throws Exception {
        createProject();
        createDatasource();
        createStrategy();
        PersistentSeedCaptureStrategyRecord strategy = strategyRepository.findById(1L).orElseThrow();
        PersistentSeedCaptureSampleRecord sample = saveSample(
                strategy,
                1,
                "SUCCEEDED",
                Instant.parse("2026-07-15T06:00:00Z")
        );
        tableRepository.saveAndFlush(new PersistentSeedCaptureSampleTableRecord(
                sample.getId(),
                "shop.orders",
                SeedJson.write(new TableMetadata("shop.orders", List.of("id"), java.util.Set.of("id"), Map.of())),
                "schema-hash",
                5L,
                "table-hash",
                false,
                "SUCCEEDED"
        ));

        List<PersistentSeedCaptureChunkRecord> chunks = List.of(
                writeChunk(sample, strategy, 0, List.of(row(1), row(2))),
                writeChunk(sample, strategy, 1, List.of(row(3), row(4))),
                writeChunk(sample, strategy, 2, List.of(row(5)))
        );
        chunkRepository.saveAllAndFlush(chunks);

        String nextCursor = mockMvc.perform(get(
                        "/api/projects/1/seed/capture-samples/" + sample.getId() + "/tables/shop.orders/rows"
                ).param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows", hasSize(3)))
                .andExpect(jsonPath("$.rows[0].id", is(1)))
                .andExpect(jsonPath("$.rows[2].id", is(3)))
                .andExpect(jsonPath("$.nextCursor", org.hamcrest.Matchers.notNullValue()))
                .andExpect(jsonPath("$.schema.primaryKeyColumns[0]", is("id")))
                .andExpect(jsonPath("$.incomplete", is(false)))
                .andExpect(jsonPath("$.checksumValid", is(true)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String cursor = new com.fasterxml.jackson.databind.ObjectMapper().readTree(nextCursor).get("nextCursor").asText();

        mockMvc.perform(get(
                        "/api/projects/1/seed/capture-samples/" + sample.getId() + "/tables/shop.orders/rows"
                ).param("cursor", cursor).param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows", hasSize(2)))
                .andExpect(jsonPath("$.rows[0].id", is(4)))
                .andExpect(jsonPath("$.rows[1].id", is(5)))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void blocksLockedSampleDeletionAndDeletesFilesWithoutCascadingAnalysis() throws Exception {
        createProject();
        createDatasource();
        createStrategy();
        PersistentSeedCaptureStrategyRecord strategy = strategyRepository.findById(1L).orElseThrow();
        PersistentSeedCaptureSampleRecord sample = saveSample(
                strategy,
                1,
                "SUCCEEDED",
                Instant.parse("2026-07-15T06:00:00Z")
        );
        PersistentSeedCaptureAnalysisRecord analysis = analysisRepository.saveAndFlush(
                new PersistentSeedCaptureAnalysisRecord(1L, strategy.getId(), "[%d]".formatted(sample.getId()))
        );
        inputLockRepository.saveAndFlush(
                new PersistentSeedCaptureAnalysisInputLockRecord(analysis.getId(), sample.getId())
        );

        mockMvc.perform(delete("/api/projects/1/seed/capture-samples/" + sample.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString(
                        "active analysis"
                )));

        inputLockRepository.deleteAll();
        tableRepository.saveAndFlush(new PersistentSeedCaptureSampleTableRecord(
                sample.getId(),
                "shop.orders",
                "{}",
                "schema-hash",
                1L,
                "table-hash",
                false,
                "SUCCEEDED"
        ));
        PersistentSeedCaptureChunkRecord chunk = writeChunk(sample, strategy, 0, List.of(row(1)));
        chunkRepository.saveAndFlush(chunk);

        mockMvc.perform(delete("/api/projects/1/seed/capture-samples/" + sample.getId()))
                .andExpect(status().isNoContent());

        org.assertj.core.api.Assertions.assertThat(sampleRepository.findById(sample.getId())).isEmpty();
        org.assertj.core.api.Assertions.assertThat(tableRepository.findBySampleIdOrderByTableNameAsc(sample.getId()))
                .isEmpty();
        org.assertj.core.api.Assertions.assertThat(chunkRepository
                        .findBySampleIdAndTableNameOrderByChunkSeqAsc(sample.getId(), "shop.orders"))
                .isEmpty();
        org.assertj.core.api.Assertions.assertThat(analysisRepository.findById(analysis.getId())).isPresent();
        org.assertj.core.api.Assertions.assertThat(
                java.nio.file.Files.exists(chunkStore.resolveRelativePath(chunk.getRelativePath()))
        ).isFalse();
    }

    private PersistentSeedCaptureSampleRecord saveSample(
            PersistentSeedCaptureStrategyRecord strategy,
            int sampleSeq,
            String status,
            Instant startedAt
    ) {
        return sampleRepository.saveAndFlush(new PersistentSeedCaptureSampleRecord(
                1L,
                strategy.getId(),
                strategy.getDatasourceId(),
                sampleSeq,
                status,
                startedAt,
                "SUCCEEDED".equals(status) || "FAILED".equals(status) ? startedAt.plusSeconds(10) : null,
                "{}",
                strategy.getConfigVersion()
        ));
    }

    private PersistentSeedCaptureChunkRecord writeChunk(
            PersistentSeedCaptureSampleRecord sample,
            PersistentSeedCaptureStrategyRecord strategy,
            int chunkSeq,
            List<Map<String, Object>> rows
    ) {
        CaptureChunkStore.ChunkManifest manifest;
        try (CaptureChunkStore.ChunkWriter writer = chunkStore.openChunk(
                sample.getProjectId(),
                strategy.getId(),
                sample.getId(),
                "shop.orders",
                chunkSeq
        )) {
            rows.forEach(writer::writeRow);
            manifest = writer.commit();
        }
        return new PersistentSeedCaptureChunkRecord(
                sample.getId(),
                "shop.orders",
                chunkSeq,
                null,
                null,
                manifest.rowCount(),
                manifest.contentHash(),
                manifest.relativePath(),
                manifest.fileChecksum(),
                "READY",
                manifest.byteSize()
        );
    }

    private static Map<String, Object> row(int id) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("name", "order-" + id);
        return row;
    }

    private void createProject() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User", "admin")
                        .content("{\"code\":\"sample-project\",\"name\":\"Sample Project\",\"description\":\"Seed\"}"))
                .andExpect(status().isCreated());
    }

    private void createDatasource() throws Exception {
        mockMvc.perform(post("/api/projects/1/seed/datasources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"unused",
                                  "host":"unused.invalid",
                                  "port":3306,
                                  "databaseName":"shop",
                                  "username":"seed",
                                  "password":"secret"
                                }
                                """))
                .andExpect(status().isCreated());
    }

    private void createStrategy() throws Exception {
        mockMvc.perform(post("/api/projects/1/seed/capture-strategies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"capture",
                                  "datasourceId":1,
                                  "includes":["shop.orders"],
                                  "excludes":[],
                                  "threadCount":1,
                                  "batchRows":100
                                }
                                """))
                .andExpect(status().isCreated());
    }
}
