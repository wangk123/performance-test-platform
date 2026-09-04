package com.yr.perftest.platform.governance;

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
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agent-governance-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false",
        "platform.governance.rate-limit.machine-capacity=2",
        "platform.governance.rate-limit.anonymous-capacity=2",
        "platform.governance.rate-limit.human-capacity=100"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Import(AgentGovernanceApiTest.TestGovernanceController.class)
class AgentGovernanceApiTest {
    private static final String CONFIG_JSON = "{\"threads\":1,\"rampUp\":0,\"duration\":0,\"loops\":1,"
            + "\"jmeterProperties\":{},\"mode\":\"DISTRIBUTED\",\"controllerNodeId\":1,"
            + "\"workerNodeIds\":[1],\"monitorTargetIds\":[]}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PersistentRequestAuditRepository requestAuditRepository;

    @Autowired
    private PersistentExecutionAuditRepository executionAuditRepository;

    @Autowired
    private PersistentTaskPlanRepository planRepository;

    @Autowired
    private PersistentTaskScenarioRepository scenarioRepository;

    @Autowired
    private PersistentScenarioExecutionRepository executionRepository;

    private String apiKey;

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
    }

    @Test
    void redactsSensitiveFieldsInAgentResponses() throws Exception {
        mockMvc.perform(get("/api/agent/test-governance/sensitive").header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password", is("***")))
                .andExpect(jsonPath("$.token", is("***")))
                .andExpect(jsonPath("$.nested.apiKey", is("***")))
                .andExpect(jsonPath("$.ok", is("visible")))
                .andExpect(jsonPath("$.count", is(7)));
    }

    @Test
    void rateLimitsMachinePrincipalBeyondCapacity() throws Exception {
        mockMvc.perform(get("/api/agent/test-governance/sensitive").header("X-API-Key", apiKey))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/agent/test-governance/sensitive").header("X-API-Key", apiKey))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/agent/test-governance/sensitive").header("X-API-Key", apiKey))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code", is("RATE_LIMITED")))
                .andExpect(jsonPath("$.data", nullValue()));
    }

    @Test
    void auditsAgentRequestsWithPrincipalAndStatus() throws Exception {
        mockMvc.perform(get("/api/agent/test-governance/sensitive").header("X-API-Key", apiKey))
                .andExpect(status().isOk());

        List<PersistentRequestAuditRecord> records = requestAuditRepository.findAll().stream()
                .filter(record -> record.getPath().equals("/api/agent/test-governance/sensitive"))
                .toList();
        assertThat(records).hasSize(1);
        PersistentRequestAuditRecord record = records.get(0);
        assertThat(record.getPath()).isEqualTo("/api/agent/test-governance/sensitive");
        assertThat(record.getMethod()).isEqualTo("GET");
        assertThat(record.getStatusCode()).isEqualTo(200);
        assertThat(record.getPrincipalType()).isEqualTo("MACHINE");
        assertThat(record.getPrincipalName()).isNotBlank();
        assertThat(record.getRequestId()).isNotBlank();
    }

    @Test
    void anonymousAgentRequestsAreAuditedAsAnonymous() throws Exception {
        mockMvc.perform(get("/api/agent/test-governance/sensitive"))
                .andExpect(status().isUnauthorized());

        List<PersistentRequestAuditRecord> records = requestAuditRepository.findAll().stream()
                .filter(record -> "ANONYMOUS".equals(record.getPrincipalType()))
                .toList();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getStatusCode()).isEqualTo(401);
    }

    @Test
    void uiFaceRequestsAreNotAuditedOrRedacted() throws Exception {
        String adminToken = loginToken();
        mockMvc.perform(get("/api/projects").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        assertThat(requestAuditRepository.findAll())
                .noneMatch(record -> record.getPath().equals("/api/projects"));
    }

    @Test
    void executionStartAndCancelAreAudited() throws Exception {
        PersistentTaskPlanRecord plan = planRepository.save(new PersistentTaskPlanRecord(1L, "plan-a", null, "admin"));
        plan.forceState(com.yr.perftest.platform.task.plandoc.PlanPhase.EXECUTION,
                com.yr.perftest.platform.task.plandoc.PlanStatus.PENDING);
        planRepository.save(plan);
        PersistentTaskScenarioRecord scenario = scenarioRepository.save(
                new PersistentTaskScenarioRecord(plan.getId(), 1L, "scenario-a", 0));
        scenario.updateProfile("scenario-a", 1L, "{}", 1L, null, null, null);
        long scenarioId = scenarioRepository.save(scenario).getId();

        MvcResult started = mockMvc.perform(post("/api/agent/scenarios/" + scenarioId + "/executions")
                        .header("X-API-Key", apiKey)
                        .header("Idempotency-Key", "audit-start-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"executionName\":\"audit-run\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long executionId = objectMapper.readTree(started.getResponse().getContentAsString())
                .at("/data/executionId").asLong();

        List<PersistentExecutionAuditRecord> startAudits = executionAuditRepository.findAll().stream()
                .filter(record -> record.getExecutionId() == executionId && "START".equals(record.getAction()))
                .toList();
        assertThat(startAudits).hasSize(1);
        assertThat(startAudits.get(0).getPrincipalType()).isEqualTo("MACHINE");
        assertThat(startAudits.get(0).isReplayed()).isFalse();

        PersistentScenarioExecutionRecord queued = executionRepository.save(
                new PersistentScenarioExecutionRecord(scenarioId, CONFIG_JSON));
        long queuedId = queued.getId();

        mockMvc.perform(post("/api/agent/executions/" + queuedId + "/cancel")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CANCELLED")));

        List<PersistentExecutionAuditRecord> cancelAudits = executionAuditRepository.findAll().stream()
                .filter(record -> record.getExecutionId() == queuedId && "CANCEL".equals(record.getAction()))
                .toList();
        assertThat(cancelAudits).hasSize(1);
        assertThat(cancelAudits.get(0).getPrincipalType()).isEqualTo("MACHINE");
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

    @RestController
    @RequestMapping("/api/agent/test-governance")
    static class TestGovernanceController {
        @GetMapping("/sensitive")
        Map<String, Object> sensitive() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("password", "top-secret");
            payload.put("token", "Bearer abc123");
            payload.put("nested", Map.of("apiKey", "key-12345"));
            payload.put("ok", "visible");
            payload.put("count", 7);
            return payload;
        }
    }
}
