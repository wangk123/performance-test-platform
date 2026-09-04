package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.execution.distributed.ExecutionNodeRole;
import com.yr.perftest.platform.execution.distributed.ExecutionNodeStatus;
import com.yr.perftest.platform.execution.distributed.PersistentExecutionNodeRecord;
import com.yr.perftest.platform.execution.distributed.PersistentExecutionNodeRepository;
import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.identity.SystemRole;
import com.yr.perftest.platform.project.PersistentProjectRecord;
import com.yr.perftest.platform.project.PersistentProjectRepository;
import com.yr.perftest.platform.script.ScriptDefinition;
import com.yr.perftest.platform.script.ScriptService;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRepository;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.PersistentTaskScenarioRecord;
import com.yr.perftest.platform.task.PersistentTaskScenarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 快捷执行集成测试（设计 §10.4）：单事务建计划(EXECUTION)→系统批注→建场景(带脚本)→start。
 * 不加 @Transactional：quickExecute 需真实提交以触发 afterCommit 的执行提交；
 * 异步 runner 在测试环境必然失败并终态化（此处只断言同步可见的状态，终态转化容忍异步）。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-quick-exec-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PlanQuickExecuteServiceTest {

    private static final HumanPrincipal ACTOR = new HumanPrincipal("tester", Set.of(SystemRole.PROJECT_MEMBER));

    @Autowired
    private PlanQuickExecuteService quickExecuteService;
    @Autowired
    private PersistentProjectRepository projectRepository;
    @Autowired
    private PersistentExecutionNodeRepository executionNodeRepository;
    @Autowired
    private ScriptService scriptService;
    @Autowired
    private PersistentTaskPlanRepository planRepository;
    @Autowired
    private PersistentTaskScenarioRepository scenarioRepository;
    @Autowired
    private PersistentScenarioExecutionRepository executionRepository;
    @Autowired
    private PersistentPlanCommentRepository commentRepository;

    private long seedProjectWithScript() {
        PersistentProjectRecord project = projectRepository.save(new PersistentProjectRecord("P1", "项目一", "", "tester"));
        return project.getId();
    }

    private void seedAvailableController() {
        PersistentExecutionNodeRecord node = new PersistentExecutionNodeRecord(
                "控制节点A", "controller.invalid.test", 22, "jmeter", "/dev/null/id_rsa",
                ExecutionNodeRole.BOTH, "/tmp/jmeter-work");
        node.markHealth(ExecutionNodeStatus.AVAILABLE, "seeded for test");
        executionNodeRepository.save(node);
    }

    @Test
    void quickExecuteCreatesPlanScenarioSystemCommentAndExecutionInOneShot() {
        long projectId = seedProjectWithScript();
        seedAvailableController();
        ScriptDefinition script = scriptService.createScript(projectId, "查询交易", "tester");

        PlanQuickExecuteService.QuickExecuteResult result =
                quickExecuteService.quickExecute(script.id(), ACTOR);

        assertThat(result.planId()).isPositive();
        assertThat(result.scenarioId()).isPositive();
        assertThat(result.executionId()).isPositive();

        // 计划：直接进入执行阶段；异步 runner 失败后可能已置 DONE，故只断言阶段与状态集合
        PersistentTaskPlanRecord plan = planRepository.findById(result.planId()).orElseThrow();
        assertThat(plan.getPhase()).isEqualTo(PlanPhase.EXECUTION);
        assertThat(plan.getStatus()).isIn(PlanStatus.RUNNING, PlanStatus.DONE);
        assertThat(plan.getDefaultControllerNodeId()).isNotNull();
        // 环境检查默认不启用 → 未执行过预检
        assertThat(plan.getPrecheckExecutedAt()).isNull();

        // 场景：建场景即绑定脚本
        PersistentTaskScenarioRecord scenario = scenarioRepository.findById(result.scenarioId()).orElseThrow();
        assertThat(scenario.getScriptVersionId()).isEqualTo(script.id());
        assertThat(scenario.getName()).isEqualTo("查询交易.jmx");

        // 系统批注：快捷执行自动通过评审
        assertThat(commentRepository.findAllByPlanIdOrderByIdAsc(result.planId()))
                .anySatisfy(comment -> {
                    assertThat(comment.getKind()).isEqualTo(PlanCommentKind.SYSTEM);
                    assertThat(comment.getContent()).contains("快捷执行自动通过评审");
                });

        // 执行记录已建立（测试环境无真实远端节点 → 异步 runner 会失败终态化，不在此断言 SUCCESS）
        assertThat(executionRepository.findById(result.executionId())).isPresent();
    }

    @Test
    void quickExecuteWithoutAvailableControllerFailsAndRollsBack() {
        long projectId = seedProjectWithScript();
        ScriptDefinition script = scriptService.createScript(projectId, "查询交易", "tester");

        // 无可用控制节点 → 建链路沿用既有「controller node is required」同步报错并整体回滚
        assertThatThrownBy(() -> quickExecuteService.quickExecute(script.id(), ACTOR))
                .isInstanceOf(com.yr.perftest.platform.execution.ExecutionValidationException.class)
                .hasMessageContaining("controller node is required");
        assertThat(planRepository.findAllByProjectIdOrderByIdDesc(projectId)).isEmpty();
    }
}
