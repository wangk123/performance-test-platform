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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-doc-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PlanDocumentServiceTest {

    private static final HumanPrincipal OWNER = new HumanPrincipal("owner", java.util.Set.of(SystemRole.PROJECT_MEMBER));
    private static final HumanPrincipal OTHER_MEMBER = new HumanPrincipal("member-b", java.util.Set.of(SystemRole.PROJECT_MEMBER));

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
    @Autowired
    private PlatformTransactionManager transactionManager;

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
    void editOutsideDraftPhaseRejected() {
        PersistentTaskPlanRecord plan = planRepository.findById(planId).orElseThrow();
        plan.forceState(PlanPhase.REVIEW, PlanStatus.PENDING);
        planRepository.save(plan);
        assertThatThrownBy(() -> documentService.updateMarkdown(planId, 2, "x", OWNER))
                .isInstanceOf(PlanStateException.class);
    }

    @Test
    void memberOtherThanOwnerCannotEdit() {
        assertThatThrownBy(() -> documentService.updateMarkdown(planId, 2, "x", OTHER_MEMBER))
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

    @Test
    void lazyCorrectionDemotesRunningToDoneWhenNoActiveExecution() {
        PersistentTaskPlanRecord plan = planRepository.findById(planId).orElseThrow();
        plan.forceState(PlanPhase.EXECUTION, PlanStatus.RUNNING);
        planRepository.save(plan);
        TaskPlan corrected = documentService.getDocument(planId);
        assertThat(corrected.phase()).isEqualTo(PlanPhase.EXECUTION);
        assertThat(corrected.status()).isEqualTo(PlanStatus.DONE);
    }

    @Test
    void concurrentEditsOnSameBaseRevisionExactlyOneWinsOtherGets409() throws Exception {
        long baseRevision = 2;
        int threads = 2;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        List<String> outcomes = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                final int idx = i;
                pool.submit(() -> {
                    TransactionTemplate template = new TransactionTemplate(transactionManager);
                    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                    ready.countDown();
                    try {
                        if (!start.await(10, TimeUnit.SECONDS)) {
                            outcomes.add("OTHER:start-timeout");
                            return;
                        }
                        template.executeWithoutResult(tx -> documentService.updateMarkdown(
                                planId, baseRevision, "## 一、背景\n\n并发内容-" + idx + "\n", OWNER));
                        outcomes.add("OK");
                    } catch (PlanRevisionConflictException conflict) {
                        outcomes.add("CONFLICT:" + conflict.getCurrentRevision());
                    } catch (Throwable other) {
                        outcomes.add("OTHER:" + other);
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(outcomes).hasSize(2);
            assertThat(outcomes).filteredOn(o -> o.startsWith("OK")).hasSize(1);
            assertThat(outcomes).filteredOn(o -> o.startsWith("CONFLICT:")).hasSize(1);
            assertThat(outcomes).filteredOn(o -> o.startsWith("OTHER:")).isEmpty();
            TaskPlan plan = documentService.getDocument(planId);
            assertThat(plan.revision()).isEqualTo(baseRevision + 1);
        } finally {
            pool.shutdownNow();
        }
    }
}
