package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskScenarioRecord;
import com.yr.perftest.platform.task.TestType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PlanEntityExtensionTest {
    @Autowired
    private TestEntityManager entityManager;

    @Test
    void newPlanDefaultsToDraftPhaseAndRevisionOne() {
        PersistentTaskPlanRecord plan = entityManager.persistFlushFind(
                new PersistentTaskPlanRecord(1L, "plan-a", null, "admin"));
        assertThat(plan.getPhase()).isEqualTo(PlanPhase.DRAFT);
        assertThat(plan.getStatus()).isEqualTo(PlanStatus.DRAFT);
        assertThat(plan.getRevision()).isEqualTo(1);
        assertThat(plan.getBody()).isNull();
        assertThat(plan.getPublishedAt()).isNull();
        assertThat(plan.getPrecheckJson()).isNull();
        assertThat(plan.getPrecheckExecutedAt()).isNull();
    }

    @Test
    void updateBodyBumpsRevisionEachWrite() {
        PersistentTaskPlanRecord plan = entityManager.persist(new PersistentTaskPlanRecord(1L, "p", null, "a"));
        plan.updateBody("# 一、背景\n内容");
        assertThat(plan.getRevision()).isEqualTo(2);
        plan.updateBody("# 一、背景\n内容2");
        assertThat(plan.getRevision()).isEqualTo(3);
        assertThat(plan.getBody()).isEqualTo("# 一、背景\n内容2");
    }

    @Test
    void scenarioAcceptsNullScriptAndBusinessFields() {
        PersistentTaskPlanRecord plan = entityManager.persist(new PersistentTaskPlanRecord(1L, "p", null, "a"));
        PersistentTaskScenarioRecord scenario = entityManager.persistFlushFind(
                new PersistentTaskScenarioRecord(plan.getId(), null, "场景A", 0));
        assertThat(scenario.getScriptVersionId()).isNull();
        scenario.updateBusinessFields("验证单交易并发能力", TestType.SINGLE_TXN);
        scenario.bindScript(42L);
        assertThat(scenario.getPurpose()).isEqualTo("验证单交易并发能力");
        assertThat(scenario.getTestType()).isEqualTo(TestType.SINGLE_TXN);
        assertThat(scenario.getScriptVersionId()).isEqualTo(42L);
    }
}
