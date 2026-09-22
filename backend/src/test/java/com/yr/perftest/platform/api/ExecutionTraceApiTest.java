package com.yr.perftest.platform.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.task.PersistentExecutionTraceSnapshotRecord;
import com.yr.perftest.platform.task.PersistentExecutionTraceSnapshotRepository;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRecord;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 执行详情链路面板 REST（Task 4）——OAP 配置但不可达 / 终态快照优先四态：
 * source-unavailable、快照回看（排序/过滤/分页）、retention-expired（仅详情）。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:execution-trace-api-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.h2.console.enabled=false",
        "platform.evidence.deep.kinds.trace.enabled=true",
        "platform.evidence.deep.kinds.trace.endpoint=http://127.0.0.1:1"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ExecutionTraceApiTest {
    /** 快照 JSON：5 条 brief，字段名与 SkyWalkingTraceModels.TraceBrief record 一致。 */
    private static final String SNAPSHOT_JSON = """
            [
              {"traceId":"t-1","service":"api-gateway","endpointName":"GET /a","startEpochMs":1000,"durationMs":900,"isError":false,"spanCount":3},
              {"traceId":"t-2","service":"api-gateway","endpointName":"GET /a","startEpochMs":2000,"durationMs":600,"isError":false,"spanCount":2},
              {"traceId":"t-3","service":"order-service","endpointName":"GET /b","startEpochMs":3000,"durationMs":300,"isError":true,"spanCount":4},
              {"traceId":"t-4","service":"api-gateway","endpointName":"POST /c","startEpochMs":4000,"durationMs":100,"isError":false,"spanCount":1},
              {"traceId":"t-5","service":"order-service","endpointName":"GET /b","startEpochMs":5000,"durationMs":50,"isError":false,"spanCount":2}
            ]
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PersistentScenarioExecutionRepository executionRepository;

    @Autowired
    private PersistentExecutionTraceSnapshotRepository snapshotRepository;

    @Test
    void unreachableOapReturnsAvailableFalseWithSourceUnavailable() throws Exception {
        long executionId = execution("RUNNING");

        mockMvc.perform(get("/api/executions/" + executionId + "/traces")
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available", is(false)))
                .andExpect(jsonPath("$.missingReason", is("source-unavailable")))
                .andExpect(jsonPath("$.traces", hasSize(0)));

        mockMvc.perform(get("/api/executions/" + executionId + "/traces/t-1")
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available", is(false)))
                .andExpect(jsonPath("$.missingReason", is("source-unavailable")))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void snapshotServesTerminalExecutionWithoutOap() throws Exception {
        long executionId = execution("SUCCESS");
        snapshotRepository.save(new PersistentExecutionTraceSnapshotRecord(
                executionId, SNAPSHOT_JSON, 5, Instant.now()));

        // sort=duration：耗时降序，首条=最慢
        mockMvc.perform(get("/api/executions/" + executionId + "/traces")
                        .queryParam("sort", "duration")
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available", is(true)))
                .andExpect(jsonPath("$.missingReason", nullValue()))
                .andExpect(jsonPath("$.traces", hasSize(5)))
                .andExpect(jsonPath("$.traces[0].traceId", is("t-1")))
                .andExpect(jsonPath("$.traces[0].durationMs", is(900)))
                .andExpect(jsonPath("$.traces[0].time", is(1000)))
                .andExpect(jsonPath("$.traces[0].service", is("api-gateway")))
                .andExpect(jsonPath("$.traces[0].entry", is("GET /a")))
                .andExpect(jsonPath("$.traces[0].error", is(false)))
                .andExpect(jsonPath("$.traces[0].spanCount", is(3)))
                .andExpect(jsonPath("$.total", is(5)))
                .andExpect(jsonPath("$.page", is(1)))
                .andExpect(jsonPath("$.size", is(20)));

        // 不传 sort：默认 duration 降序（首条仍为最慢）
        mockMvc.perform(get("/api/executions/" + executionId + "/traces")
                        .header("Authorization", "Bearer " + token()))
                .andExpect(jsonPath("$.traces[0].traceId", is("t-1")));

        // sort=time：时间降序（startEpochMs 最新在前）
        mockMvc.perform(get("/api/executions/" + executionId + "/traces")
                        .queryParam("sort", "time")
                        .header("Authorization", "Bearer " + token()))
                .andExpect(jsonPath("$.traces[0].traceId", is("t-5")));

        // 分页第 2 页（size=2 → 第 2 页为 t-3/t-4）
        mockMvc.perform(get("/api/executions/" + executionId + "/traces")
                        .queryParam("sort", "duration")
                        .queryParam("page", "2")
                        .queryParam("size", "2")
                        .header("Authorization", "Bearer " + token()))
                .andExpect(jsonPath("$.traces", hasSize(2)))
                .andExpect(jsonPath("$.traces[0].traceId", is("t-3")))
                .andExpect(jsonPath("$.traces[1].traceId", is("t-4")))
                .andExpect(jsonPath("$.total", is(5)))
                .andExpect(jsonPath("$.page", is(2)))
                .andExpect(jsonPath("$.size", is(2)));

        // service 过滤
        mockMvc.perform(get("/api/executions/" + executionId + "/traces")
                        .queryParam("service", "api-gateway")
                        .header("Authorization", "Bearer " + token()))
                .andExpect(jsonPath("$.traces", hasSize(3)))
                .andExpect(jsonPath("$.total", is(3)));

        // onlyError / minDurationMs 过滤
        mockMvc.perform(get("/api/executions/" + executionId + "/traces")
                        .queryParam("onlyError", "true")
                        .header("Authorization", "Bearer " + token()))
                .andExpect(jsonPath("$.traces", hasSize(1)))
                .andExpect(jsonPath("$.traces[0].traceId", is("t-3")));

        mockMvc.perform(get("/api/executions/" + executionId + "/traces")
                        .queryParam("minDurationMs", "200")
                        .header("Authorization", "Bearer " + token()))
                .andExpect(jsonPath("$.traces", hasSize(3)));

        // 快照存在但 OAP span 详情不可达 → 详情 retention-expired
        mockMvc.perform(get("/api/executions/" + executionId + "/traces/t-1")
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available", is(false)))
                .andExpect(jsonPath("$.missingReason", is("retention-expired")))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void emptySnapshotStillServesFromSnapshotWithoutOapFallback() throws Exception {
        long executionId = execution("FAILED");
        snapshotRepository.save(new PersistentExecutionTraceSnapshotRecord(
                executionId, "[]", 0, Instant.now()));

        mockMvc.perform(get("/api/executions/" + executionId + "/traces")
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available", is(true)))
                .andExpect(jsonPath("$.missingReason", nullValue()))
                .andExpect(jsonPath("$.traces", hasSize(0)))
                .andExpect(jsonPath("$.total", is(0)));
    }

    @Test
    void pageSizeClampedTo100() throws Exception {
        long executionId = execution("SUCCESS");
        snapshotRepository.save(new PersistentExecutionTraceSnapshotRecord(
                executionId, SNAPSHOT_JSON, 5, Instant.now()));

        mockMvc.perform(get("/api/executions/" + executionId + "/traces")
                        .queryParam("size", "500")
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size", is(100)))
                .andExpect(jsonPath("$.traces", hasSize(5)));

        mockMvc.perform(get("/api/executions/" + executionId + "/traces")
                        .queryParam("size", "0")
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size", is(1)));
    }

    private String token() throws Exception {
        return AuthTestSupport.loginToken(mockMvc, objectMapper);
    }

    private long execution(String status) {
        PersistentScenarioExecutionRecord execution = new PersistentScenarioExecutionRecord(1L, "{}");
        execution.markRunning("/tmp/result.jtl", "/tmp/jmeter.log");
        switch (status) {
            case "SUCCESS" -> execution.markSuccess(0);
            case "FAILED" -> execution.markFailed(1, "boom");
            default -> {
            }
        }
        return executionRepository.save(execution).getId();
    }
}
