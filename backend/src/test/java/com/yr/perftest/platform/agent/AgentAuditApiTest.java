package com.yr.perftest.platform.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.execution.distributed.ExecutionNodeRole;
import com.yr.perftest.platform.execution.distributed.PersistentExecutionNodeRecord;
import com.yr.perftest.platform.execution.distributed.PersistentExecutionNodeRepository;
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

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 审计轨迹查询入口测试（T13）：请求审计与执行审计可重建平台侧操作轨迹。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agent-audit-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AgentAuditApiTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PersistentTaskPlanRepository planRepository;

    @Autowired
    private PersistentTaskScenarioRepository scenarioRepository;

    @Autowired
    private PersistentExecutionNodeRepository nodeRepository;

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
    void requestAuditReconstructsAgentCalls() throws Exception {
        // 第一次查询能看到 setup 阶段的请求（自身审计行在响应写出后才落库）
        mockMvc.perform(get("/api/agent/audit/requests?limit=10")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", not(empty())))
                .andExpect(jsonPath("$.data.items[*].path", hasItem("/api/agent-api-keys")))
                .andExpect(jsonPath("$.data.items[*].principalType", hasItem("HUMAN")));

        // 第二次查询能看到第一次查询自身的审计行，轨迹可逐条重建
        mockMvc.perform(get("/api/agent/audit/requests?limit=10")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].path", hasItem("/api/agent/audit/requests")))
                .andExpect(jsonPath("$.data.items[*].principalType", hasItem("MACHINE")));
    }

    @Test
    void executionAuditReconstructsStartTrace() throws Exception {
        PersistentTaskPlanRecord plan = planRepository.save(new PersistentTaskPlanRecord(1L, "plan-a", null, "admin"));
        plan.forceState(com.yr.perftest.platform.task.plandoc.PlanPhase.EXECUTION,
                com.yr.perftest.platform.task.plandoc.PlanStatus.PENDING);
        planRepository.save(plan);
        PersistentTaskScenarioRecord scenario = scenarioRepository.save(
                new PersistentTaskScenarioRecord(plan.getId(), 1L, "scenario-a", 0));
        PersistentExecutionNodeRecord node = nodeRepository.save(new PersistentExecutionNodeRecord(
                "node-1", "10.0.0.1", 22, "ops", "/keys/id", ExecutionNodeRole.CONTROLLER, "/tmp/perf"));
        scenario.updateProfile("scenario-a", 1L, "{}", node.getId(), null, null, null);
        long scenarioId = scenarioRepository.save(scenario).getId();

        MvcResult started = mockMvc.perform(post("/api/agent/scenarios/" + scenarioId + "/executions")
                        .header("X-API-Key", apiKey)
                        .header("Idempotency-Key", "audit-trace-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"executionName\":\"audit-run\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long executionId = objectMapper.readTree(started.getResponse().getContentAsString())
                .at("/data/executionId").asLong();

        mockMvc.perform(get("/api/agent/audit/executions")
                        .header("X-API-Key", apiKey)
                        .param("executionId", Long.toString(executionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.items[*].action", hasItem("START")))
                .andExpect(jsonPath("$.data.items[*].principalType", hasItem("MACHINE")));
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
