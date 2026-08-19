package com.yr.perftest.platform.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.execution.TaskExecutionResult;
import com.yr.perftest.platform.execution.aggregate.AggregateReportService;
import com.yr.perftest.platform.execution.aggregate.AggregateSnapshotCodec;
import com.yr.perftest.platform.execution.aggregate.PersistentAggregateReportRecord;
import com.yr.perftest.platform.execution.aggregate.PersistentAggregateReportRepository;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agent-verification-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false",
        "platform.storage.root=build/tmp/t9-evidence-storage"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AgentVerificationApiTest {
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
    private PersistentAggregateReportRepository aggregateReportRepository;

    @Autowired
    private PersistentExecutionMetricSeriesRepository metricSeriesRepository;

    private String apiKey;
    private long scenarioId;
    private long otherScenarioId;

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
        scenarioId = scenarioRepository.save(
                new PersistentTaskScenarioRecord(plan.getId(), 1L, "scenario-a", 0)).getId();
        otherScenarioId = scenarioRepository.save(
                new PersistentTaskScenarioRecord(plan.getId(), 1L, "scenario-b", 1)).getId();
    }

    @Test
    void unapprovedCaptureExecutionIsRejected() throws Exception {
        long executionId = createFinishedExecution(scenarioId);
        long captureId = createCapture(executionId);

        mockMvc.perform(post("/api/agent/evidence-captures/" + captureId + "/execute")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("EXECUTION_CONFLICT")));

        mockMvc.perform(get("/api/agent/evidence-captures/" + captureId).header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PENDING_APPROVAL")))
                .andExpect(jsonPath("$.data.bundleRef", org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void machinePrincipalCannotApproveCapture() throws Exception {
        long executionId = createFinishedExecution(scenarioId);
        long captureId = createCapture(executionId);

        mockMvc.perform(post("/api/agent/evidence-captures/" + captureId + "/approve")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("ACCESS_DENIED")));
    }

    @Test
    void humanApprovedCaptureExecutesAndFlowsEvidenceBack() throws Exception {
        long executionId = createFinishedExecution(scenarioId);
        long bucket = executionRepository.findById(executionId).orElseThrow().getStartTime().toEpochMilli();
        metricSeriesRepository.save(new PersistentExecutionMetricSeriesRecord(
                executionId, bucket, "checkout", 10L, 0L, 50.0, 100L, 120L));
        metricSeriesRepository.save(new PersistentExecutionMetricSeriesRecord(
                executionId, bucket, "search", 10L, 1L, 45.0, 200L, 260L));
        long captureId = createCapture(executionId);

        String adminToken = loginToken();
        mockMvc.perform(post("/api/agent/evidence-captures/" + captureId + "/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("APPROVED")))
                .andExpect(jsonPath("$.data.approvedByName", is("admin")));

        MvcResult executed = mockMvc.perform(post("/api/agent/evidence-captures/" + captureId + "/execute")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("COMPLETED")))
                .andExpect(jsonPath("$.data.bundleRef", notNullValue()))
                .andExpect(jsonPath("$.data.sources.length()", is(4)))
                .andReturn();
        JsonNode body = objectMapper.readTree(executed.getResponse().getContentAsString());
        JsonNode series = null;
        for (JsonNode source : body.at("/data/sources")) {
            if ("series".equals(source.get("sourceType").asText())) {
                series = source;
            }
        }
        assertThat(series).isNotNull();
        assertThat(series.get("present").asBoolean()).isTrue();
        assertThat(series.get("count").asInt()).isEqualTo(1);

        String bundleRef = body.at("/data/bundleRef").asText();
        Path bundle = Path.of("build/tmp/t9-evidence-storage", bundleRef);
        assertThat(Files.isRegularFile(bundle)).isTrue();
    }

    @Test
    void capturePrecheckRejectsMissingPurposeAndCostNote() throws Exception {
        long executionId = createFinishedExecution(scenarioId);

        mockMvc.perform(post("/api/agent/evidence-captures")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"executionId\":" + executionId + ",\"purpose\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_FAILED")));

        mockMvc.perform(post("/api/agent/evidence-captures")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"executionId\":" + executionId + ",\"purpose\":\"diagnose\",\"impactLevel\":\"HIGH\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_FAILED")));
    }

    @Test
    void verificationReportsRegressedWhenP95Worsens() throws Exception {
        long baseline = createFinishedExecution(scenarioId);
        long candidate = createFinishedExecution(scenarioId);
        persistAggregateRows(baseline, List.of(
                row("checkout", 150, 200, 0.01, 50),
                row("search", 80, 100, 0, 100)));
        persistAggregateRows(candidate, List.of(
                row("checkout", 200, 300, 0.02, 45),
                row("search", 79, 98, 0, 102)));

        verify(baseline, candidate)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verdict", is("REGRESSED")));
    }

    @Test
    void verificationReportsImprovedWhenP95Improves() throws Exception {
        long baseline = createFinishedExecution(scenarioId);
        long candidate = createFinishedExecution(scenarioId);
        persistAggregateRows(baseline, List.of(
                row("checkout", 150, 200, 0.01, 50),
                row("search", 80, 100, 0, 100)));
        persistAggregateRows(candidate, List.of(
                row("checkout", 140, 140, 0.01, 60),
                row("search", 79, 90, 0, 110)));

        verify(baseline, candidate)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verdict", is("IMPROVED")));
    }

    @Test
    void verificationIsInconclusiveWhenMixedResults() throws Exception {
        long baseline = createFinishedExecution(scenarioId);
        long candidate = createFinishedExecution(scenarioId);
        persistAggregateRows(baseline, List.of(
                row("checkout", 150, 200, 0.01, 50),
                row("search", 80, 100, 0, 100)));
        persistAggregateRows(candidate, List.of(
                row("checkout", 200, 300, 0.02, 45),
                row("search", 79, 40, 0, 150)));

        verify(baseline, candidate)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verdict", is("INCONCLUSIVE")));
    }

    @Test
    void verificationIsInconclusiveWhenScenariosDiffer() throws Exception {
        long baseline = createFinishedExecution(scenarioId);
        long candidate = createFinishedExecution(otherScenarioId);
        persistAggregateRows(baseline, List.of(row("checkout", 150, 200, 0.01, 50)));
        persistAggregateRows(candidate, List.of(row("checkout", 140, 140, 0.01, 60)));

        verify(baseline, candidate)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verdict", is("INCONCLUSIVE")))
                .andExpect(jsonPath("$.data.reasons[0]", is("different scenario")));
    }

    @Test
    void guardrailErrorRateIncreaseMarksRegressed() throws Exception {
        long baseline = createFinishedExecution(scenarioId);
        long candidate = createFinishedExecution(scenarioId);
        persistAggregateRows(baseline, List.of(
                row("checkout", 150, 200, 0.01, 50),
                row("search", 80, 100, 0, 100)));
        persistAggregateRows(candidate, List.of(
                row("checkout", 150, 200, 0.03, 50),
                row("search", 80, 100, 0, 100)));

        verify(baseline, candidate)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verdict", is("REGRESSED")));
    }

    @Test
    void verificationRequiresRegisteredChangeRecord() throws Exception {
        long baseline = createFinishedExecution(scenarioId);
        long candidate = createFinishedExecution(scenarioId);

        mockMvc.perform(post("/api/agent/verifications")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baselineExecutionId\":" + baseline
                                + ",\"candidateExecutionId\":" + candidate
                                + ",\"changeRecordId\":999999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("NOT_FOUND")));
    }

    private long createCapture(long executionId) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/agent/evidence-captures")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"executionId\":" + executionId
                                + ",\"purpose\":\"diagnose latency spike\",\"impactLevel\":\"LOW\","
                                + "\"costNote\":\"bounded snapshot only\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PENDING_APPROVAL")))
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString())
                .at("/data/captureId").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions verify(long baseline, long candidate) throws Exception {
        MvcResult registered = mockMvc.perform(post("/api/agent/change-records")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"changeType\":\"CODE\",\"changeRef\":\"abc1234\",\"description\":\"optimize checkout\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long changeRecordId = objectMapper.readTree(registered.getResponse().getContentAsString())
                .at("/data/changeRecordId").asLong();

        return mockMvc.perform(post("/api/agent/verifications")
                .header("X-API-Key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"baselineExecutionId\":" + baseline
                        + ",\"candidateExecutionId\":" + candidate
                        + ",\"changeRecordId\":" + changeRecordId + "}"));
    }

    private long createFinishedExecution(long scenario) {
        PersistentScenarioExecutionRecord execution = new PersistentScenarioExecutionRecord(
                scenario,
                "{\"threads\":1,\"rampUp\":0,\"duration\":0,\"loops\":1,\"jmeterProperties\":{},\"mode\":\"DISTRIBUTED\",\"controllerNodeId\":1,\"workerNodeIds\":[1],\"monitorTargetIds\":[]}"
        );
        execution.markRunning("result.jtl", "jmeter.log");
        execution.markSuccess(0);
        return executionRepository.save(execution).getId();
    }

    private void persistAggregateRows(long executionId, List<TaskExecutionResult.AggregateRow> rows) throws Exception {
        TaskExecutionResult.Summary summary = new TaskExecutionResult.Summary(60, 10, 20, 30, 0, "final");
        aggregateReportRepository.save(new PersistentAggregateReportRecord(
                executionId,
                AggregateReportService.ACCURACY_FINAL,
                1_700_000_000_000L,
                1_700_000_001_000L,
                1.0,
                objectMapper.writeValueAsString(summary),
                objectMapper.writeValueAsString(rows),
                null,
                Instant.now(),
                AggregateSnapshotCodec.BUILDER_VERSION
        ));
    }

    private TaskExecutionResult.AggregateRow row(
            String label,
            long average,
            long p95,
            double errorRate,
            double throughput
    ) {
        return new TaskExecutionResult.AggregateRow(
                label,
                "thread-1",
                1000,
                average,
                average,
                p95,
                p95,
                p95,
                10,
                p95 + 50,
                errorRate,
                throughput
        );
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
