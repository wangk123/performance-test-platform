package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.identity.SystemRole;
import com.yr.perftest.platform.project.PersistentProjectMemberRecord;
import com.yr.perftest.platform.project.PersistentProjectMemberRepository;
import com.yr.perftest.platform.project.PersistentProjectRecord;
import com.yr.perftest.platform.project.PersistentProjectRepository;
import com.yr.perftest.platform.project.ProjectRole;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-share-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PlanShareTest {

    private static final HumanPrincipal OWNER = new HumanPrincipal("owner", Set.of(SystemRole.PROJECT_MEMBER));
    private static final HumanPrincipal MEMBER = new HumanPrincipal("member-b", Set.of(SystemRole.PROJECT_MEMBER));

    @Autowired
    private PlanWorkflowService workflow;
    @Autowired
    private PersistentTaskPlanRepository planRepository;
    @Autowired
    private PersistentProjectRepository projectRepository;
    @Autowired
    private PersistentProjectMemberRepository memberRepository;
    @Autowired
    private PersistentPlanShareTokenRepository shareTokenRepository;

    private long planId;

    @BeforeEach
    void setUp() {
        PersistentProjectRecord project = projectRepository.save(new PersistentProjectRecord("P1", "项目一", "", "owner"));
        memberRepository.save(new PersistentProjectMemberRecord(project.getId(), "owner", ProjectRole.OWNER));
        memberRepository.save(new PersistentProjectMemberRecord(project.getId(), "member-b", ProjectRole.MEMBER));
        PersistentTaskPlanRecord plan = planRepository.save(
                new PersistentTaskPlanRecord(project.getId(), "已发布计划", null, "owner"));
        plan.updateBody("## 一、背景\n\n结论内容\n");
        plan.applyPublish(Instant.now());
        planId = planRepository.save(plan).getId();
    }

    @Test
    void shareCreationRestrictedToPublishedPlanAndOwner() {
        assertThatThrownBy(() -> workflow.createShare(planId, MEMBER, null))
                .isInstanceOf(PlanAccessDeniedException.class);
        PlanWorkflowService.ShareView share = workflow.createShare(planId, OWNER, 7);
        assertThat(share.token()).hasSize(36);
        PlanWorkflowService.SharedPlanView view = workflow.getSharedPlan(share.token());
        assertThat(view.name()).isEqualTo("已发布计划");
        assertThat(view.body()).contains("结论内容");
    }

    @Test
    void unpublishedPlanCannotShare() {
        PersistentTaskPlanRecord plan = planRepository.findById(planId).orElseThrow();
        plan.forceState(PlanPhase.REPORT, PlanStatus.DONE);
        planRepository.save(plan);
        assertThatThrownBy(() -> workflow.createShare(planId, OWNER, null))
                .isInstanceOf(PlanStateException.class);
    }

    @Test
    void revokedAndExpiredTokensReturnShareNotFound() {
        PlanWorkflowService.ShareView share = workflow.createShare(planId, OWNER, null);
        workflow.revokeShare(planId, share.id(), OWNER);
        assertThatThrownBy(() -> workflow.getSharedPlan(share.token()))
                .hasMessageContaining("SHARE_NOT_FOUND");

        PlanWorkflowService.ShareView expiring = workflow.createShare(planId, OWNER, 1);
        PersistentPlanShareTokenRecord raw = shareTokenRepository.findByToken(expiring.token()).orElseThrow();
        raw.expireForTest(Instant.now().minusSeconds(60)); // 过期由 expiresAt 判定；把时间改到过去
        shareTokenRepository.save(raw);
        assertThatThrownBy(() -> workflow.getSharedPlan(expiring.token()))
                .hasMessageContaining("SHARE_NOT_FOUND");
    }
}
