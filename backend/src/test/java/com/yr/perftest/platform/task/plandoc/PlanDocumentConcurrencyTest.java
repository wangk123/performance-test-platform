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

/**
 * 并发编辑冲突（spec §5.1 三选一的前置：同 baseRevision 恰好一胜一败）。
 * 独立成类且保留每方法重启：用例自带 REQUIRES_NEW 工作线程模拟真实提交，
 * 与类级 @Transactional 回滚模型互斥（外层未提交的 plan 行对工作线程不可见）。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-doc-concurrency-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PlanDocumentConcurrencyTest {

    private static final HumanPrincipal OWNER = new HumanPrincipal("owner", java.util.Set.of(SystemRole.PROJECT_MEMBER));

    @Autowired
    private PlanDocumentService documentService;
    @Autowired
    private PersistentTaskPlanRepository planRepository;
    @Autowired
    private PersistentProjectRepository projectRepository;
    @Autowired
    private PersistentProjectMemberRepository memberRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private long planId;

    @BeforeEach
    void setUp() {
        PersistentProjectRecord project = projectRepository.save(
                new PersistentProjectRecord("P1", "项目一", "", "owner"));
        memberRepository.save(new PersistentProjectMemberRecord(project.getId(), "owner", ProjectRole.OWNER));
        PersistentTaskPlanRecord plan = planRepository.save(
                new PersistentTaskPlanRecord(project.getId(), "计划一", null, "owner"));
        plan.updateBody("## 一、背景\n\n初始内容\n");
        planId = planRepository.save(plan).getId();
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
