package com.yr.perftest.platform.seed;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:capture-analysis-execution-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false",
        "platform.storage.root=./build/test-storage/capture-analysis-execution",
        "platform.seed.disk-low-water-bytes=0"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class CaptureAnalysisExecutionTest {
    private static final Path STORAGE = Path.of("build/test-storage/capture-analysis-execution");

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
    private PersistentSeedCaptureAnalysisResultRepository resultRepository;

    @Autowired
    private PersistentSeedTemplateRepository templateRepository;

    @Autowired
    private CaptureChunkStore chunkStore;

    @Autowired
    private DiskLowWaterGuard diskGuard;

    @Autowired
    private DatasourceCaptureLeaseService leaseService;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void cleanStorage() throws IOException {
        if (!Files.exists(STORAGE)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(STORAGE)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    throw new IllegalStateException("failed to clean analysis storage", ex);
                }
            });
        }
    }

    @Test
    void runsAdjacentAnalysisPersistsProgressDiffChunksAndIndependentTemplate() throws Exception {
        Fixture fixture = fixture();
        PersistentSeedCaptureSampleRecord first = sample(fixture.strategy(), 1, "old");
        PersistentSeedCaptureSampleRecord second = sample(fixture.strategy(), 2, "new");
        PersistentSeedCaptureSampleRecord third = sample(fixture.strategy(), 3, "new");
        addSnapshot(first, fixture.strategy(), List.of(row(1, "old")));
        addSnapshot(second, fixture.strategy(), List.of(row(1, "new"), row(2, "inserted")));
        addSnapshot(third, fixture.strategy(), List.of(row(1, "new"), row(2, "inserted")));

        SeedCaptureAnalysisService service = serviceWithoutAutoSubmit();
        Map<String, Object> created = service.create(
                1L,
                new CreateSeedCaptureAnalysisRequest(
                        fixture.strategy().getId(),
                        List.of(first.getId(), second.getId(), third.getId()),
                        false
                )
        );
        long analysisId = ((Number) created.get("id")).longValue();
        assertThat(inputLockRepository.findByAnalysisId(analysisId)).hasSize(3);

        executor(diskGuard).run(analysisId);

        PersistentSeedCaptureAnalysisRecord analysis =
                analysisRepository.findById(analysisId).orElseThrow();
        assertThat(analysis.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(analysis.getPhase()).isEqualTo("SUCCEEDED");
        assertThat(analysis.getCompletedTables()).isEqualTo(1);
        assertThat(analysis.getTotalTables()).isEqualTo(1);
        assertThat(analysis.getComparedRows()).isGreaterThan(0);
        assertThat(analysis.getCandidateOperationCount()).isGreaterThan(0);
        assertThat(inputLockRepository.findByAnalysisId(analysisId)).isEmpty();
        assertThat(templateRepository.findById(analysis.getTemplateId())).isPresent();
        assertThat(templateRepository.findById(analysis.getTemplateId()).orElseThrow()
                        .getAnalysisId())
                .isEqualTo(analysisId);
        assertThat(resultRepository.findByAnalysisIdOrderByIdAsc(analysisId)).isNotEmpty();

        mockMvc.perform(get(
                        "/api/projects/1/seed/capture-analyses/" + analysisId
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.inputManifest.samples", org.hamcrest.Matchers.hasSize(3)));
        mockMvc.perform(get(
                        "/api/projects/1/seed/capture-analyses/"
                                + analysisId + "/tables/shop.orders/diffs"
                ).param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.rows[0].kind").value("UPDATE"))
                .andExpect(jsonPath("$.rows[1].kind").value("INSERT"));
    }

    @Test
    void cancellationIsCooperativeAndReleasesAllInputLocks() {
        Fixture fixture = fixture();
        List<PersistentSeedCaptureSampleRecord> samples = samples(fixture.strategy());
        SeedCaptureAnalysisService service = serviceWithoutAutoSubmit();
        long analysisId = id(service.create(
                1L,
                new CreateSeedCaptureAnalysisRequest(
                        fixture.strategy().getId(),
                        samples.stream().map(PersistentSeedCaptureSampleRecord::getId).toList(),
                        false
                )
        ));

        assertThat(service.cancel(1L, analysisId).get("status"))
                .isEqualTo("CANCEL_REQUESTED");
        executor(diskGuard).run(analysisId);

        assertThat(analysisRepository.findById(analysisId).orElseThrow().getStatus())
                .isEqualTo("CANCELED");
        assertThat(inputLockRepository.findByAnalysisId(analysisId)).isEmpty();
        assertThat(sampleRepository.findById(samples.get(0).getId())).isPresent();
    }

    @Test
    void failureReleasesLocksAndDoesNotCreateTemplate() {
        Fixture fixture = fixture();
        List<PersistentSeedCaptureSampleRecord> samples = samples(fixture.strategy());
        SeedCaptureAnalysisService service = serviceWithoutAutoSubmit();
        long analysisId = id(service.create(
                1L,
                new CreateSeedCaptureAnalysisRequest(
                        fixture.strategy().getId(),
                        samples.stream().map(PersistentSeedCaptureSampleRecord::getId).toList(),
                        false
                )
        ));

        executor(new DiskLowWaterGuard(STORAGE, Long.MAX_VALUE)).run(analysisId);

        PersistentSeedCaptureAnalysisRecord analysis =
                analysisRepository.findById(analysisId).orElseThrow();
        assertThat(analysis.getStatus()).isEqualTo("FAILED");
        assertThat(analysis.getErrorMessage()).contains("low-water");
        assertThat(inputLockRepository.findByAnalysisId(analysisId)).isEmpty();
        assertThat(templateRepository.findByProjectIdOrderByIdDesc(1L)).isEmpty();
    }

    @Test
    void startupInterruptionReleasesAnalysisLocksWithoutDeletingSamples() {
        Fixture fixture = fixture();
        List<PersistentSeedCaptureSampleRecord> samples = samples(fixture.strategy());
        SeedCaptureAnalysisService service = serviceWithoutAutoSubmit();
        long analysisId = id(service.create(
                1L,
                new CreateSeedCaptureAnalysisRequest(
                        fixture.strategy().getId(),
                        samples.stream().map(PersistentSeedCaptureSampleRecord::getId).toList(),
                        false
                )
        ));
        PersistentSeedCaptureAnalysisRecord analysis = analysisRepository.findById(analysisId).orElseThrow();
        analysis.updateHeartbeat(Instant.parse("2026-07-15T07:00:00Z"));
        analysisRepository.saveAndFlush(analysis);

        new CaptureStartupReconciliation(
                sampleRepository,
                leaseService,
                chunkStore,
                analysisRepository,
                inputLockRepository,
                Duration.ofMinutes(5)
        ).reconcile(Instant.parse("2026-07-15T08:00:00Z"));

        assertThat(analysisRepository.findById(analysisId).orElseThrow().getStatus())
                .isEqualTo("INTERRUPTED");
        assertThat(inputLockRepository.findByAnalysisId(analysisId)).isEmpty();
        assertThat(sampleRepository.findAll()).hasSize(3);
    }

    @Test
    void deletingAnalysisDoesNotCascadeSamplesOrTemplate() {
        Fixture fixture = fixture();
        List<PersistentSeedCaptureSampleRecord> samples = samples(fixture.strategy());
        SeedCaptureAnalysisService service = serviceWithoutAutoSubmit();
        long analysisId = id(service.create(
                1L,
                new CreateSeedCaptureAnalysisRequest(
                        fixture.strategy().getId(),
                        samples.stream().map(PersistentSeedCaptureSampleRecord::getId).toList(),
                        false
                )
        ));
        PersistentSeedCaptureAnalysisRecord analysis = analysisRepository.findById(analysisId).orElseThrow();
        analysis.markStatus("CANCEL_REQUESTED");
        analysis.cancel();
        analysisRepository.saveAndFlush(analysis);
        PersistentSeedTemplateRecord template = templateRepository.saveAndFlush(
                PersistentSeedTemplateRecord.forAnalysis(1L, analysisId, "{\"operations\":[]}", "{}")
        );

        assertThat(service.delete(1L, analysisId).get("status")).isEqualTo("DELETED");
        assertThat(analysisRepository.findById(analysisId)).isEmpty();
        assertThat(sampleRepository.findAll()).hasSize(3);
        assertThat(templateRepository.findById(template.getId())).isPresent();
    }

    @Test
    void pagesDetailedDiffRowsAcrossAnalysisChunksWithStableCursor() {
        Fixture fixture = fixture();
        PersistentSeedCaptureAnalysisRecord analysis = analysisRepository.saveAndFlush(
                new PersistentSeedCaptureAnalysisRecord(1L, fixture.strategy().getId(), "[]")
        );
        saveAnalysisChunk(analysis, fixture.strategy(), 0, List.of(row(1, "one"), row(2, "two")));
        saveAnalysisChunk(analysis, fixture.strategy(), 1, List.of(row(3, "three"), row(4, "four")));
        SeedCaptureAnalysisService service = serviceWithoutAutoSubmit();

        Map<String, Object> first = service.readDiffs(
                1L,
                analysis.getId(),
                "shop.orders",
                null,
                3
        );
        assertThat((List<?>) first.get("rows")).hasSize(3);
        assertThat(first.get("nextCursor")).isNotNull();

        Map<String, Object> second = service.readDiffs(
                1L,
                analysis.getId(),
                "shop.orders",
                (String) first.get("nextCursor"),
                3
        );
        assertThat((List<?>) second.get("rows")).hasSize(1);
        assertThat(second.get("nextCursor")).isNull();
    }

    private SeedCaptureAnalysisService serviceWithoutAutoSubmit() {
        return new SeedCaptureAnalysisService(
                strategyRepository,
                sampleRepository,
                tableRepository,
                chunkRepository,
                analysisRepository,
                inputLockRepository,
                resultRepository,
                templateRepository,
                chunkStore
        );
    }

    private SeedCaptureAnalysisExecutor executor(DiskLowWaterGuard guard) {
        return new SeedCaptureAnalysisExecutor(
                sampleRepository,
                tableRepository,
                chunkRepository,
                analysisRepository,
                inputLockRepository,
                resultRepository,
                templateRepository,
                chunkStore,
                guard
        );
    }

    private Fixture fixture() {
        PersistentSeedDatasourceRecord datasource = datasourceRepository.saveAndFlush(
                new PersistentSeedDatasourceRecord(1L, "shop", "unused", 3306, "shop", "seed", "secret")
        );
        PersistentSeedCaptureStrategyRecord strategy = strategyRepository.saveAndFlush(
                new PersistentSeedCaptureStrategyRecord(
                        1L,
                        "analysis",
                        datasource.getId(),
                        "[\"shop.orders\"]",
                        "[]",
                        1,
                        100
                )
        );
        return new Fixture(strategy);
    }

    private List<PersistentSeedCaptureSampleRecord> samples(
            PersistentSeedCaptureStrategyRecord strategy
    ) {
        return List.of(
                sample(strategy, 1, "one"),
                sample(strategy, 2, "two"),
                sample(strategy, 3, "three")
        );
    }

    private PersistentSeedCaptureSampleRecord sample(
            PersistentSeedCaptureStrategyRecord strategy,
            int sequence,
            String ignored
    ) {
        Instant startedAt = Instant.parse("2026-07-15T06:0%d:00Z".formatted(sequence));
        return sampleRepository.saveAndFlush(new PersistentSeedCaptureSampleRecord(
                1L,
                strategy.getId(),
                strategy.getDatasourceId(),
                sequence,
                "SUCCEEDED",
                startedAt,
                startedAt.plusSeconds(10),
                "{\"includes\":[\"shop.orders\"],\"excludes\":[],\"threadCount\":1,\"batchRows\":100}",
                strategy.getConfigVersion()
        ));
    }

    private void addSnapshot(
            PersistentSeedCaptureSampleRecord sample,
            PersistentSeedCaptureStrategyRecord strategy,
            List<Map<String, Object>> rows
    ) {
        TableMetadata metadata = new TableMetadata(
                "shop.orders",
                List.of("id"),
                Set.of("id"),
                Map.of()
        );
        CaptureChunkStore.ChunkManifest manifest;
        try (CaptureChunkStore.ChunkWriter writer = chunkStore.openChunk(
                sample.getProjectId(),
                strategy.getId(),
                sample.getId(),
                "shop.orders",
                0
        )) {
            rows.forEach(writer::writeRow);
            manifest = writer.commit();
        }
        String schemaHash = LogicalFingerprint.schemaFingerprint(
                List.of(metadata.table(), metadata.primaryKeyColumns(), metadata.uniqueColumns())
        );
        String tableHash = LogicalFingerprint.tableFingerprint(
                schemaHash,
                rows.size(),
                manifest.contentHash()
        );
        tableRepository.saveAndFlush(new PersistentSeedCaptureSampleTableRecord(
                sample.getId(),
                "shop.orders",
                SeedJson.write(metadata),
                schemaHash,
                rows.size(),
                tableHash,
                false,
                "SUCCEEDED"
        ));
        chunkRepository.saveAndFlush(new PersistentSeedCaptureChunkRecord(
                sample.getId(),
                "shop.orders",
                0,
                null,
                null,
                manifest.rowCount(),
                manifest.contentHash(),
                manifest.relativePath(),
                manifest.fileChecksum(),
                "READY",
                manifest.byteSize()
        ));
    }

    private void saveAnalysisChunk(
            PersistentSeedCaptureAnalysisRecord analysis,
            PersistentSeedCaptureStrategyRecord strategy,
            int chunkSeq,
            List<Map<String, Object>> rows
    ) {
        CaptureChunkStore.ChunkManifest manifest;
        try (CaptureChunkStore.ChunkWriter writer = chunkStore.openAnalysisChunk(
                analysis.getProjectId(),
                strategy.getId(),
                analysis.getId(),
                "shop.orders",
                chunkSeq
        )) {
            rows.forEach(writer::writeRow);
            manifest = writer.commit();
        }
        resultRepository.saveAndFlush(new PersistentSeedCaptureAnalysisResultRecord(
                analysis.getId(),
                "shop.orders",
                chunkSeq,
                "TABLE_DIFF",
                "{\"tableName\":\"shop.orders\"}",
                manifest.relativePath(),
                manifest.fileChecksum(),
                manifest.rowCount()
        ));
    }

    private static long id(Map<String, Object> view) {
        return ((Number) view.get("id")).longValue();
    }

    private static Map<String, Object> row(int id, String name) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("name", name);
        return row;
    }

    private record Fixture(PersistentSeedCaptureStrategyRecord strategy) {
    }
}
