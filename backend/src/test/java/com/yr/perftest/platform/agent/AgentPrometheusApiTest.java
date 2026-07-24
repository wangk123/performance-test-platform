package com.yr.perftest.platform.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.monitoring.MetricSeries;
import com.yr.perftest.platform.monitoring.MetricSeriesPoint;
import com.yr.perftest.platform.monitoring.MonitoringValidationException;
import com.yr.perftest.platform.monitoring.PersistentExecutionMonitorBindingRecord;
import com.yr.perftest.platform.monitoring.PersistentExecutionMonitorBindingRepository;
import com.yr.perftest.platform.monitoring.PrometheusQueryClient;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agent-prometheus-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AgentPrometheusApiTest {
    private static final Instant FROM = Instant.ofEpochSecond(1_700_000_000L);
    private static final Instant TO = FROM.plusSeconds(10);
    private static final int STEP_SECONDS = 5;

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
    private PersistentExecutionMonitorBindingRepository bindingRepository;

    @MockitoBean
    private PrometheusQueryClient prometheusQueryClient;

    private String apiKey;
    private long executionId;

    @BeforeEach
    void setUp() throws Exception {
        apiKey = issueApiKey();
        PersistentTaskPlanRecord plan = planRepository.save(new PersistentTaskPlanRecord(1L, "plan-a", null, "admin"));
        PersistentTaskScenarioRecord scenario = scenarioRepository.save(
                new PersistentTaskScenarioRecord(plan.getId(), 1L, "scenario-a", 0)
        );
        PersistentScenarioExecutionRecord execution = new PersistentScenarioExecutionRecord(
                scenario.getId(),
                "{\"threads\":1,\"rampUp\":0,\"duration\":60,\"loops\":1,\"jmeterProperties\":{},\"mode\":\"DISTRIBUTED\",\"controllerNodeId\":1,\"workerNodeIds\":[1],\"monitorTargetIds\":[101]}"
        );
        execution.markRunning("result.jtl", "jmeter.log");
        executionId = executionRepository.save(execution).getId();

        PersistentExecutionMonitorBindingRecord binding =
                new PersistentExecutionMonitorBindingRecord(executionId, 101L);
        binding.markStart(FROM);
        bindingRepository.save(binding);

        when(prometheusQueryClient.queryRange(anyString(), anyLong(), anyLong(), anyInt()))
                .thenReturn(List.of(new MetricSeries(
                        "server-a",
                        Map.of("instance", "server-a", "target_id", "101"),
                        List.of(
                                new MetricSeriesPoint(FROM.getEpochSecond(), 10),
                                new MetricSeriesPoint(FROM.plusSeconds(5).getEpochSecond(), 20),
                                new MetricSeriesPoint(TO.getEpochSecond(), 30)
                        )
                )));
    }

    @Test
    void returnsBoundedMetricPointsForMetricSelector() throws Exception {
        mockMvc.perform(prometheusRequest(executionId)
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId", notNullValue()))
                .andExpect(jsonPath("$.schemaVersion", is("1")))
                .andExpect(jsonPath("$.error", nullValue()))
                .andExpect(jsonPath("$.data.items[*].timestamp", contains(
                        Math.toIntExact(FROM.getEpochSecond()),
                        Math.toIntExact(FROM.plusSeconds(5).getEpochSecond()),
                        Math.toIntExact(TO.getEpochSecond())
                )))
                .andExpect(jsonPath("$.data.availability.present", is(true)))
                .andExpect(jsonPath("$.data.availability.from", is(FROM.toString())))
                .andExpect(jsonPath("$.data.availability.to", is(TO.toString())))
                .andExpect(jsonPath("$.data.availability.granularity", is("5s")))
                .andExpect(jsonPath("$.data.availability.sourceRef", is("prometheus:SERVER_CPU?step=5")))
                .andExpect(jsonPath("$.truncated", is(false)))
                .andExpect(jsonPath("$.nextCursor", nullValue()));

        verify(prometheusQueryClient).queryRange(
                org.mockito.ArgumentMatchers.contains("node_cpu_seconds_total"),
                anyLong(),
                anyLong(),
                org.mockito.ArgumentMatchers.eq(STEP_SECONDS)
        );
    }

    @Test
    void continuesFromNextAlignedWindowAfterItemBudget() throws Exception {
        MvcResult firstPage = mockMvc.perform(prometheusRequest(executionId)
                        .header("X-API-Key", apiKey)
                        .param("maxItems", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].timestamp", contains(
                        Math.toIntExact(FROM.getEpochSecond()),
                        Math.toIntExact(FROM.plusSeconds(5).getEpochSecond())
                )))
                .andExpect(jsonPath("$.truncated", is(true)))
                .andExpect(jsonPath("$.nextCursor", notNullValue()))
                .andExpect(jsonPath("$.warnings", hasItem("budget:items")))
                .andReturn();
        String cursor = objectMapper.readTree(firstPage.getResponse().getContentAsString())
                .get("nextCursor")
                .asText();

        mockMvc.perform(prometheusRequest(executionId)
                        .header("X-API-Key", apiKey)
                        .param("cursor", cursor)
                        .param("maxItems", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].timestamp", contains(Math.toIntExact(TO.getEpochSecond()))))
                .andExpect(jsonPath("$.truncated", is(false)))
                .andExpect(jsonPath("$.nextCursor", nullValue()));
    }

    @Test
    void mapsPrometheusFailureToDataSourceUnavailableWithAvailability() throws Exception {
        when(prometheusQueryClient.queryRange(anyString(), anyLong(), anyLong(), anyInt()))
                .thenThrow(new MonitoringValidationException("prometheus offline"));

        mockMvc.perform(prometheusRequest(executionId)
                        .header("X-API-Key", apiKey))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code", is("DATA_SOURCE_UNAVAILABLE")))
                .andExpect(jsonPath("$.data.availability.present", is(false)))
                .andExpect(jsonPath("$.data.availability.missingReason", is("SOURCE_UNAVAILABLE")));
    }

    @Test
    void ignoresRawPromqlAndExecutesOnlyMetricKindTemplate() throws Exception {
        String injectedPromql = "vector(999)";

        mockMvc.perform(prometheusRequest(executionId)
                        .header("X-API-Key", apiKey)
                        .param("promql", injectedPromql))
                .andExpect(status().isOk());

        verify(prometheusQueryClient, never()).queryRange(
                org.mockito.ArgumentMatchers.eq(injectedPromql),
                anyLong(),
                anyLong(),
                anyInt()
        );
        verify(prometheusQueryClient).queryRange(
                org.mockito.ArgumentMatchers.contains("node_cpu_seconds_total"),
                anyLong(),
                anyLong(),
                org.mockito.ArgumentMatchers.eq(STEP_SECONDS)
        );
    }

    @Test
    void returnsNotFoundForMissingExecution() throws Exception {
        mockMvc.perform(prometheusRequest(999999)
                        .header("X-API-Key", apiKey))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("NOT_FOUND")))
                .andExpect(jsonPath("$.data", nullValue()));

        verify(prometheusQueryClient, never()).queryRange(anyString(), anyLong(), anyLong(), anyInt());
    }

    @Test
    void returnsAuthenticationFailedWithoutIdentity() throws Exception {
        mockMvc.perform(prometheusRequest(executionId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("AUTHENTICATION_FAILED")));

        verify(prometheusQueryClient, never()).queryRange(anyString(), anyLong(), anyLong(), anyInt());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder prometheusRequest(long id) {
        return get("/api/agent/executions/" + id + "/prometheus")
                .param("metricSelector", "SERVER_CPU")
                .param("from", FROM.toString())
                .param("to", TO.toString())
                .param("step", Integer.toString(STEP_SECONDS));
    }

    private String issueApiKey() throws Exception {
        String adminToken = loginToken();
        MvcResult issued = mockMvc.perform(post("/api/agent-api-keys")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":\"ops\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(issued.getResponse().getContentAsString()).get("plainKey").asText();
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
