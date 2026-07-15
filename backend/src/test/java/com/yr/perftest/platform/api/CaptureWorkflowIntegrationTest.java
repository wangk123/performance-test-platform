package com.yr.perftest.platform.api;

import com.yr.perftest.platform.seed.CaptureBatchConsumer;
import com.yr.perftest.platform.seed.CaptureChunkStore;
import com.yr.perftest.platform.seed.CapturePartitionPlanner;
import com.yr.perftest.platform.seed.CaptureRowSource;
import com.yr.perftest.platform.seed.CreateSeedCaptureAnalysisRequest;
import com.yr.perftest.platform.seed.CreateSeedCaptureStrategyRequest;
import com.yr.perftest.platform.seed.DatasourceCaptureLeaseService;
import com.yr.perftest.platform.seed.DiskLowWaterGuard;
import com.yr.perftest.platform.seed.PersistentSeedCaptureAnalysisResultRepository;
import com.yr.perftest.platform.seed.PersistentSeedCaptureAnalysisInputLockRepository;
import com.yr.perftest.platform.seed.PersistentSeedCaptureAnalysisRepository;
import com.yr.perftest.platform.seed.PersistentSeedCaptureAnalysisRecord;
import com.yr.perftest.platform.seed.PersistentSeedCaptureChunkRepository;
import com.yr.perftest.platform.seed.PersistentSeedCaptureSampleRecord;
import com.yr.perftest.platform.seed.PersistentSeedCaptureSampleRepository;
import com.yr.perftest.platform.seed.PersistentSeedCaptureSampleTableRepository;
import com.yr.perftest.platform.seed.PersistentSeedCaptureSessionRepository;
import com.yr.perftest.platform.seed.PersistentSeedCaptureStrategyRecord;
import com.yr.perftest.platform.seed.PersistentSeedCaptureStrategyRepository;
import com.yr.perftest.platform.seed.PersistentSeedDatasourceRecord;
import com.yr.perftest.platform.seed.PersistentSeedDatasourceRepository;
import com.yr.perftest.platform.seed.PersistentSeedTemplateRepository;
import com.yr.perftest.platform.seed.SampleCaptureExecutor;
import com.yr.perftest.platform.seed.SeedCaptureAnalysisExecutor;
import com.yr.perftest.platform.seed.SeedCaptureAnalysisService;
import com.yr.perftest.platform.seed.SeedCaptureStrategyService;
import com.yr.perftest.platform.seed.TableMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:capture-workflow-integration-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false",
        "platform.storage.root=./build/test-storage/capture-workflow-integration",
        "platform.seed.disk-low-water-bytes=0"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class CaptureWorkflowIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PersistentSeedDatasourceRepository datasourceRepository;

    @Autowired
    private PersistentSeedCaptureStrategyRepository strategyRepository;

    @Autowired
    private PersistentSeedCaptureSessionRepository legacySessionRepository;

    @Autowired
    private PersistentSeedCaptureSampleRepository sampleRepository;

    @Autowired
    private PersistentSeedCaptureSampleTableRepository tableRepository;

    @Autowired
    private PersistentSeedCaptureAnalysisRepository analysisRepository;

    @Autowired
    private PersistentSeedCaptureAnalysisInputLockRepository inputLockRepository;

    @Autowired
    private PersistentSeedCaptureAnalysisResultRepository analysisResultRepository;

    @Autowired
    private PersistentSeedCaptureChunkRepository chunkRepository;

    @Autowired
    private PersistentSeedTemplateRepository templateRepository;

    @Autowired
    private DatasourceCaptureLeaseService leaseService;

    @Autowired
    private CaptureChunkStore chunkStore;

    @Autowired
    private DiskLowWaterGuard diskGuard;

    @Autowired
    private CapturePartitionPlanner partitionPlanner;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanStorage() throws IOException {
        Path root = Path.of("build/test-storage/capture-workflow-integration");
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    throw new IllegalStateException("failed to clean workflow storage", ex);
                }
            });
        }
        Files.createDirectories(root);
    }

    @Test
    void executesThreeSamplesThenCreatesTemplateAfterConfirmingIncompleteSamples() {
        PersistentSeedDatasourceRecord datasource = datasourceRepository.saveAndFlush(
                new PersistentSeedDatasourceRecord(1L, "fake", "fake", 3306, "shop", "u", "p")
        );
        SeedCaptureStrategyService strategyService = new SeedCaptureStrategyService(
                datasourceRepository,
                strategyRepository,
                sampleRepository,
                leaseService,
                null
        );
        long strategyId = new TransactionTemplate(transactionManager).execute(status ->
                strategyService.create(
                        1L,
                        new CreateSeedCaptureStrategyRequest(
                                "workflow",
                                datasource.getId(),
                                List.of("shop.orders"),
                                List.of(),
                                1,
                                100
                        )
                ).id()
        );

        long succeededSample = execute(strategyService, strategyId);
        captureExecutor(new FakeSource(List.of(row(1, "old"), row(2, "same")), false, () -> {
                }))
                .capture(succeededSample);

        long failedSample = execute(strategyService, strategyId);
        captureExecutor(new FakeSource(
                List.of(row(1, "new"), row(2, "same")),
                true,
                () -> {
                }
        ))
                .capture(failedSample);

        long canceledSample = execute(strategyService, strategyId);
        captureExecutor(new FakeSource(
                List.of(row(1, "new"), row(2, "latest")),
                false,
                () -> {
                    PersistentSeedCaptureSampleRecord sample =
                            sampleRepository.findById(canceledSample).orElseThrow();
                    sample.requestCancel();
                    sampleRepository.saveAndFlush(sample);
                }
        ))
                .capture(canceledSample);

        List<PersistentSeedCaptureSampleRecord> samples =
                sampleRepository.findByStrategyIdOrderBySampleSeqAsc(strategyId);
        assertThat(samples)
                .withFailMessage("sample errors: %s", samples.stream()
                        .map(PersistentSeedCaptureSampleRecord::getErrorMessage)
                        .toList())
                .extracting(PersistentSeedCaptureSampleRecord::getStatus)
                .containsExactly("SUCCEEDED", "FAILED", "CANCELED");
        assertThat(leaseService.findActiveSampleId(datasource.getId())).isEmpty();

        SeedCaptureAnalysisService analysisService = new SeedCaptureAnalysisService(
                strategyRepository,
                sampleRepository,
                tableRepository,
                chunkRepository,
                analysisRepository,
                inputLockRepository,
                analysisResultRepository,
                templateRepository,
                chunkStore
        );
        List<Long> sampleIds = samples
                .stream()
                .map(PersistentSeedCaptureSampleRecord::getId)
                .toList();
        CreateSeedCaptureAnalysisRequest request =
                new CreateSeedCaptureAnalysisRequest(strategyId, sampleIds, false);
        assertThatThrownBy(() -> analysisService.create(1L, request))
                .hasMessageContaining("confirmIncomplete");
        long analysisId = ((Number) analysisService.create(
                1L,
                new CreateSeedCaptureAnalysisRequest(strategyId, sampleIds, true)
        ).get("id")).longValue();

        new SeedCaptureAnalysisExecutor(
                sampleRepository,
                tableRepository,
                chunkRepository,
                analysisRepository,
                inputLockRepository,
                analysisResultRepository,
                templateRepository,
                chunkStore,
                diskGuard
        ).run(analysisId);

        assertThat(analysisRepository.findById(analysisId).orElseThrow().getStatus())
                .isEqualTo("SUCCEEDED");
        assertThat(inputLockRepository.findByAnalysisId(analysisId)).isEmpty();
        PersistentSeedCaptureAnalysisRecord analysis =
                analysisRepository.findById(analysisId).orElseThrow();
        assertThat(templateRepository.findById(
                analysis.getTemplateId()
        )).isPresent();
        assertThat(templateRepository.findById(analysis.getTemplateId()).orElseThrow().getAnalysisId())
                .isEqualTo(analysisId);
    }

    @Test
    void retiredSessionEndpointsDoNotCreateLegacySessions() throws Exception {
        mockMvc.perform(post("/api/projects/1/seed/captures")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "datasourceId": 1,
                                  "provider": "SNAPSHOT",
                                  "includes": ["shop.orders"],
                                  "excludes": []
                                }
                                """))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/projects/1/seed/captures/1/samples"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/projects/1/seed/captures/1/finish"))
                .andExpect(status().isNotFound());
        assertThat(legacySessionRepository.count()).isZero();
    }

    private long execute(SeedCaptureStrategyService service, long strategyId) {
        return new TransactionTemplate(transactionManager).execute(status ->
                ((Number) service.execute(1L, strategyId).get("id")).longValue()
        );
    }

    private SampleCaptureExecutor captureExecutor(FakeSource source) {
        return new SampleCaptureExecutor(
                sampleRepository,
                datasourceRepository,
                tableRepository,
                chunkRepository,
                leaseService,
                chunkStore,
                diskGuard,
                ignored -> source,
                partitionPlanner
        );
    }

    private static Map<String, Object> row(int id, String name) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("name", name);
        return row;
    }

    private static final class FakeSource implements CaptureRowSource {
        private final List<Map<String, Object>> rows;
        private final boolean failAfterFirstBatch;
        private final Runnable afterFirstBatch;

        private FakeSource(
                List<Map<String, Object>> rows,
                boolean failAfterFirstBatch,
                Runnable afterFirstBatch
        ) {
            this.rows = rows;
            this.failAfterFirstBatch = failAfterFirstBatch;
            this.afterFirstBatch = afterFirstBatch;
        }

        @Override
        public List<String> listTables(String database) {
            return List.of("shop.orders");
        }

        @Override
        public TableMetadata readMetadata(String qualifiedTable) {
            return new TableMetadata("shop.orders", List.of("id"), Set.of("id"), Map.of());
        }

        @Override
        public void readBatches(
                CapturePartitionPlanner.Partition partition,
                int batchRows,
                CaptureBatchConsumer consumer
        ) throws Exception {
            for (int start = 0; start < rows.size(); start += batchRows) {
                List<Map<String, Object>> batch = rows.subList(
                        start,
                        Math.min(start + batchRows, rows.size())
                );
                if (!consumer.accept(batch)) {
                    return;
                }
                if (start == 0) {
                    if (failAfterFirstBatch) {
                        throw new IOException("fake source failed after first batch");
                    }
                    afterFirstBatch.run();
                }
            }
        }
    }
}
