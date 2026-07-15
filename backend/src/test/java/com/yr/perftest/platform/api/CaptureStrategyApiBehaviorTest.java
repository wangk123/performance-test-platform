package com.yr.perftest.platform.api;

import com.yr.perftest.platform.seed.PersistentSeedCaptureSampleRecord;
import com.yr.perftest.platform.seed.PersistentSeedCaptureSampleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:capture-strategy-api-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false",
        "platform.storage.root=./build/test-storage/capture-strategy-api"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class CaptureStrategyApiBehaviorTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PersistentSeedCaptureSampleRepository sampleRepository;

    @Test
    void createsListsUpdatesDetailsAndDeletesStrategy() throws Exception {
        createProject();
        createDatasource();

        mockMvc.perform(post("/api/projects/1/seed/capture-strategies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(strategyJson("baseline", 1, List.of("db.*"), List.of(), 4, 5000)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("baseline")))
                .andExpect(jsonPath("$.includes[0]", is("db.*")))
                .andExpect(jsonPath("$.configVersion", is(1)));

        mockMvc.perform(get("/api/projects/1/seed/capture-strategies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("baseline")));

        mockMvc.perform(get("/api/projects/1/seed/capture-strategies/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchRows", is(5000)));

        mockMvc.perform(put("/api/projects/1/seed/capture-strategies/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(strategyJson("baseline-v2", 1, List.of("db.orders"), List.of("db.audit"), 8, 10000)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("baseline-v2")))
                .andExpect(jsonPath("$.configVersion", is(2)))
                .andExpect(jsonPath("$.excludes[0]", is("db.audit")));

        mockMvc.perform(delete("/api/projects/1/seed/capture-strategies/1"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/projects/1/seed/capture-strategies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void rejectsInvalidStrategyConfiguration() throws Exception {
        createProject();
        createDatasource();

        mockMvc.perform(post("/api/projects/1/seed/capture-strategies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(strategyJson("empty-include", 1, List.of(), List.of(), 4, 5000)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("include filter is required")));

        mockMvc.perform(post("/api/projects/1/seed/capture-strategies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(strategyJson("bad-thread", 1, List.of("db.*"), List.of(), 0, 5000)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("threadCount must be between 1 and 32")));

        mockMvc.perform(post("/api/projects/1/seed/capture-strategies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(strategyJson("bad-batch", 1, List.of("db.*"), List.of(), 4, 99)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("batchRows must be between 100 and 100000")));
    }

    @Test
    void rejectsDatasourceOutsideProjectAndMissingStrategy() throws Exception {
        createProject();
        createDatasource();
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User", "admin")
                        .content("{\"code\":\"other\",\"name\":\"Other\",\"description\":\"Other\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/projects/2/seed/capture-strategies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(strategyJson("wrong-owner", 1, List.of("db.*"), List.of(), 4, 5000)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("datasource not found: 1")));

        mockMvc.perform(get("/api/projects/1/seed/capture-strategies/999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("capture strategy not found: 999")));

        mockMvc.perform(post("/api/projects/1/seed/capture-strategies/999/execute"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("capture strategy not found: 999")));
    }

    @Test
    void executesWithoutSynchronousScanAndRejectsConcurrentCapture() throws Exception {
        createProject();
        createDatasource();
        mockMvc.perform(post("/api/projects/1/seed/capture-strategies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(strategyJson("baseline", 1, List.of("db.*"), List.of(), 4, 5000)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/projects/1/seed/capture-strategies/1/execute"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("QUEUED")))
                .andExpect(jsonPath("$.sampleSeq", is(1)))
                .andExpect(jsonPath("$.configVersion", is(1)))
                .andExpect(jsonPath("$.captureStartedAt", matchesPattern(".+Z")));

        mockMvc.perform(post("/api/projects/1/seed/capture-strategies/1/execute"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", matchesPattern(".*active capture.*")));

        List<PersistentSeedCaptureSampleRecord> samples =
                sampleRepository.findByStrategyIdOrderBySampleSeqAsc(1L);
        assertThat(samples).hasSize(1);
        assertThat(samples).extracting(PersistentSeedCaptureSampleRecord::getSampleSeq)
                .containsExactly(1);
        assertThat(samples.get(0).getConfigSnapshotJson())
                .contains("\"name\":\"baseline\"")
                .contains("\"includes\":[\"db.*\"]")
                .contains("\"threadCount\":4")
                .contains("\"batchRows\":5000");
        assertThat(samples.get(0).getCaptureStartedAt()).isNotNull();
    }

    private void createProject() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User", "admin")
                        .content("{\"code\":\"seed-project\",\"name\":\"Seed Project\",\"description\":\"Seed\"}"))
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

    private static String strategyJson(
            String name,
            long datasourceId,
            List<String> includes,
            List<String> excludes,
            int threadCount,
            int batchRows
    ) {
        return """
                {
                  "name":"%s",
                  "datasourceId":%d,
                  "includes":%s,
                  "excludes":%s,
                  "threadCount":%d,
                  "batchRows":%d
                }
                """.formatted(
                name,
                datasourceId,
                jsonArray(includes),
                jsonArray(excludes),
                threadCount,
                batchRows
        );
    }

    private static String jsonArray(List<String> values) {
        return values.stream()
                .map(value -> "\"" + value + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }
}
