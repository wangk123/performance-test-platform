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
        "spring.datasource.url=jdbc:h2:mem:plan-publish-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
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
    private PlanDocumentService documentService;

    private long planId;

    @BeforeEach
    void setUp() {
        PersistentProjectRecord project = projectRepository.save(new PersistentProjectRecord("P1", "项目一", "", "owner"));
        memberRepository.save(new PersistentProjectMemberRecord(project.getId(), "owner", ProjectRole.OWNER));
        PersistentTaskPlanRecord plan = planRepository.save(
                new PersistentTaskPlanRecord(project.getId(), "计划", null, "owner"));
        plan.updateBody("""
                ## 七、场景设计

                ### S1 登录 · SINGLE_TXN

                **场景目的**：p

                #### 执行记录

                ## 十一、结论

                ### 指标达成表

                | 指标 | 目标 | 实际结果 | 状态 |
                |---|---|---|---|
                | 登录 TPS | ≥ 200 | 待执行 | 待判定 |

                **总体结论**：（发布时填写）
                """);
        plan.forceState(PlanPhase.REPORT, PlanStatus.PENDING);
        planId = planRepository.save(plan).getId();
    }

    @Test
    void generateReportWritesOverviewAndActualColumn() {
        TaskPlan plan = workflow.generateReport(planId, OWNER);
        assertThat(plan.status()).isEqualTo(PlanStatus.DONE);
        String body = planRepository.findById(planId).orElseThrow().getBody();
        assertThat(body).contains("<!-- backfill:report -->");
        assertThat(body).contains("#### 执行结果总览");
        assertThat(body).contains("| 登录 TPS | ≥ 200 | 待执行 | 待判定 |"); // 无执行时实际列不被改写
        // 再生成：整块替换，不重复堆叠
        workflow.generateReport(planId, OWNER);
        String again = planRepository.findById(planId).orElseThrow().getBody();
        assertThat(again.split("<!-- backfill:report -->", -1).length - 1).isEqualTo(1);
        assertThat(again.split("#### 执行结果总览", -1).length - 1).isEqualTo(1);
    }

    @Test
    void publishRequiresConclusionAndWritesItAndSnapshot() {
        workflow.generateReport(planId, OWNER);
        assertThatThrownBy(() -> workflow.publish(planId, OWNER, " "))
                .isInstanceOf(PlanValidationException.class);
        TaskPlan published = workflow.publish(planId, OWNER, "核心指标全部达成，可上线。");
        assertThat(published.phase()).isEqualTo(PlanPhase.PUBLISH);
        assertThat(published.status()).isEqualTo(PlanStatus.PUBLISHED);
        String body = planRepository.findById(planId).orElseThrow().getBody();
        assertThat(body).contains("**总体结论**：核心指标全部达成，可上线。");
        var snapshots = workflow.listSnapshots(planId, OWNER);
        assertThat(snapshots).hasSize(1);
        assertThat(snapshotRepository.findAllByPlanIdOrderByRevisionDesc(planId).get(0).getDocJson()).contains("总体结论");
        // 冻结：编辑被拒
        assertThatThrownBy(() -> documentService.updateMarkdown(planId, published.revision(), "x", OWNER))
                .isInstanceOf(PlanStateException.class);
    }

    @Test
    void publishWithoutConclusionSectionDoesNotWriteNullLiteral() {
        PersistentTaskPlanRecord plan = planRepository.findById(planId).orElseThrow();
        plan.updateBody("## 一、背景\n\n内容\n"); // 无「十一、结论」章节
        plan.forceState(PlanPhase.REPORT, PlanStatus.DONE);
        planRepository.save(plan);

        TaskPlan published = workflow.publish(planId, OWNER, "结论文本");
        assertThat(published.phase()).isEqualTo(PlanPhase.PUBLISH);
        String body = planRepository.findById(planId).orElseThrow().getBody();
        assertThat(body).contains("**总体结论**：结论文本");
        assertThat(body).contains("## 十一、结论");
        assertThat(body).doesNotContain("null");
        var snapshot = snapshotRepository.findAllByPlanIdOrderByRevisionDesc(planId).get(0);
        assertThat(snapshot.getDocJson()).contains("总体结论");
        assertThat(snapshot.getDocJson()).doesNotContain("null");
    }

    @Test
    void newRevisionResetsToDraftAndBumps() {
        workflow.generateReport(planId, OWNER);
        TaskPlan published = workflow.publish(planId, OWNER, "结论");
        int revision = published.revision();
        TaskPlan next = workflow.newRevision(planId, OWNER);
        assertThat(next.phase()).isEqualTo(PlanPhase.DRAFT);
        assertThat(next.status()).isEqualTo(PlanStatus.DRAFT);
        assertThat(next.revision()).isEqualTo(revision + 1);
        assertThat(planRepository.findById(planId).orElseThrow().getPrecheckExecutedAt()).isNull();
        assertThat(snapshotRepository.findAllByPlanIdOrderByRevisionDesc(planId)).hasSize(1); // 旧快照保留
    }
}
