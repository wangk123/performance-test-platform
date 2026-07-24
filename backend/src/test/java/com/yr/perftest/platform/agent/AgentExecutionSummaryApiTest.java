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
        "spring.datasource.url=jdbc:h2:mem:agent-execution-summary-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AgentExecutionSummaryApiTest {
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
        PersistentScenarioExecutionRecord execution = executionRepository.save(
                new PersistentScenarioExecutionRecord(scenario.getId(), "{\"threads\":1,\"rampUp\":0,\"duration\":0,\"loops\":1,\"jmeterProperties\":{},\"mode\":\"DISTRIBUTED\",\"controllerNodeId\":1,\"workerNodeIds\":[1],\"monitorTargetIds\":[]}")
        );
        execution.setExecutionName("run-1");
        executionId = executionRepository.save(execution).getId();
    }

    @Test
    void returnsEnvelopeSummaryForExistingExecution() throws Exception {
        mockMvc.perform(get("/api/agent/executions/" + executionId + "/summary")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId", notNullValue()))
                .andExpect(jsonPath("$.schemaVersion", is("1")))
                .andExpect(jsonPath("$.error", nullValue()))
                .andExpect(jsonPath("$.truncated", nullValue()))
                .andExpect(jsonPath("$.nextCursor", nullValue()))
                .andExpect(jsonPath("$.data.executionId", is((int) executionId)))
                .andExpect(jsonPath("$.data.status", is("QUEUED")))
                .andExpect(jsonPath("$.data.scenarioName", is("scenario-a")))
                .andExpect(jsonPath("$.data.samples", notNullValue()))
                .andExpect(jsonPath("$.data.throughput", notNullValue()))
                .andExpect(jsonPath("$.data.avgRtMs", notNullValue()))
                .andExpect(jsonPath("$.data.p95RtMs", notNullValue()))
                .andExpect(jsonPath("$.data.errorRate", notNullValue()));
    }

    @Test
    void returnsNotFoundEnvelopeForMissingExecution() throws Exception {
        mockMvc.perform(get("/api/agent/executions/999999/summary")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("NOT_FOUND")))
                .andExpect(jsonPath("$.data", nullValue()));
    }

    @Test
    void returnsAuthenticationFailedWithoutIdentity() throws Exception {
        mockMvc.perform(get("/api/agent/executions/" + executionId + "/summary"))
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
