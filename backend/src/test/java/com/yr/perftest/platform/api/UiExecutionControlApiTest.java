package com.yr.perftest.platform.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.execution.distributed.ExecutionNodeRole;
import com.yr.perftest.platform.execution.distributed.PersistentExecutionNodeRecord;
import com.yr.perftest.platform.execution.distributed.PersistentExecutionNodeRepository;
import com.yr.perftest.platform.governance.PersistentExecutionAuditRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * C1：UI 面执行控制路径与 agent 面共用同一条控制 seam。
 * UI 触发/停止走 ExecutionControlService：幂等键生效、执行审计以 HUMAN 身份落库、冲突返回 409。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ui-execution-control-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class UiExecutionControlApiTest {
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

    @Autowired
    private PersistentScenarioExecutionRepository executionRepository;

    @Autowired
    private PersistentExecutionAuditRepository executionAuditRepository;

    private String adminToken;
    private long scenarioId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = loginToken();
        PersistentTaskPlanRecord plan = planRepository.save(new PersistentTaskPlanRecord(1L, "plan-a", null, "admin"));
        PersistentTaskScenarioRecord scenario = scenarioRepository.save(
                new PersistentTaskScenarioRecord(plan.getId(), 1L, "scenario-a", 0));
        PersistentExecutionNodeRecord node = nodeRepository.save(new PersistentExecutionNodeRecord(
                "node-1", "10.0.0.1", 22, "ops", "/keys/id", ExecutionNodeRole.CONTROLLER, "/tmp/perf"));
        scenario.updateProfile("scenario-a", 1L, "{}", node.getId(), null, null, null);
        scenarioId = scenarioRepository.save(scenario).getId();
    }

    @Test
    void uiTriggerStartsOncePerIdempotencyKeyAndAuditsAsHuman() throws Exception {
        MvcResult first = mockMvc.perform(post("/api/scenarios/" + scenarioId + "/executions")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", "ui-idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"executionName\":\"ui-run\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long firstExecutionId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asLong();

        MvcResult second = mockMvc.perform(post("/api/scenarios/" + scenarioId + "/executions")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", "ui-idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"executionName\":\"ui-run\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long secondExecutionId = objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asLong();

        assertThat(secondExecutionId).isEqualTo(firstExecutionId);
        assertThat(executionRepository.findAllByScenarioIdOrderByIdDesc(scenarioId)).hasSize(1);
        assertThat(executionAuditRepository.findAll())
                .anySatisfy(audit -> {
                    assertThat(audit.getExecutionId()).isEqualTo(firstExecutionId);
                    assertThat(audit.getAction()).isEqualTo("START");
                    assertThat(audit.getPrincipalType()).isEqualTo("HUMAN");
                    assertThat(audit.getPrincipalName()).isEqualTo("admin");
                });
    }

    @Test
    void uiStopAuditsAndConflictOnFinishedMapsTo409() throws Exception {
        var execution = executionRepository.save(
                new PersistentScenarioExecutionRecord(scenarioId,
                        "{\"threads\":1,\"rampUp\":0,\"duration\":0,\"loops\":1,\"jmeterProperties\":{},\"mode\":\"LOCAL\",\"controllerNodeId\":null,\"workerNodeIds\":[],\"monitorTargetIds\":[]}"));
        execution.markRunning("result.jtl", "jmeter.log");
        long executionId = executionRepository.save(execution).getId();

        mockMvc.perform(post("/api/executions/" + executionId + "/stop")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        assertThat(executionAuditRepository.findAll())
                .anySatisfy(audit -> {
                    assertThat(audit.getExecutionId()).isEqualTo(executionId);
                    assertThat(audit.getAction()).isEqualTo("STOP");
                    assertThat(audit.getPrincipalType()).isEqualTo("HUMAN");
                });

        // STOPPING 状态下重复停止是幂等放行
        mockMvc.perform(post("/api/executions/" + executionId + "/stop")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // 已结束执行再停止 → 409 EXECUTION_CONFLICT
        var record = executionRepository.findById(executionId).orElseThrow();
        record.markSuccess(0);
        executionRepository.save(record);

        mockMvc.perform(post("/api/executions/" + executionId + "/stop")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(result -> {
                    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
                    assertThat(body.get("code").asText()).isEqualTo("EXECUTION_CONFLICT");
                });
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
