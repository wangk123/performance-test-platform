package com.yr.perftest.platform.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.execution.failure.FailureSamplePaths;
import com.yr.perftest.platform.execution.failure.FailureSampleRecord;
import com.yr.perftest.platform.execution.failure.FailureSampleStore;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRecord;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRepository;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.PersistentTaskScenarioRecord;
import com.yr.perftest.platform.task.PersistentTaskScenarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Path;

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
        "spring.datasource.url=jdbc:h2:mem:agent-failure-sample-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AgentFailureSampleApiTest {
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
    private FailureSampleStore failureSampleStore;

    @TempDir
    Path tempDir;

    private String apiKey;
    private long executionId;
    private Path dbPath;

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
        PersistentScenarioExecutionRecord execution = executionRepository.save(
                new PersistentScenarioExecutionRecord(scenario.getId(), "{\"threads\":1,\"rampUp\":0,\"duration\":0,\"loops\":1,\"jmeterProperties\":{},\"mode\":\"DISTRIBUTED\",\"controllerNodeId\":1,\"workerNodeIds\":[1],\"monitorTargetIds\":[]}")
        );
        execution.markRunning(tempDir.resolve("result.jtl").toString(), tempDir.resolve("jmeter.log").toString());
        executionId = executionRepository.save(execution).getId();

        dbPath = FailureSamplePaths.sqlite(tempDir);
        failureSampleStore.initialize(dbPath);
        insertSample(101, "first");
        insertSample(102, "x".repeat(2000));
        insertSample(103, "third");
    }

    @AfterEach
    void tearDown() {
        failureSampleStore.closeConnection(dbPath);
    }

    @Test
    void returnsOrderedFailureSamplesInPagedEnvelope() throws Exception {
        mockMvc.perform(get("/api/agent/executions/" + executionId + "/failure-samples")
                        .header("X-API-Key", apiKey)
                        .param("maxItems", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId", notNullValue()))
                .andExpect(jsonPath("$.schemaVersion", is("1")))
                .andExpect(jsonPath("$.error", nullValue()))
                .andExpect(jsonPath("$.data.items[*].id", contains(1, 2, 3)))
                .andExpect(jsonPath("$.data.availability.present", is(true)))
                .andExpect(jsonPath("$.data.availability.sourceRef", is("failure-samples.db#1-3")))
                .andExpect(jsonPath("$.truncated", is(false)))
                .andExpect(jsonPath("$.nextCursor", nullValue()));
    }

    @Test
    void continuesAfterItemBudgetWithoutDuplicatesOrGaps() throws Exception {
        MvcResult firstPage = mockMvc.perform(get("/api/agent/executions/" + executionId + "/failure-samples")
                        .header("X-API-Key", apiKey)
                        .param("maxItems", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id", contains(1, 2)))
                .andExpect(jsonPath("$.truncated", is(true)))
                .andExpect(jsonPath("$.nextCursor", notNullValue()))
                .andExpect(jsonPath("$.warnings", hasItem("budget:items")))
                .andReturn();
        String nextCursor = objectMapper.readTree(firstPage.getResponse().getContentAsString())
                .get("nextCursor")
                .asText();

        mockMvc.perform(get("/api/agent/executions/" + executionId + "/failure-samples")
                        .header("X-API-Key", apiKey)
                        .param("cursor", nextCursor)
                        .param("maxItems", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id", contains(3)))
                .andExpect(jsonPath("$.truncated", is(false)))
                .andExpect(jsonPath("$.nextCursor", nullValue()));
    }

    @Test
    void truncatesBeforeExceedingByteBudget() throws Exception {
        mockMvc.perform(get("/api/agent/executions/" + executionId + "/failure-samples")
                        .header("X-API-Key", apiKey)
                        .param("maxItems", "10")
                        .param("maxBytes", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id", contains(1)))
                .andExpect(jsonPath("$.truncated", is(true)))
                .andExpect(jsonPath("$.nextCursor", notNullValue()))
                .andExpect(jsonPath("$.warnings", hasItem("budget:bytes")));
    }

    @Test
    void rejectsInvalidBudget() throws Exception {
        mockMvc.perform(get("/api/agent/executions/" + executionId + "/failure-samples")
                        .header("X-API-Key", apiKey)
                        .param("maxItems", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.data", nullValue()));
    }

    @Test
    void returnsNotFoundForMissingExecution() throws Exception {
        mockMvc.perform(get("/api/agent/executions/999999/failure-samples")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("NOT_FOUND")))
                .andExpect(jsonPath("$.data", nullValue()));
    }

    @Test
    void returnsAuthenticationFailedWithoutIdentity() throws Exception {
        mockMvc.perform(get("/api/agent/executions/" + executionId + "/failure-samples"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("AUTHENTICATION_FAILED")));
    }

    private void insertSample(long externalId, String message) throws Exception {
        failureSampleStore.insertReturningId(dbPath, new FailureSampleRecord(
                externalId,
                1_700_000_000_000L + externalId,
                "HTTP Request",
                "500",
                false,
                125,
                message,
                "thread-1",
                "worker-1",
                "https://example.test",
                "",
                "",
                "",
                "",
                "failed"
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
