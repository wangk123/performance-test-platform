package com.yr.perftest.platform.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.plandoc.PlanPhase;
import com.yr.perftest.platform.task.plandoc.PlanStatus;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-api-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PlanDocumentApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PersistentTaskPlanRepository planRepository;

    private String token;
    private long planId;

    @BeforeEach
    void setUp() throws Exception {
        token = AuthTestSupport.loginToken(mockMvc, objectMapper);
        PersistentTaskPlanRecord plan = planRepository.save(new PersistentTaskPlanRecord(1L, "计划A", null, "admin"));
        plan.initializeBody("## 一、背景\n\n内容\n");
        planId = planRepository.save(plan).getId();
    }

    private MvcResult transition(String action, String body) throws Exception {
        return mockMvc.perform(post("/api/task-plans/" + planId + "/" + action)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body == null ? "{}" : body))
                .andReturn();
    }

    @Test
    void getPlanReturnsPermissionsAlongside() throws Exception {
        mockMvc.perform(get("/api/task-plans/" + planId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.name").value("计划A"))
                .andExpect(jsonPath("$.plan.phase").value("DRAFT"))
                .andExpect(jsonPath("$.permissions.SUBMIT").value(true))
                .andExpect(jsonPath("$.permissions.PUBLISH").value(false));
    }

    @Test
    void fullTransitionChainOverRest() throws Exception {
        transition("submit", "{\"comment\":\"请评审\"}").getResponse().getStatus();
        transition("start-review", null);
        transition("approve", null);
        transition("start-execution", null);
        mockMvc.perform(get("/api/task-plans/" + planId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.plan.phase").value("EXECUTION"))
                .andExpect(jsonPath("$.plan.status").value("PENDING"));
    }

    @Test
    void illegalTransitionReturns409WithAllowedActions() throws Exception {
        MvcResult result = transition("approve", null);
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("code").asText()).isEqualTo("PLAN_STATE");
        assertThat(body.get("allowedActions").toString()).contains("SUBMIT");
    }

    @Test
    void documentConflictReturns409WithServerMarkdown() throws Exception {
        mockMvc.perform(put("/api/task-plans/" + planId + "/document")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseRevision\":1,\"markdown\":\"## 一、背景\\n\\n甲版\\n\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(2));
        MvcResult conflict = mockMvc.perform(put("/api/task-plans/" + planId + "/document")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseRevision\":1,\"markdown\":\"## 一、背景\\n\\n乙版\\n\"}"))
                .andReturn();
        assertThat(conflict.getResponse().getStatus()).isEqualTo(409);
        JsonNode body = objectMapper.readTree(conflict.getResponse().getContentAsString());
        assertThat(body.get("code").asText()).isEqualTo("PLAN_REVISION_CONFLICT");
        assertThat(body.get("currentRevision").asInt()).isEqualTo(2);
        assertThat(body.get("serverMarkdown").asText()).contains("甲版");
    }

    @Test
    void rejectRequiresCommentOverRest() throws Exception {
        transition("submit", null);
        transition("start-review", null);
        assertThat(transition("reject", "{\"comment\":\"\"}").getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void publishRequiresConclusionOverRest() throws Exception {
        PersistentTaskPlanRecord plan = planRepository.findById(planId).orElseThrow();
        plan.forceState(PlanPhase.REPORT, PlanStatus.DONE);
        planRepository.save(plan);
        assertThat(transition("publish", "{\"conclusion\":\" \"}").getResponse().getStatus()).isEqualTo(400);
        transition("publish", "{\"conclusion\":\"达成，可发布\"}");
        mockMvc.perform(get("/api/task-plans/" + planId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.plan.phase").value("PUBLISH"))
                .andExpect(jsonPath("$.plan.body").value(org.hamcrest.Matchers.containsString("**总体结论**：达成，可发布")));
    }

    @Test
    void precheckEndpointsRoundTrip() throws Exception {
        mockMvc.perform(put("/api/task-plans/" + planId + "/precheck-settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"items\":[\"指标已定义\",\"场景已配置\"]}"))
                .andExpect(status().isOk());
        MvcResult run = mockMvc.perform(post("/api/task-plans/" + planId + "/precheck-run")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        JsonNode report = objectMapper.readTree(run.getResponse().getContentAsString());
        assertThat(report.get("failures").size()).isGreaterThan(0); // 无场景 → 场景已配置 未过
    }

    @Test
    void commentsRoundTripAndShareNotFound() throws Exception {
        mockMvc.perform(post("/api/task-plans/" + planId + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"补充口径\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/task-plans/" + planId + "/comments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[0].content").value("补充口径"));
        mockMvc.perform(get("/api/share/plans/not-a-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonMemberPlanReadsRejectedOverRest() throws Exception {
        String outsider = AuthTestSupport.loginToken(mockMvc, objectMapper, "tester", "tester123");
        mockMvc.perform(get("/api/task-plans/" + planId).header("Authorization", "Bearer " + outsider))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLAN_ACCESS_DENIED"));
        mockMvc.perform(get("/api/task-plans/" + planId + "/comments").header("Authorization", "Bearer " + outsider))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/task-plans/" + planId + "/report").header("Authorization", "Bearer " + outsider))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/projects/1/plan-templates").header("Authorization", "Bearer " + outsider))
                .andExpect(status().isForbidden());
        // 成员读门禁不影响管理员既有路径
        mockMvc.perform(get("/api/task-plans/" + planId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousPlanReadRejectedOverRest() throws Exception {
        mockMvc.perform(get("/api/task-plans/" + planId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateDefaultConfigGuardsBlankNameAndNonOwner() throws Exception {
        String outsider = AuthTestSupport.loginToken(mockMvc, objectMapper, "tester", "tester123");
        mockMvc.perform(put("/api/task-plans/" + planId)
                        .header("Authorization", "Bearer " + outsider)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"改名\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLAN_ACCESS_DENIED"));
        mockMvc.perform(put("/api/task-plans/" + planId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/task-plans/" + planId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"改名后\",\"remark\":\"r\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("改名后"));
    }
}
