package com.yr.perftest.platform.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agent-execution-control-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AgentExecutionControlApiTest {
    private static final String CONFIG_JSON = "{\"threads\":1,\"rampUp\":0,\"duration\":0,\"loops\":1,\"jmeterProperties\":{},\"mode\":\"DISTRIBUTED\",\"controllerNodeId\":1,\"workerNodeIds\":[1],\"monitorTargetIds\":[]}";

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

    private String apiKey;
    private long scenarioId;

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
        plan.forceState(com.yr.perftest.platform.task.plandoc.PlanPhase.EXECUTION,
                com.yr.perftest.platform.task.plandoc.PlanStatus.PENDING);
        planRepository.save(plan);
        PersistentTaskScenarioRecord scenario = scenarioRepository.save(
                new PersistentTaskScenarioRecord(plan.getId(), 1L, "scenario-a", 0));
        scenario.updateProfile("scenario-a", 1L, "{}", 1L, null, null, null);
        scenarioId = scenarioRepository.save(scenario).getId();
    }

    @Test
    void startWithIdempotencyKeyStartsOnlyOnce() throws Exception {
        MvcResult first = mockMvc.perform(post("/api/agent/scenarios/" + scenarioId + "/executions")
                        .header("X-API-Key", apiKey)
                        .header("Idempotency-Key", "start-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"executionName\":\"run-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error", nullValue()))
                .andExpect(jsonPath("$.data.schemaVersion", is("1")))
                .andExpect(jsonPath("$.data.status", is("QUEUED")))
                .andExpect(jsonPath("$.data.replayed", is(false)))
                .andReturn();
        long executionId = objectMapper.readTree(first.getResponse().getContentAsString())
                .at("/data/executionId").asLong();

        mockMvc.perform(post("/api/agent/scenarios/" + scenarioId + "/executions")
                        .header("X-API-Key", apiKey)
                        .header("Idempotency-Key", "start-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"executionName\":\"run-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.executionId", is((int) executionId)))
                .andExpect(jsonPath("$.data.replayed", is(true)));

        org.assertj.core.api.Assertions.assertThat(
                executionRepository.findAllByScenarioIdOrderByIdDesc(scenarioId)).hasSize(1);
    }

    @Test
    void sameKeyWithDifferentBodyConflicts() throws Exception {
        mockMvc.perform(post("/api/agent/scenarios/" + scenarioId + "/executions")
                        .header("X-API-Key", apiKey)
                        .header("Idempotency-Key", "start-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"executionName\":\"run-1\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/agent/scenarios/" + scenarioId + "/executions")
                        .header("X-API-Key", apiKey)
                        .header("Idempotency-Key", "start-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"executionName\":\"run-2\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("IDEMPOTENCY_CONFLICT")))
                .andExpect(jsonPath("$.data", nullValue()));
    }

    @Test
    void cancelQueuedExecutionThenStatusIsCancelled() throws Exception {
        PersistentScenarioExecutionRecord execution = executionRepository.save(
                new PersistentScenarioExecutionRecord(scenarioId, CONFIG_JSON));
        long executionId = execution.getId();

        mockMvc.perform(post("/api/agent/executions/" + executionId + "/cancel")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CANCELLED")));

        mockMvc.perform(get("/api/agent/executions/" + executionId + "/status")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.executionId", is((int) executionId)))
                .andExpect(jsonPath("$.data.status", is("CANCELLED")))
                .andExpect(jsonPath("$.data.endedAt", notNullValue()));
    }

    @Test
    void stopAndCancelOnFinishedExecutionReturnConflict() throws Exception {
        PersistentScenarioExecutionRecord execution = executionRepository.save(
                new PersistentScenarioExecutionRecord(scenarioId, CONFIG_JSON));
        execution.markRunning("result.jtl", "jmeter.log");
        execution.markSuccess(0);
        long executionId = executionRepository.save(execution).getId();

        mockMvc.perform(post("/api/agent/executions/" + executionId + "/stop")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("EXECUTION_CONFLICT")));
        mockMvc.perform(post("/api/agent/executions/" + executionId + "/cancel")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("EXECUTION_CONFLICT")));
    }

    @Test
    void precheckReportsMissingNodeRecords() throws Exception {
        mockMvc.perform(post("/api/agent/scenarios/" + scenarioId + "/precheck")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.schemaVersion", is("1")))
                .andExpect(jsonPath("$.data.valid", is(false)))
                .andExpect(jsonPath("$.data.errors[0]", is("controller node 1 does not exist")))
                .andExpect(jsonPath("$.data.nodes[0].status", is("MISSING")))
                .andExpect(jsonPath("$.data.queueAhead", is(0)));
    }

    @Test
    void returnsAuthenticationFailedWithoutIdentity() throws Exception {
        mockMvc.perform(post("/api/agent/scenarios/" + scenarioId + "/executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("AUTHENTICATION_FAILED")));
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
