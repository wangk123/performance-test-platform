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

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agent-analysis-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AgentAnalysisApiTest {
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
        PersistentTaskScenarioRecord scenario = scenarioRepository.save(
                new PersistentTaskScenarioRecord(plan.getId(), 1L, "scenario-a", 0)
        );
        PersistentScenarioExecutionRecord execution = executionRepository.save(
                new PersistentScenarioExecutionRecord(scenario.getId(), "{\"threads\":1,\"rampUp\":0,\"duration\":0,\"loops\":1,\"jmeterProperties\":{},\"mode\":\"DISTRIBUTED\",\"controllerNodeId\":1,\"workerNodeIds\":[1],\"monitorTargetIds\":[]}")
        );
        execution.markRunning("result.jtl", "jmeter.log");
        execution.markSuccess(0);
        executionId = executionRepository.save(execution).getId();

        metricSeriesRepository.save(new PersistentExecutionMetricSeriesRecord(executionId, 1_000L, "checkout", 10L, 0L, 50.0, 100L, 120L));
        metricSeriesRepository.save(new PersistentExecutionMetricSeriesRecord(executionId, 1_000L, "search", 10L, 0L, 100.0, 80L, 100L));
        metricSeriesRepository.save(new PersistentExecutionMetricSeriesRecord(executionId, 2_000L, "checkout", 10L, 1L, 45.0, 200L, 260L));
        metricSeriesRepository.save(new PersistentExecutionMetricSeriesRecord(executionId, 2_000L, "search", 10L, 0L, 95.0, 90L, 110L));
    }

    @Test
    void returnsDeterministicAnalysisEnvelope() throws Exception {
        String path = "/api/agent/executions/" + executionId + "/analysis"
                + "?from=1970-01-01T00:00:00Z&to=1970-01-01T00:00:10Z";
        MvcResult first = mockMvc.perform(get(path).header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion", is("1")))
                .andExpect(jsonPath("$.error", org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.schemaVersion", is("1")))
                .andExpect(jsonPath("$.data.executionId", is((int) executionId)))
                .andExpect(jsonPath("$.data.algorithmVersions.trend", is("1")))
                .andExpect(jsonPath("$.data.algorithmVersions.anomaly", is("1")))
                .andExpect(jsonPath("$.data.algorithmVersions['error-cluster']", is("1")))
                .andExpect(jsonPath("$.data.algorithmVersions['resource-saturation']", is("1")))
                .andExpect(jsonPath("$.data.facts.length()", is(4)))
                .andExpect(jsonPath("$.data.facts[0].kind", is("trend")))
                .andExpect(jsonPath("$.data.facts[0].algorithmVersion", is("1")))
                .andExpect(jsonPath("$.data.facts[0].data.tickCount", is(2)))
                .andExpect(jsonPath("$.data.facts[1].kind", is("anomaly")))
                .andExpect(jsonPath("$.data.facts[2].kind", is("error-cluster")))
                .andExpect(jsonPath("$.data.facts[3].kind", is("resource-saturation")))
                .andExpect(jsonPath("$.data.completeness.length()", is(3)))
                .andReturn();
        MvcResult second = mockMvc.perform(get(path).header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode firstFacts = objectMapper.readTree(first.getResponse().getContentAsString()).at("/data/facts");
        JsonNode secondFacts = objectMapper.readTree(second.getResponse().getContentAsString()).at("/data/facts");
        org.assertj.core.api.Assertions.assertThat(secondFacts).isEqualTo(firstFacts);
    }

    @Test
    void kindsParameterSelectsAlgorithms() throws Exception {
        mockMvc.perform(get("/api/agent/executions/" + executionId + "/analysis")
                        .param("from", "1970-01-01T00:00:00Z")
                        .param("to", "1970-01-01T00:00:10Z")
                        .param("kinds", "trend")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.facts.length()", is(1)))
                .andExpect(jsonPath("$.data.facts[0].kind", is("trend")));
    }

    @Test
    void comparesExecutionWithItselfAsComparable() throws Exception {
        mockMvc.perform(get("/api/agent/executions/compare")
                        .param("baselineId", Long.toString(executionId))
                        .param("candidateId", Long.toString(executionId))
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.algorithmId", is("execution-compare")))
                .andExpect(jsonPath("$.data.algorithmVersion", is("1")))
                .andExpect(jsonPath("$.data.data.comparable", is(true)))
                .andExpect(jsonPath("$.data.data.overallVerdict", is("STABLE")));
    }

    @Test
    void returnsNotFoundForMissingExecution() throws Exception {
        mockMvc.perform(get("/api/agent/executions/999999/analysis")
                        .param("from", "1970-01-01T00:00:00Z")
                        .param("to", "1970-01-01T00:00:10Z")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("NOT_FOUND")));
    }

    @Test
    void returnsAuthenticationFailedWithoutIdentity() throws Exception {
        mockMvc.perform(get("/api/agent/executions/" + executionId + "/analysis"))
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
