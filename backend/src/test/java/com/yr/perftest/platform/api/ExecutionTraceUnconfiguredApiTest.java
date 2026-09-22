package com.yr.perftest.platform.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRecord;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * trace endpoint 未配置（默认 application.yml）→ 列表与详情均 available=false / unconfigured。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:execution-trace-unconfigured-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.h2.console.enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ExecutionTraceUnconfiguredApiTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PersistentScenarioExecutionRepository executionRepository;

    @Test
    void unconfiguredEndpointReturnsUnconfigured() throws Exception {
        PersistentScenarioExecutionRecord execution = new PersistentScenarioExecutionRecord(1L, "{}");
        execution.markRunning("/tmp/result.jtl", "/tmp/jmeter.log");
        long executionId = executionRepository.save(execution).getId();
        String token = AuthTestSupport.loginToken(mockMvc, objectMapper);

        mockMvc.perform(get("/api/executions/" + executionId + "/traces")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available", is(false)))
                .andExpect(jsonPath("$.missingReason", is("unconfigured")))
                .andExpect(jsonPath("$.traces", hasSize(0)));

        mockMvc.perform(get("/api/executions/" + executionId + "/traces/t-1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available", is(false)))
                .andExpect(jsonPath("$.missingReason", is("unconfigured")));
    }

    @Test
    void missingExecutionRejected() throws Exception {
        String token = AuthTestSupport.loginToken(mockMvc, objectMapper);

        mockMvc.perform(get("/api/executions/999999/traces")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }
}
