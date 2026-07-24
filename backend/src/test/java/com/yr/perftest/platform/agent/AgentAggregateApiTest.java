package com.yr.perftest.platform.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.execution.TaskExecutionResult;
import com.yr.perftest.platform.execution.aggregate.AggregateReportService;
import com.yr.perftest.platform.execution.aggregate.AggregateSnapshotCodec;
import com.yr.perftest.platform.execution.aggregate.PersistentAggregateReportRecord;
import com.yr.perftest.platform.execution.aggregate.PersistentAggregateReportRepository;
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
import java.util.List;

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
        "spring.datasource.url=jdbc:h2:mem:agent-aggregate-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AgentAggregateApiTest {
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
    private PersistentAggregateReportRepository aggregateReportRepository;

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
        persistAggregateRows(executionId, List.of(
                row("first", 10),
                row("second", 20),
                row("third", 30)
        ));
    }

    @Test
    void returnsAggregateRowsInPagedEnvelope() throws Exception {
        mockMvc.perform(get("/api/agent/executions/" + executionId + "/aggregate")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId", notNullValue()))
                .andExpect(jsonPath("$.schemaVersion", is("1")))
                .andExpect(jsonPath("$.error", nullValue()))
                .andExpect(jsonPath("$.data.items[*].label", contains("first", "second", "third")))
                .andExpect(jsonPath("$.data.availability.present", is(true)))
                .andExpect(jsonPath("$.data.availability.sourceRef", is("aggregate#0-2")))
                .andExpect(jsonPath("$.truncated", is(false)))
                .andExpect(jsonPath("$.nextCursor", nullValue()));
    }

    @Test
    void continuesAfterOrdinalItemBudgetWithoutDuplicatesOrGaps() throws Exception {
        MvcResult firstPage = mockMvc.perform(get("/api/agent/executions/" + executionId + "/aggregate")
                        .header("X-API-Key", apiKey)
                        .param("maxItems", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].label", contains("first", "second")))
                .andExpect(jsonPath("$.truncated", is(true)))
                .andExpect(jsonPath("$.nextCursor", notNullValue()))
                .andExpect(jsonPath("$.warnings", hasItem("budget:items")))
                .andReturn();
        String nextCursor = objectMapper.readTree(firstPage.getResponse().getContentAsString())
                .get("nextCursor")
                .asText();

        mockMvc.perform(get("/api/agent/executions/" + executionId + "/aggregate")
                        .header("X-API-Key", apiKey)
                        .param("cursor", nextCursor)
                        .param("maxItems", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].label", contains("third")))
                .andExpect(jsonPath("$.data.availability.sourceRef", is("aggregate#2-2")))
                .andExpect(jsonPath("$.truncated", is(false)))
                .andExpect(jsonPath("$.nextCursor", nullValue()));
    }

    @Test
    void reportsNoDataForExecutionWithoutAggregateRows() throws Exception {
        long emptyExecutionId = createFinishedExecution();

        mockMvc.perform(get("/api/agent/executions/" + emptyExecutionId + "/aggregate")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.availability.present", is(false)))
                .andExpect(jsonPath("$.data.availability.missingReason", is("NO_DATA")));
    }

    @Test
    void returnsNotFoundForMissingExecution() throws Exception {
        mockMvc.perform(get("/api/agent/executions/999999/aggregate")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("NOT_FOUND")))
                .andExpect(jsonPath("$.data", nullValue()));
    }

    @Test
    void returnsAuthenticationFailedWithoutIdentity() throws Exception {
        mockMvc.perform(get("/api/agent/executions/" + executionId + "/aggregate"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("AUTHENTICATION_FAILED")));
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

    private void persistAggregateRows(long id, List<TaskExecutionResult.AggregateRow> rows) throws Exception {
        TaskExecutionResult.Summary summary = new TaskExecutionResult.Summary(60, 10, 20, 30, 0, "final");
        aggregateReportRepository.save(new PersistentAggregateReportRecord(
                id,
                AggregateReportService.ACCURACY_FINAL,
                1_700_000_000_000L,
                1_700_000_001_000L,
                1.0,
                objectMapper.writeValueAsString(summary),
                objectMapper.writeValueAsString(rows),
                null,
                Instant.now(),
                AggregateSnapshotCodec.BUILDER_VERSION
        ));
    }

    private TaskExecutionResult.AggregateRow row(String label, int samples) {
        return new TaskExecutionResult.AggregateRow(
                label,
                "thread-1",
                samples,
                20,
                18,
                25,
                30,
                35,
                10,
                40,
                0,
                samples
        );
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
