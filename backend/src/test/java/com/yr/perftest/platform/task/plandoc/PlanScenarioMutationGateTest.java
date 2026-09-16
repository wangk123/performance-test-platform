package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.task.PersistentScenarioExecutionRecord;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRepository;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.PersistentTaskScenarioRecord;
import com.yr.perftest.platform.task.PersistentTaskScenarioRepository;
import com.yr.perftest.platform.task.TaskScenarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 场景增删改门禁（spec §4.4）：存在活跃执行则冻结；发布后不冻结。bindScript 按状态放行。 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-scenario-gate-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.h2.console.enabled=false"
})
@Transactional
class PlanScenarioMutationGateTest {

    @Autowired
    private PersistentTaskPlanRepository planRepository;
    @Autowired
    private PersistentTaskScenarioRepository scenarioRepository;
    @Autowired
    private PersistentScenarioExecutionRepository executionRepository;
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

    private void forceState(PlanStatus status) {
        PersistentTaskPlanRecord plan = planRepository.findById(planId).orElseThrow();
        plan.forceState(status);
        planRepository.save(plan);
    }

    private void startActiveExecution() {
        PersistentScenarioExecutionRecord execution = executionRepository.save(
                new PersistentScenarioExecutionRecord(scenarioId,
                        "{\"threads\":1,\"rampUp\":0,\"duration\":1,\"loops\":1,\"jmeterProperties\":{}}"));
        execution.markRunning("result.jtl", "jmeter.log"); // RUNNING = 活跃执行
        executionRepository.save(execution);
    }

    @Test
    void createScenarioRejectedWhenActiveExecutionExists() {
        forceState(PlanStatus.EXECUTING);
        startActiveExecution();
        assertThatThrownBy(() -> scenarioService.createScenario(
                planId, null, "新场景", null, null, null, null, null, null, null, null))
                .isInstanceOf(PlanStateException.class)
                .hasMessageContaining("存在活跃执行，场景禁止增删改")
                .hasFieldOrPropertyWithValue("status", PlanStatus.EXECUTING);
        assertThat(scenarioRepository.findAllByPlanIdOrderBySortOrderAscIdAsc(planId)).hasSize(1);
    }

    @Test
    void updateScenarioRejectedWhenActiveExecutionExists() {
        forceState(PlanStatus.REPORTING);
        startActiveExecution();
        assertThatThrownBy(() -> scenarioService.updateScenario(
                scenarioId, "改名", null, null, null, null, null, null, null, null, null, false))
                .isInstanceOf(PlanStateException.class)
                .hasMessageContaining("禁止增删改");
        assertThat(scenarioRepository.findById(scenarioId).orElseThrow().getName()).isEqualTo("场景A");
    }

    @Test
    void deleteScenarioRejectedWhenActiveExecutionExists() {
        forceState(PlanStatus.PLANNING);
        startActiveExecution();
        assertThatThrownBy(() -> scenarioService.deleteScenario(scenarioId))
                .isInstanceOf(PlanStateException.class)
                .hasMessageContaining("禁止增删改");
        assertThat(scenarioRepository.findById(scenarioId)).isPresent();
    }

    @Test
    void mutationAllowedInAnyStatusWithoutActiveExecution() {
        // 无活跃执行任意状态放行（含 EXECUTING 与已发布；发布后不再冻结，spec §4.4）
        for (PlanStatus status : PlanStatus.values()) {
            forceState(status);
            assertThatCode(() -> scenarioService.updateScenario(
                    scenarioId, "改名-" + status, null, null, null, null, null, null, null, null, null, false))
                    .doesNotThrowAnyException();
        }
        assertThat(scenarioRepository.findById(scenarioId).orElseThrow().getName()).isEqualTo("改名-PUBLISHED");
    }

    @Test
    void bindScriptForbiddenBeforeApproval() {
        // 9L 为不存在的脚本：门禁先于脚本存在性校验抛 PLAN_STATE
        assertThatThrownBy(() -> scenarioService.bindScript(scenarioId, 9L))
                .isInstanceOf(PlanStateException.class)
                .hasMessageContaining("评审通过后才可关联脚本");
        forceState(PlanStatus.IN_REVIEW);
        assertThatThrownBy(() -> scenarioService.bindScript(scenarioId, 9L))
                .isInstanceOf(PlanStateException.class)
                .hasMessageContaining("评审通过后才可关联脚本");
    }

    @Test
    void bindScriptAllowedAfterApproval() {
        for (PlanStatus status : PlanStatus.values()) {
            if (status == PlanStatus.PLANNING || status == PlanStatus.IN_REVIEW) {
                continue;
            }
            forceState(status);
            // 状态门禁已放行：异常来自脚本存在性校验（9L 不存在），而非 PLAN_STATE
            assertThatThrownBy(() -> scenarioService.bindScript(scenarioId, 9L))
                    .as("bindScript in %s", status)
                    .isNotInstanceOf(PlanStateException.class);
        }
    }
}
