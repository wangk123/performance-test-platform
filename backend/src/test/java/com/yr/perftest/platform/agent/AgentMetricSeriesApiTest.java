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

import java.time.Instant;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agent-metric-series-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AgentMetricSeriesApiTest {
    private static final long FIRST_TICK_MS = 1_700_000_000_000L;

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
    private long scenarioId;
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
        PersistentTaskScenarioRecord scenario = scenarioRepository.save(
                new PersistentTaskScenarioRecord(plan.getId(), 1L, "scenario-a", 0)
        );
        scenarioId = scenario.getId();
        executionId = createFinishedExecution();
        insertTick(executionId, FIRST_TICK_MS, 10);
        insertTick(executionId, FIRST_TICK_MS + 1_000, 20);
        insertTick(executionId, FIRST_TICK_MS + 2_000, 30);
        insertTick(executionId, FIRST_TICK_MS + 10_000, 40);
    }

    @Test
    void returnsWindowedTicksWithActualAvailabilityCoverage() throws Exception {
        mockMvc.perform(seriesRequest(executionId, FIRST_TICK_MS, FIRST_TICK_MS + 2_000)
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId", notNullValue()))
                .andExpect(jsonPath("$.schemaVersion", is("1")))
                .andExpect(jsonPath("$.error", nullValue()))
                .andExpect(jsonPath("$.data.items[*].bucketTimeMs", contains(
                        FIRST_TICK_MS,
                        FIRST_TICK_MS + 1_000,
                        FIRST_TICK_MS + 2_000
                )))
                .andExpect(jsonPath("$.data.availability.present", is(true)))
                .andExpect(jsonPath("$.data.availability.from", is(Instant.ofEpochMilli(FIRST_TICK_MS).toString())))
                .andExpect(jsonPath("$.data.availability.to", is(Instant.ofEpochMilli(FIRST_TICK_MS + 2_000).toString())))
                .andExpect(jsonPath("$.data.availability.granularity", is("1s")))
                .andExpect(jsonPath("$.truncated", is(false)))
                .andExpect(jsonPath("$.nextCursor", nullValue()));
    }

    @Test
    void continuesAfterBucketTimeItemBudgetWithoutDuplicatesOrGaps() throws Exception {
        MvcResult firstPage = mockMvc.perform(seriesRequest(executionId, FIRST_TICK_MS, FIRST_TICK_MS + 2_000)
                        .header("X-API-Key", apiKey)
                        .param("maxItems", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].bucketTimeMs", contains(
                        FIRST_TICK_MS,
                        FIRST_TICK_MS + 1_000
                )))
                .andExpect(jsonPath("$.truncated", is(true)))
                .andExpect(jsonPath("$.nextCursor", notNullValue()))
                .andExpect(jsonPath("$.warnings", hasItem("budget:items")))
                .andReturn();
        String nextCursor = objectMapper.readTree(firstPage.getResponse().getContentAsString())
                .get("nextCursor")
                .asText();

        mockMvc.perform(seriesRequest(executionId, FIRST_TICK_MS, FIRST_TICK_MS + 2_000)
                        .header("X-API-Key", apiKey)
                        .param("cursor", nextCursor)
                        .param("maxItems", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].bucketTimeMs", contains(FIRST_TICK_MS + 2_000)))
                .andExpect(jsonPath("$.truncated", is(false)))
                .andExpect(jsonPath("$.nextCursor", nullValue()));
    }

    @Test
    void reportsNoDataWithoutSubstitutingTicksOutsideRequestedWindow() throws Exception {
        mockMvc.perform(seriesRequest(executionId, FIRST_TICK_MS + 5_000, FIRST_TICK_MS + 6_000)
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.availability.present", is(false)))
                .andExpect(jsonPath("$.data.availability.missingReason", is("NO_DATA")));
    }

    @Test
    void returnsNotFoundForMissingExecution() throws Exception {
        mockMvc.perform(seriesRequest(999999, FIRST_TICK_MS, FIRST_TICK_MS + 2_000)
                        .header("X-API-Key", apiKey))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("NOT_FOUND")))
                .andExpect(jsonPath("$.data", nullValue()));
    }

    @Test
    void returnsAuthenticationFailedWithoutIdentity() throws Exception {
        mockMvc.perform(seriesRequest(executionId, FIRST_TICK_MS, FIRST_TICK_MS + 2_000))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("AUTHENTICATION_FAILED")));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder seriesRequest(
            long id,
            long fromMs,
            long toMs
    ) {
        return get("/api/agent/executions/" + id + "/metrics/series")
                .param("from", Instant.ofEpochMilli(fromMs).toString())
                .param("to", Instant.ofEpochMilli(toMs).toString())
                .param("granularity", "1s");
    }

    private long createFinishedExecution() {
        PersistentScenarioExecutionRecord execution = new PersistentScenarioExecutionRecord(
                scenarioId,
                "{\"threads\":1,\"rampUp\":0,\"duration\":0,\"loops\":1,\"jmeterProperties\":{},\"mode\":\"DISTRIBUTED\",\"controllerNodeId\":1,\"workerNodeIds\":[1],\"monitorTargetIds\":[]}"
        );
        execution.markRunning("result.jtl", "jmeter.log");
        execution.markSuccess(0);
        return executionRepository.save(execution).getId();
    }

    private void insertTick(long id, long bucketTimeMs, long samples) {
        metricSeriesRepository.save(new PersistentExecutionMetricSeriesRecord(
                id,
                bucketTimeMs,
                "HTTP Request",
                samples,
                0L,
                (double) samples,
                20L,
                30L
        ));
    }

    private String loginToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("token").asText();
    }
}
