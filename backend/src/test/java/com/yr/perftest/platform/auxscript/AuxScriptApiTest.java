package com.yr.perftest.platform.auxscript;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.execution.distributed.ExecutionNodeRole;
import com.yr.perftest.platform.execution.distributed.PersistentExecutionNodeRecord;
import com.yr.perftest.platform.execution.distributed.PersistentExecutionNodeRepository;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:aux-script-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false",
        "platform.storage.root=build/tmp/aux-script-storage",
        "platform.auxscript.timeout-seconds=2"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AuxScriptApiTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuxScriptBindingService bindingService;

    @Autowired
    private AuxScriptExecutor executor;

    @Autowired
    private AuxScriptService auxScriptService;

    @Autowired
    private PersistentTaskPlanRepository planRepository;

    @Autowired
    private PersistentTaskScenarioRepository scenarioRepository;

    @Autowired
    private PersistentScenarioExecutionRepository executionRepository;

    @Autowired
    private PersistentExecutionNodeRepository nodeRepository;

    private String apiKey;
    private String adminToken;
    private long scenarioId;

    @BeforeEach
    void setUp() throws Exception {
        String adminToken = loginToken();
        this.adminToken = adminToken;
        MvcResult issued = mockMvc.perform(post("/api/agent-api-keys")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":\"ops\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        apiKey = objectMapper.readTree(issued.getResponse().getContentAsString()).get("plainKey").asText();
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
    void scriptCrudAndVersioning() throws Exception {
        mockMvc.perform(post("/api/projects/1/aux-scripts")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-User", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"prepare-env\",\"type\":\"SHELL\",\"scope\":\"PROJECT\",\"description\":\"prepare\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scriptId").isNumber())
                .andExpect(jsonPath("$.versions", hasSize(0)));

        MvcResult version = mockMvc.perform(post("/api/aux-scripts/1/versions")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-User", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceCode\":\"#!/bin/sh\\necho prepared\",\"remark\":\"v1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNo", is(1)))
                .andReturn();
        long versionId = objectMapper.readTree(version.getResponse().getContentAsString()).get("versionId").asLong();
        assertThat(versionId).isPositive();

        mockMvc.perform(get("/api/aux-scripts/1").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versions", hasSize(1)))
                .andExpect(jsonPath("$.versions[0].versionNo", is(1)));
    }

    @Test
    void preScriptsRunWhenExecutionStarts() throws Exception {
        long versionId = createScriptVersion("#!/bin/sh\necho prep-ok");
        bind(List.of(new AuxScriptBindingService.BindingInput(
                AuxScriptPhase.PRE, versionId, AuxScriptFailurePolicy.STOP_TASK, 0)));

        MvcResult started = mockMvc.perform(post("/api/agent/scenarios/" + scenarioId + "/executions")
                        .header("X-API-Key", apiKey)
                        .header("Idempotency-Key", "aux-pre-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"executionName\":\"aux-run\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long executionId = objectMapper.readTree(started.getResponse().getContentAsString())
                .at("/data/executionId").asLong();

        mockMvc.perform(get("/api/executions/" + executionId + "/aux-script-executions")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].phase", is("PRE")))
                .andExpect(jsonPath("$[0].status", is("SUCCESS")))
                .andExpect(jsonPath("$[0].exitCode", is(0)));
    }

    @Test
    void postScriptsRunAfterExecutionFinished() throws Exception {
        long versionId = createScriptVersion("#!/bin/sh\necho cleanup-ok");
        bind(List.of(new AuxScriptBindingService.BindingInput(
                AuxScriptPhase.POST, versionId, AuxScriptFailurePolicy.CONTINUE, 0)));

        long executionId = createExecution();
        mockMvc.perform(post("/api/agent/executions/" + executionId + "/cancel")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CANCELLED")));

        // 取消路径在提交后同步触发 POST 脚本
        List<AuxScriptExecutor.ExecutionView> postRecords = executor.listExecutions(executionId).stream()
                .filter(view -> "POST".equals(view.phase()))
                .toList();
        assertThat(postRecords).hasSize(1);
        assertThat(postRecords.get(0).status()).isEqualTo("SUCCESS");
    }

    @Test
    void stopTaskPolicySkipsRemainingScripts() {
        long failVersion = createScriptVersion("#!/bin/sh\nexit 1");
        long okVersion = createScriptVersion("#!/bin/sh\necho should-not-run");
        long executionId = createExecution();
        bind(List.of(
                new AuxScriptBindingService.BindingInput(AuxScriptPhase.PRE, failVersion, AuxScriptFailurePolicy.STOP_TASK, 0),
                new AuxScriptBindingService.BindingInput(AuxScriptPhase.PRE, okVersion, AuxScriptFailurePolicy.CONTINUE, 1)
        ));

        List<AuxScriptExecutor.ExecutionView> views = executor.runPhase(executionId, AuxScriptPhase.PRE);

        assertThat(views).extracting(AuxScriptExecutor.ExecutionView::status)
                .containsExactly("FAILED", "SKIPPED");
    }

    @Test
    void continuePolicyRunsRemainingScripts() {
        long failVersion = createScriptVersion("#!/bin/sh\nexit 1");
        long okVersion = createScriptVersion("#!/bin/sh\necho still-runs");
        long executionId = createExecution();
        bind(List.of(
                new AuxScriptBindingService.BindingInput(AuxScriptPhase.PRE, failVersion, AuxScriptFailurePolicy.CONTINUE, 0),
                new AuxScriptBindingService.BindingInput(AuxScriptPhase.PRE, okVersion, AuxScriptFailurePolicy.CONTINUE, 1)
        ));

        List<AuxScriptExecutor.ExecutionView> views = executor.runPhase(executionId, AuxScriptPhase.PRE);

        assertThat(views).extracting(AuxScriptExecutor.ExecutionView::status)
                .containsExactly("FAILED", "SUCCESS");
    }

    @Test
    void manualConfirmPolicyHaltsUntilConfirmed() {
        long failVersion = createScriptVersion("#!/bin/sh\nexit 1");
        long okVersion = createScriptVersion("#!/bin/sh\necho runs-after-confirm");
        long executionId = createExecution();
        bind(List.of(
                new AuxScriptBindingService.BindingInput(AuxScriptPhase.PRE, failVersion, AuxScriptFailurePolicy.MANUAL_CONFIRM, 0),
                new AuxScriptBindingService.BindingInput(AuxScriptPhase.PRE, okVersion, AuxScriptFailurePolicy.CONTINUE, 1)
        ));

        List<AuxScriptExecutor.ExecutionView> first = executor.runPhase(executionId, AuxScriptPhase.PRE);
        assertThat(first).extracting(AuxScriptExecutor.ExecutionView::status)
                .containsExactly("AWAITING_CONFIRMATION", "SKIPPED");

        List<AuxScriptExecutor.ExecutionView> resumed = executor.confirmExecution(executionId);
        assertThat(resumed).extracting(AuxScriptExecutor.ExecutionView::status)
                .containsExactly("SUCCESS");
    }

    @Test
    void longRunningScriptTimesOut() {
        long slowVersion = createScriptVersion("#!/bin/sh\nsleep 5");
        long executionId = createExecution();
        bind(List.of(new AuxScriptBindingService.BindingInput(
                AuxScriptPhase.PRE, slowVersion, AuxScriptFailurePolicy.CONTINUE, 0)));

        List<AuxScriptExecutor.ExecutionView> views = executor.runPhase(executionId, AuxScriptPhase.PRE);

        assertThat(views).extracting(AuxScriptExecutor.ExecutionView::status)
                .containsExactly("TIMEOUT");
    }

    private long createScriptVersion(String sourceCode) {
        AuxScriptService.AuxScriptView script = auxScriptService.createScript(
                1L,
                "script-" + System.nanoTime(),
                AuxScriptType.SHELL,
                AuxScriptScope.PROJECT,
                null,
                "admin"
        );
        return auxScriptService.addVersion(script.scriptId(), sourceCode, null, "admin").versionId();
    }

    private void bind(List<AuxScriptBindingService.BindingInput> bindings) {
        bindingService.replaceBindings(scenarioId, bindings, "admin");
    }

    private long createExecution() {
        return executionRepository.save(new PersistentScenarioExecutionRecord(scenarioId, "{}")).getId();
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
