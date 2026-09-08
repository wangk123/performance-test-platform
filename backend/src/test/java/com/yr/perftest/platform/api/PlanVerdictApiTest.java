package com.yr.perftest.platform.api;

import com.yr.perftest.platform.identity.AuthTokenService;
import com.yr.perftest.platform.project.PersistentProjectMemberRecord;
import com.yr.perftest.platform.project.PersistentProjectMemberRepository;
import com.yr.perftest.platform.project.PersistentProjectRecord;
import com.yr.perftest.platform.project.PersistentProjectRepository;
import com.yr.perftest.platform.project.ProjectRole;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.plandoc.PlanPhase;
import com.yr.perftest.platform.task.plandoc.PlanStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-verdict-api-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PlanVerdictApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthTokenService authTokenService;
    @Autowired
    private PersistentProjectRepository projectRepository;
    @Autowired
    private PersistentProjectMemberRepository memberRepository;
    @Autowired
    private PersistentTaskPlanRepository planRepository;

    private long planId;
    private String token;

    @BeforeEach
    void setUp() {
        PersistentProjectRecord project = projectRepository.save(new PersistentProjectRecord("P1", "项目一", "", "admin"));
        memberRepository.save(new PersistentProjectMemberRecord(project.getId(), "admin", ProjectRole.OWNER));
        PersistentTaskPlanRecord plan = planRepository.save(
                new PersistentTaskPlanRecord(project.getId(), "计划", null, "admin"));
        plan.updateBody("""
                ## 二、测试目的与指标

                | 对象 | 指标 | 目标值 | 口径 |
                |---|---|---|---|
                | 登录场景 | TPS | ≥ 500 | 口径 |
                """);
        plan.forceState(PlanPhase.REPORT, PlanStatus.DONE);
        planId = planRepository.save(plan).getId();
        token = authTokenService.issue("admin");
    }

    @Test
    void verdictReadableByProjectMemberWhenReportDone() throws Exception {
        mockMvc.perform(get("/api/task-plans/{id}/verdict", planId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.present").value(true))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.overall").value("INDETERMINATE")) // 场景无执行
                .andExpect(jsonPath("$.rows[0].objectName").value("登录场景"))
                .andExpect(jsonPath("$.rows[0].status").value("INDETERMINATE"))
                .andExpect(jsonPath("$.rows[0].metricType").value("TPS"));
    }

    @Test
    void verdictRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/task-plans/{id}/verdict", planId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verdictDeniedForNonMember() throws Exception {
        // AuthTokenService.issue 对未知用户直接抛错；改用种子账号 tester（已注册但非 P1 项目成员）验证非成员 403
        String outsider = authTokenService.issue("tester");
        mockMvc.perform(get("/api/task-plans/{id}/verdict", planId)
                        .header("Authorization", "Bearer " + outsider))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLAN_ACCESS_DENIED"));
    }
}
