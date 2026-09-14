package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.execution.ExecutionStatus;
import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.identity.SystemRole;
import com.yr.perftest.platform.project.PersistentProjectMemberRecord;
import com.yr.perftest.platform.project.PersistentProjectRecord;
import com.yr.perftest.platform.project.PersistentProjectRepository;
import com.yr.perftest.platform.project.PersistentProjectMemberRepository;
import com.yr.perftest.platform.project.ProjectRole;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRepository;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.TaskPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-doc-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.h2.console.enabled=false"
})
@Transactional
class PlanDocumentServiceTest {

    private static final HumanPrincipal OWNER = new HumanPrincipal("owner", java.util.Set.of(SystemRole.PROJECT_MEMBER));
    private static final HumanPrincipal OTHER_MEMBER = new HumanPrincipal("member-b", java.util.Set.of(SystemRole.PROJECT_MEMBER));
    private static final HumanPrincipal OUTSIDER = new HumanPrincipal("outsider", java.util.Set.of(SystemRole.PROJECT_MEMBER));

    @Autowired
    private PlanDocumentService documentService;
    @Autowired
    private PersistentTaskPlanRepository planRepository;
    @Autowired
    private PersistentProjectRepository projectRepository;
    @Autowired
    private PersistentProjectMemberRepository memberRepository;
    @Autowired
    private PersistentScenarioExecutionRepository executionRepository;

    private long planId;

    @BeforeEach
    void setUp() {
        PersistentProjectRecord project = projectRepository.save(
                new PersistentProjectRecord("P1", "项目一", "", "owner"));
        memberRepository.save(new PersistentProjectMemberRecord(project.getId(), "owner", ProjectRole.OWNER));
        memberRepository.save(new PersistentProjectMemberRecord(project.getId(), "member-b", ProjectRole.MEMBER));
        PersistentTaskPlanRecord plan = planRepository.save(
                new PersistentTaskPlanRecord(project.getId(), "计划一", null, "owner"));
        plan.updateBody("## 一、背景\n\n初始内容\n");
        planId = planRepository.save(plan).getId();
    }

    @Test
    void updateMarkdownWithFreshBaseSucceedsAndBumpsRevision() {
        TaskPlan updated = documentService.updateMarkdown(planId, 2, "## 一、背景\n\n新内容\n", OWNER);
        assertThat(updated.revision()).isEqualTo(3);
        assertThat(updated.body()).contains("新内容");
    }

    @Test
    void staleBaseThrowsConflictWithServerMarkdown() {
        try {
            documentService.updateMarkdown(planId, 1, "## 一、背景\n\n旧基线\n", OWNER);
            throw new AssertionError("expected PlanRevisionConflictException");
        } catch (PlanRevisionConflictException conflict) {
            assertThat(conflict.getCurrentRevision()).isEqualTo(2);
            assertThat(conflict.getServerMarkdown()).contains("初始内容");
        }
    }

    @Test
    void editAllowedOutsidePlanning() {
        // spec §4.4：EDIT 任意状态放开（含已发布），revision 冲突保护兜底
        PersistentTaskPlanRecord plan = planRepository.findById(planId).orElseThrow();
        plan.forceState(PlanStatus.IN_REVIEW);
        planRepository.save(plan);
        TaskPlan edited = documentService.updateMarkdown(planId, 2, "x", OWNER);
        assertThat(edited.revision()).isEqualTo(3);
    }

    @Test
    void memberCanEditButOutsiderCannot() {
        // 无角色维度（spec §10）：门槛=登录且为项目成员
        TaskPlan edited = documentService.updateMarkdown(planId, 2, "x", OTHER_MEMBER);
        assertThat(edited.revision()).isEqualTo(3);
        assertThatThrownBy(() -> documentService.updateMarkdown(planId, 2, "x", OUTSIDER))
                .isInstanceOf(PlanAccessDeniedException.class);
    }

    @Test
    void backfillIsSystemWriteNoBaseRevisionAndIdempotent() {
        documentService.backfillExecutionRecord(planId, "场景A", 1001L, "- 2026-09-04 10:00 · 50 并发 · SUCCESS · 吞吐 100 TPS");
        documentService.backfillExecutionRecord(planId, "场景A", 1001L, "- 重复");
        TaskPlan plan = documentService.getDocument(planId);
        assertThat(plan.body()).contains("<!-- backfill:execution:1001 -->");
        assertThat(plan.body()).contains("吞吐 100 TPS");
        assertThat(plan.body()).doesNotContain("- 重复");
        assertThat(plan.revision()).isEqualTo(3); // 初建 +1，第二次幂等不 bump
    }
}
