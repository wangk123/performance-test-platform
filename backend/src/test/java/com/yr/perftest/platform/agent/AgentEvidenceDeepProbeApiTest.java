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

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * db-metrics 深度证据源启用后的端到端用例：Prometheus 不可达时必须显式
 * SOURCE_UNAVAILABLE，不得伪造空成功。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agent-deep-probe-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false",
        "platform.evidence.deep.kinds.db-metrics.enabled=true",
        "platform.monitoring.prometheus.base-url=http://127.0.0.1:1",
        "platform.monitoring.prometheus.connect-timeout-ms=200",
        "platform.monitoring.prometheus.request-timeout-ms=200"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AgentEvidenceDeepProbeApiTest {
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
        long scenarioId = scenarioRepository.save(
                new PersistentTaskScenarioRecord(plan.getId(), 1L, "scenario-a", 0)).getId();
        PersistentScenarioExecutionRecord execution = new PersistentScenarioExecutionRecord(
                scenarioId, "{\"threads\":1}");
        Path tempDir = Files.createTempDirectory("agent-deep-probe-test");
        execution.markRunning(
                tempDir.resolve("result.jtl").toString(),
                tempDir.resolve("jmeter.log").toString()
        );
        execution.markSuccess(0);
        executionId = executionRepository.save(execution).getId();
    }

    @Test
    void enabledDbMetricsReportsSourceUnavailableWhenPrometheusUnreachable() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/agent/executions/" + executionId + "/evidence")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode summaries = objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/summaries");
        JsonNode dbMetrics = null;
        for (JsonNode summary : summaries) {
            if ("deep:db-metrics".equals(summary.get("sourceType").asText())) {
                dbMetrics = summary;
            }
        }
        assertThat(dbMetrics).isNotNull();
        assertThat(dbMetrics.get("availability").get("present").asBoolean()).isFalse();
        assertThat(dbMetrics.get("availability").get("missingReason").asText()).isEqualTo("SOURCE_UNAVAILABLE");
        assertThat(dbMetrics.get("sourceRef").asText()).startsWith("prometheus:db-metrics");
        assertThat(dbMetrics.get("sourceClock").asText()).isEqualTo("prometheus");
        assertThat(dbMetrics.get("summary").get("retentionDays").asInt()).isEqualTo(7);
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
