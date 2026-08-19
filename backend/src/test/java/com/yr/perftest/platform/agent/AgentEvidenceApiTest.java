package com.yr.perftest.platform.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.execution.aggregate.PersistentExecutionMetricSeriesRecord;
import com.yr.perftest.platform.execution.aggregate.PersistentExecutionMetricSeriesRepository;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRecord;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRepository;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.PersistentTaskScenarioRecord;
import com.yr.perftest.platform.task.PersistentTaskScenarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agent-evidence-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AgentEvidenceApiTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PersistentTaskPlanRepository planRepository;

    @Autowired
    private PersistentTaskScenarioRepository scenarioRepository;

    @Autowired
    private PersistentScenarioExecutionRepository executionRepository;

    @Autowired
    private PersistentExecutionMetricSeriesRepository metricSeriesRepository;

    private String apiKey;
    private long executionId;

    @BeforeEach
    void setUp() throws Exception {
        String adminToken = loginToken();
        MvcResult issued = mockMvc.perform(post("/api/agent-api-keys")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":\"ops\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        apiKey = objectMapper.readTree(issued.getResponse().getContentAsString()).get("plainKey").asText();

        PersistentTaskPlanRecord plan = planRepository.save(new PersistentTaskPlanRecord(1L, "plan-a", null, "admin"));
        long scenarioId = scenarioRepository.save(
                new PersistentTaskScenarioRecord(plan.getId(), 1L, "scenario-a", 0)).getId();
        PersistentScenarioExecutionRecord execution = new PersistentScenarioExecutionRecord(
                scenarioId,
                "{\"threads\":1,\"rampUp\":0,\"duration\":0,\"loops\":1,\"jmeterProperties\":{},\"mode\":\"DISTRIBUTED\",\"controllerNodeId\":1,\"workerNodeIds\":[1],\"monitorTargetIds\":[]}"
        );
        Path tempDir = Files.createTempDirectory("agent-evidence-test");
        execution.markRunning(
                tempDir.resolve("result.jtl").toString(),
                tempDir.resolve("jmeter.log").toString()
        );
        execution.markSuccess(0);
        executionId = executionRepository.save(execution).getId();
        long bucket = executionRepository.findById(executionId).orElseThrow().getStartTime().toEpochMilli();
        metricSeriesRepository.save(new PersistentExecutionMetricSeriesRecord(
                executionId, bucket, "checkout", 10L, 0L, 50.0, 100L, 120L));
    }

    @Test
    void collectsBaseAndDeepEvidenceWithExplicitAvailability() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/agent/executions/" + executionId + "/evidence")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.schemaVersion", is("1")))
                .andExpect(jsonPath("$.data.executionId", is((int) executionId)))
                .andExpect(jsonPath("$.data.summaries[*].sourceType",
                        hasItems("execution", "aggregate", "series", "failure-sample", "prometheus",
                                "deep:db-metrics", "deep:trace", "deep:app-log", "deep:slow-sql", "deep:profiling")))
                .andReturn();
        JsonNode summaries = objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/summaries");
        for (JsonNode summary : summaries) {
            String sourceType = summary.get("sourceType").asText();
            JsonNode availability = summary.get("availability");
            assertThat(availability).as(sourceType + " declares availability").isNotNull();
            assertThat(summary.get("key").get("executionId").asLong()).isEqualTo(executionId);
            if (sourceType.startsWith("deep:")) {
                assertThat(availability.get("present").asBoolean())
                        .as(sourceType + " must not fake data while disabled")
                        .isFalse();
                assertThat(availability.get("missingReason").asText()).isEqualTo("SOURCE_UNAVAILABLE");
            }
        }
    }

    @Test
    void seriesEvidenceIsPresentInsideExecutionWindow() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/agent/executions/" + executionId + "/evidence")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode summaries = objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/summaries");
        for (JsonNode summary : summaries) {
            if ("series".equals(summary.get("sourceType").asText())) {
                assertThat(summary.get("availability").get("present").asBoolean()).isTrue();
                assertThat(summary.get("sourceRef").asText()).startsWith("metric-series#");
            }
        }
    }

    @Test
    void traceIdDrillDownIncludesOnlyTraceCapableSources() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/agent/executions/" + executionId + "/evidence")
                        .header("X-API-Key", apiKey)
                        .param("traceId", "trace-abc-123"))
                .andExpect(status().isOk())
                .andReturn();
        List<String> deepTypes = deepSourceTypes(result);
        assertThat(deepTypes).contains("deep:trace", "deep:app-log");
        assertThat(deepTypes).doesNotContain("deep:db-metrics", "deep:slow-sql", "deep:profiling");
    }

    @Test
    void kindsFilterSelectsOnlyRequestedSources() throws Exception {
        mockMvc.perform(get("/api/agent/executions/" + executionId + "/evidence")
                        .header("X-API-Key", apiKey)
                        .param("kinds", "execution", "trace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summaries[*].sourceType", hasItems("execution", "deep:trace")))
                .andExpect(jsonPath("$.data.summaries.length()", is(2)));
    }

    @Test
    void deletedExecutionReportsDeletedEvidenceInsteadOfFakeEmpty() throws Exception {
        PersistentScenarioExecutionRecord doomed = new PersistentScenarioExecutionRecord(
                executionRepository.findById(executionId).orElseThrow().getScenarioId(),
                "{\"threads\":1}"
        );
        long doomedId = executionRepository.save(doomed).getId();
        executionRepository.deleteById(doomedId);

        MvcResult result = mockMvc.perform(get("/api/agent/executions/" + doomedId + "/evidence")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode summaries = objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/summaries");
        boolean anyDeleted = false;
        for (JsonNode summary : summaries) {
            JsonNode availability = summary.get("availability");
            if (availability != null && !availability.get("present").asBoolean()
                    && "DELETED".equals(availability.get("missingReason").asText())) {
                anyDeleted = true;
            }
        }
        assertThat(anyDeleted).isTrue();
    }

    private List<String> deepSourceTypes(MvcResult result) throws Exception {
        JsonNode summaries = objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/summaries");
        List<String> types = new ArrayList<>();
        for (JsonNode summary : summaries) {
            if (summary.get("sourceType").asText().startsWith("deep:")) {
                types.add(summary.get("sourceType").asText());
            }
        }
        return types;
    }

    private String loginToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }
}
