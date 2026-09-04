package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.PersistentTaskScenarioRecord;
import com.yr.perftest.platform.task.PersistentTaskScenarioRepository;
import com.yr.perftest.platform.task.TaskScenarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 场景增删改阶段门禁（设计 §4.5）：PUBLISH 与 EXECUTION/RUNNING 冻结。 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-scenario-gate-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PlanScenarioMutationGateTest {

    @Autowired
    private PersistentTaskPlanRepository planRepository;
    @Autowired
    private PersistentTaskScenarioRepository scenarioRepository;
    @Autowired
    private TaskScenarioService scenarioService;

    private long planId;
    private long scenarioId;

    @BeforeEach
    void setUp() {
        PersistentTaskPlanRecord plan = planRepository.save(new PersistentTaskPlanRecord(1L, "计划", null, "owner"));
        planId = planRepository.save(plan).getId();
        PersistentTaskScenarioRecord scenario = scenarioRepository.save(
                new PersistentTaskScenarioRecord(planId, null, "场景A", 0));
        scenarioId = scenarioRepository.save(scenario).getId();
    }

    private void forceState(PlanPhase phase, PlanStatus status) {
        PersistentTaskPlanRecord plan = planRepository.findById(planId).orElseThrow();
        plan.forceState(phase, status);
        planRepository.save(plan);
    }

    @Test
    void createScenarioRejectedOnPublish() {
        forceState(PlanPhase.PUBLISH, PlanStatus.PUBLISHED);
        assertThatThrownBy(() -> scenarioService.createScenario(
                planId, null, "新场景", null, null, null, null, null, null, null))
                .isInstanceOf(PlanStateException.class)
                .hasMessageContaining("禁止增删改")
                .hasFieldOrPropertyWithValue("phase", PlanPhase.PUBLISH)
                .hasFieldOrPropertyWithValue("status", PlanStatus.PUBLISHED);
        assertThat(scenarioRepository.findAllByPlanIdOrderBySortOrderAscIdAsc(planId)).hasSize(1);
    }

    @Test
    void updateScenarioRejectedOnExecutionRunning() {
        forceState(PlanPhase.EXECUTION, PlanStatus.RUNNING);
        assertThatThrownBy(() -> scenarioService.updateScenario(
                scenarioId, "改名", null, null, null, null, null, null, null, null, false))
                .isInstanceOf(PlanStateException.class)
                .hasMessageContaining("禁止增删改");
        assertThat(scenarioRepository.findById(scenarioId).orElseThrow().getName()).isEqualTo("场景A");
    }

    @Test
    void deleteScenarioRejectedOnExecutionRunning() {
        forceState(PlanPhase.EXECUTION, PlanStatus.RUNNING);
        assertThatThrownBy(() -> scenarioService.deleteScenario(scenarioId))
                .isInstanceOf(PlanStateException.class)
                .hasMessageContaining("禁止增删改");
        assertThat(scenarioRepository.findById(scenarioId)).isPresent();
    }

    @Test
    void updateScenarioAllowedOnExecutionDone() {
        forceState(PlanPhase.EXECUTION, PlanStatus.DONE);
        scenarioService.updateScenario(scenarioId, "执行后改名", null, null, null,
                null, null, null, null, null, false);
        assertThat(scenarioRepository.findById(scenarioId).orElseThrow().getName()).isEqualTo("执行后改名");
    }

    @Test
    void updateScenarioRejectedOnPublish() {
        forceState(PlanPhase.PUBLISH, PlanStatus.PUBLISHED);
        assertThatThrownBy(() -> scenarioService.updateScenario(
                scenarioId, "改名", null, null, null, null, null, null, null, null, false))
                .isInstanceOf(PlanStateException.class);
    }

    @Test
    void deleteScenarioRejectedOnPublish() {
        forceState(PlanPhase.PUBLISH, PlanStatus.PUBLISHED);
        assertThatThrownBy(() -> scenarioService.deleteScenario(scenarioId))
                .isInstanceOf(PlanStateException.class);
        assertThat(scenarioRepository.findById(scenarioId)).isPresent();
    }
}
