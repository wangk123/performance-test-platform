package com.yr.perftest.platform.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:report-enhancement-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false",
        "platform.storage.root=build/tmp/report-enhancement-storage"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ReportEnhancementApiTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PersistentTaskPlanRepository planRepository;

    private String adminToken;
    private long basePlanId;
    private long targetPlanId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = loginToken();
        basePlanId = planRepository.save(new PersistentTaskPlanRecord(1L, "base-plan", null, "admin")).getId();
        targetPlanId = planRepository.save(new PersistentTaskPlanRecord(1L, "target-plan", null, "admin")).getId();
    }

    @Test
    void compareEndpointPersistsAndRetrievesComparison() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/reports/compare")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-User", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"basePlanId\":" + basePlanId + ",\"targetPlanId\":" + targetPlanId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.compareId", notNullValue()))
                .andExpect(jsonPath("$.basePlanId", is((int) basePlanId)))
                .andExpect(jsonPath("$.targetPlanId", is((int) targetPlanId)))
                .andExpect(jsonPath("$.overall.baseSamples", is(0)))
                .andReturn();
        long compareId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("compareId").asLong();

        mockMvc.perform(get("/api/reports/compare/" + compareId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compareId", is((int) compareId)))
                .andExpect(jsonPath("$.rows").isArray());
    }

    @Test
    void compareMissingPlanReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/reports/compare")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-User", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"basePlanId\":999999,\"targetPlanId\":" + targetPlanId + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("EXECUTION_VALIDATION_FAILED")));
    }

    @Test
    void pdfExportProducesPdfBytes() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/reports/plans/" + basePlanId + "/export/pdf")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(body.length).isGreaterThan(100);
        assertThat(new String(body, 0, 5)).isEqualTo("%PDF-");
        assertThat(result.getResponse().getContentType()).isEqualTo("application/pdf");
    }

    @Test
    void pdfExportMissingPlanReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/reports/plans/999999/export/pdf")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
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
