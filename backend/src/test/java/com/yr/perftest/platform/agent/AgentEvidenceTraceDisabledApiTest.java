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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * trace 深度证据源未启用（默认 enabled=false，不配 endpoint）的端到端用例（T3）：
 * 必须显式 unavailable 且 summary 声明 requiresApproval=true。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agent-trace-disabled-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.h2.console.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class AgentEvidenceTraceDisabledApiTest {
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
        Path tempDir = Files.createTempDirectory("agent-trace-disabled-test");
        execution.markRunning(
                tempDir.resolve("result.jtl").toString(),
                tempDir.resolve("jmeter.log").toString()
        );
        execution.markSuccess(0);
        executionId = executionRepository.save(execution).getId();
    }

    @Test
    void disabledTraceReportsUnavailableWithApprovalFlag() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/agent/executions/" + executionId + "/evidence")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode summaries = objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/summaries");
        JsonNode trace = null;
        for (JsonNode summary : summaries) {
            if ("deep:trace".equals(summary.get("sourceType").asText())) {
                trace = summary;
            }
        }
        assertThat(trace).isNotNull();
        assertThat(trace.get("availability").get("present").asBoolean()).isFalse();
        assertThat(trace.get("availability").get("missingReason").asText()).isEqualTo("SOURCE_UNAVAILABLE");
        assertThat(trace.get("sourceRef").asText()).contains("disabled");
        assertThat(trace.get("summary").get("requiresApproval").asBoolean()).isTrue();
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
