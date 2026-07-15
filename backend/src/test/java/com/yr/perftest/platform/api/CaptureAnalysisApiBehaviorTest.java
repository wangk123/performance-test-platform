package com.yr.perftest.platform.api;

import com.yr.perftest.platform.seed.PersistentSeedCaptureStrategyRecord;
import com.yr.perftest.platform.seed.PersistentSeedCaptureStrategyRepository;
import com.yr.perftest.platform.seed.PersistentSeedCaptureSampleRecord;
import com.yr.perftest.platform.seed.PersistentSeedCaptureSampleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:capture-analysis-api-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false",
        "platform.storage.root=./build/test-storage/capture-analysis-api",
        "platform.seed.disk-low-water-bytes=0"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class CaptureAnalysisApiBehaviorTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PersistentSeedCaptureStrategyRepository strategyRepository;

    @Autowired
    private PersistentSeedCaptureSampleRepository sampleRepository;

    @Test
    void rejectsAnalysisWithFewerThanThreeSamples() throws Exception {
        createProject();
        createDatasource();
        createStrategy();

        mockMvc.perform(post("/api/projects/1/seed/capture-analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "strategyId":1,
                                  "sampleIds":[1,2]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("three")));
    }

    @Test
    void requiresExplicitConfirmationForIncompleteSamples() throws Exception {
        createProject();
        createDatasource();
        createStrategy();
        PersistentSeedCaptureStrategyRecord strategy = strategyRepository.findById(1L).orElseThrow();
        saveSample(strategy, 1, "SUCCEEDED");
        saveSample(strategy, 2, "FAILED");
        saveSample(strategy, 3, "SUCCEEDED");

        mockMvc.perform(post("/api/projects/1/seed/capture-analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "strategyId":1,
                                  "sampleIds":[1,2,3]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("confirmIncomplete")));
    }

    @Test
    void createsQueuedAnalysisAndDoesNotRunSynchronously() throws Exception {
        createProject();
        createDatasource();
        createStrategy();
        PersistentSeedCaptureStrategyRecord strategy = strategyRepository.findById(1L).orElseThrow();
        saveSample(strategy, 1, "SUCCEEDED");
        saveSample(strategy, 2, "SUCCEEDED");
        saveSample(strategy, 3, "SUCCEEDED");

        mockMvc.perform(post("/api/projects/1/seed/capture-analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "strategyId":1,
                                  "sampleIds":[1,2,3]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("QUEUED")))
                .andExpect(jsonPath("$.inputSampleIds", org.hamcrest.Matchers.hasSize(3)));
    }

    private void createProject() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User", "admin")
                        .content("{\"code\":\"analysis-project\",\"name\":\"Analysis Project\",\"description\":\"Seed\"}"))
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
                                  "databaseName":"seed",
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
                                  "name":"analysis",
                                  "datasourceId":1,
                                  "includes":["seed.orders"],
                                  "excludes":[],
                                  "threadCount":1,
                                  "batchRows":100
                                }
                                """))
                .andExpect(status().isCreated());
    }

    private void saveSample(
            PersistentSeedCaptureStrategyRecord strategy,
            int sequence,
            String status
    ) {
        Instant startedAt = Instant.parse("2026-07-15T06:0%d:00Z".formatted(sequence));
        sampleRepository.saveAndFlush(new PersistentSeedCaptureSampleRecord(
                1L,
                strategy.getId(),
                strategy.getDatasourceId(),
                sequence,
                status,
                startedAt,
                startedAt.plusSeconds(10),
                "{}",
                strategy.getConfigVersion()
        ));
    }
}
