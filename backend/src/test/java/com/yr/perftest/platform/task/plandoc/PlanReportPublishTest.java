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
import com.yr.perftest.platform.task.TaskPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-publish-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PlanReportPublishTest {

    private static final HumanPrincipal OWNER = new HumanPrincipal("owner", Set.of(SystemRole.PROJECT_MEMBER));

    @Autowired
    private PlanWorkflowService workflow;
    @Autowired
    private PersistentTaskPlanRepository planRepository;
    @Autowired
    private PersistentProjectRepository projectRepository;
    @Autowired
    private PersistentProjectMemberRepository memberRepository;
    @Autowired
    private PersistentPlanPublishSnapshotRepository snapshotRepository;
    @Autowired
    private PersistentPlanVersionRepository versionRepository;
    @Autowired
    private PlanDocumentService documentService;

    private long planId;

    @BeforeEach
    void setUp() {
        PersistentProjectRecord project = projectRepository.save(new PersistentProjectRecord("P1", "项目一", "", "owner"));
        memberRepository.save(new PersistentProjectMemberRecord(project.getId(), "owner", ProjectRole.OWNER));
        PersistentTaskPlanRecord plan = planRepository.save(
                new PersistentTaskPlanRecord(project.getId(), "计划", null, "owner"));
        plan.updateBody("""
                ## 八、场景设计

                ### S1 登录 · SINGLE_TXN

                **场景目的**：p

                #### 执行记录

                ## 十二、结论

                ### 指标达成表

                | 指标 | 目标 | 实际结果 | 状态 |
                |---|---|---|---|
                | 登录 TPS | ≥ 200 | 待执行 | 待判定 |

                **总体结论**：（发布时填写）
                """);
        plan.forceState(PlanPhase.EXECUTION, PlanStatus.DONE); // 新链路：执行全部完成即可发布（报告阶段已取消）
        planId = planRepository.save(plan).getId();
    }

    @Test
    void publishBackfillsOverviewBeforeFreeze() {
        TaskPlan plan = workflow.publish(planId, OWNER, "结论", "V1.0");
        assertThat(plan.status()).isEqualTo(PlanStatus.PUBLISHED);
        String body = planRepository.findById(planId).orElseThrow().getBody();
        assertThat(body).contains("<!-- backfill:report -->");
        assertThat(body).contains("#### 执行结果总览");
        assertThat(body).contains("| 登录 TPS | ≥ 200 | 待执行 | 待判定 |"); // 无执行时实际列不被改写
        assertThat(body).contains("**总体结论**：结论");
    }

    @Test
    void publishRequiresConclusionAndWritesItAndSnapshot() {
        assertThatThrownBy(() -> workflow.publish(planId, OWNER, " ", "V1.0"))
                .isInstanceOf(PlanValidationException.class);
        TaskPlan published = workflow.publish(planId, OWNER, "核心指标全部达成，可上线。", "V1.0");
        assertThat(published.phase()).isEqualTo(PlanPhase.PUBLISH);
        assertThat(published.status()).isEqualTo(PlanStatus.PUBLISHED);
        String body = planRepository.findById(planId).orElseThrow().getBody();
        assertThat(body).contains("**总体结论**：核心指标全部达成，可上线。");
        var snapshots = workflow.listSnapshots(planId, OWNER);
        assertThat(snapshots).hasSize(1);
        assertThat(snapshotRepository.findAllByPlanIdOrderByRevisionDesc(planId).get(0).getDocJson()).contains("总体结论");
        // 修订记录合并（spec §9）：发布动作登记 kind=PUBLISH 版本，快照=含结论正文
        var versions = versionRepository.findByPlanIdOrderByCreatedAtDescIdDesc(planId);
        assertThat(versions).hasSize(1);
        assertThat(versions.get(0).getKind()).isEqualTo(PersistentPlanVersionRecord.KIND_PUBLISH);
        assertThat(versions.get(0).getVersionNo()).isEqualTo("V1.0");
        assertThat(versions.get(0).getChangeNote()).isEqualTo("核心指标全部达成，可上线。");
        assertThat(versions.get(0).getSnapshotBody()).contains("总体结论");
        assertThat(versions.get(0).getPlanPhase()).isEqualTo("PUBLISH");
        // 用户决策（2026-09-11）：放开「发布后冻结编辑」——发布后仍可编辑（新 revision 追加，发布快照/版本登记不变）
        TaskPlan edited = documentService.updateMarkdown(planId, published.revision(), "x", OWNER);
        assertThat(edited.revision()).isEqualTo(published.revision() + 1);
    }

    @Test
    void publishWithoutConclusionSectionDoesNotWriteNullLiteral() {
        PersistentTaskPlanRecord plan = planRepository.findById(planId).orElseThrow();
        plan.updateBody("## 一、背景\n\n内容\n"); // 无「十二、结论」章节
        plan.forceState(PlanPhase.REPORT, PlanStatus.DONE);
        planRepository.save(plan);

        TaskPlan published = workflow.publish(planId, OWNER, "结论文本", "V1.0");
        assertThat(published.phase()).isEqualTo(PlanPhase.PUBLISH);
        String body = planRepository.findById(planId).orElseThrow().getBody();
        assertThat(body).contains("**总体结论**：结论文本");
        assertThat(body).contains("## 十二、结论");
        assertThat(body).doesNotContain("null");
        var snapshot = snapshotRepository.findAllByPlanIdOrderByRevisionDesc(planId).get(0);
        assertThat(snapshot.getDocJson()).contains("总体结论");
        assertThat(snapshot.getDocJson()).doesNotContain("null");
    }

    @Test
    void newRevisionResetsToDraftAndBumps() {
        TaskPlan published = workflow.publish(planId, OWNER, "结论", "V1.0");
        int revision = published.revision();
        TaskPlan next = workflow.newRevision(planId, OWNER);
        assertThat(next.phase()).isEqualTo(PlanPhase.DRAFT);
        assertThat(next.status()).isEqualTo(PlanStatus.DRAFT);
        assertThat(next.revision()).isEqualTo(revision + 1);
        assertThat(planRepository.findById(planId).orElseThrow().getPrecheckExecutedAt()).isNull();
        assertThat(snapshotRepository.findAllByPlanIdOrderByRevisionDesc(planId)).hasSize(1); // 旧快照保留
    }
}
