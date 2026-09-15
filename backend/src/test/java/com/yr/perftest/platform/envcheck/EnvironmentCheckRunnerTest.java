package com.yr.perftest.platform.envcheck;

import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.project.PersistentProjectMemberRecord;
import com.yr.perftest.platform.project.PersistentProjectMemberRepository;
import com.yr.perftest.platform.project.PersistentProjectRecord;
import com.yr.perftest.platform.project.PersistentProjectRepository;
import com.yr.perftest.platform.project.ProjectRole;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.PersistentTaskScenarioRecord;
import com.yr.perftest.platform.task.PersistentTaskScenarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 编排：缺凭据开跑拦截（E8）+ LOCAL 项纳入矩阵 + 不可达机器归 WARNING（spec §4）。 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:envcheck-runner-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Transactional
class EnvironmentCheckRunnerTest {

    /** HumanPrincipal 内部 EnumSet.copyOf 拒绝空集合，沿用现有脚手架角色集。 */
    private static final HumanPrincipal OWNER =
            new HumanPrincipal("owner", Set.of(com.yr.perftest.platform.identity.SystemRole.PROJECT_MEMBER));

    @Autowired EnvironmentCheckRunner runner;
    @Autowired EnvCheckCredentialService credentials;
    @Autowired com.yr.perftest.platform.task.TaskPlanService planService;
    @Autowired com.yr.perftest.platform.task.plandoc.PlanWorkflowService workflow;
    @Autowired PersistentTaskPlanRepository planRepository;
    @Autowired PersistentTaskScenarioRepository scenarioRepository;
    @Autowired PersistentProjectRepository projectRepository;
    @Autowired PersistentProjectMemberRepository memberRepository;

    /** H2 identity 不随 @Transactional 回滚复位，凭据归属项目 id 由 seed 捕获（不能硬编码 1L）。 */
    private long seededProjectId;

    @Test
    void missingCredentialBlocksBeforeAnyProbe() {
        long planId = seedPlanWithEnvTable("10.5.5.5", "订单服务"); // 无凭据
        var settings = new com.yr.perftest.platform.task.plandoc.PrecheckSettings(
                true, List.of("doc.metrics-defined", "os.ulimit"));
        workflow.updatePrecheckSettings(planId, OWNER, settings);
        assertThatThrownBy(() -> runner.run(planId, "owner", false))
                .isInstanceOf(EnvCheckCredentialMissingException.class)
                .extracting(e -> ((EnvCheckCredentialMissingException) e).missingHosts())
                .isEqualTo(List.of("10.5.5.5"));
    }

    @Test
    void unreachableHostBecomesWarningRunCompletes() {
        long planId = seedPlanWithEnvTable("127.0.0.1", "订单服务");
        credentials.save(seededProjectId, "owner", new EnvCheckCredentialService.CredentialInput(
                "127.0.0.1", 22, "nobody", "bad-pass", null, null, null));
        var settings = new com.yr.perftest.platform.task.plandoc.PrecheckSettings(
                true, List.of("doc.metrics-defined", "os.ulimit"));
        workflow.updatePrecheckSettings(planId, OWNER, settings);
        var run = runner.run(planId, "owner", false);
        assertThat(run.getWarned()).isGreaterThanOrEqualTo(1); // ssh 认证失败 → WARNING
        assertThat(run.getPassed()).isGreaterThanOrEqualTo(1); // LOCAL 项通过
    }

    /** 建项目 + owner 成员 + 计划（「三、测试指标」指标表 + 「五、测试资源 → 环境部署信息」表）+ 一个已绑定脚本的场景。 */
    private long seedPlanWithEnvTable(String host, String module) {
        PersistentProjectRecord project = projectRepository.save(
                new PersistentProjectRecord("P1", "项目一", "", "owner"));
        memberRepository.save(new PersistentProjectMemberRecord(project.getId(), "owner", ProjectRole.OWNER));
        PersistentTaskPlanRecord plan = planRepository.save(
                new PersistentTaskPlanRecord(project.getId(), "计划一", null, "owner"));
        plan.updateBody("## 三、测试指标\n\n| 交易 | 指标 | 目标值 | 口径 |\n|---|---|---|---|\n| 查询 | TPS | 200 | 均值 |\n\n"
                + "## 五、测试资源\n\n### 环境部署信息\n\n| 地址 | 模块 | 说明 |\n|---|---|---|\n| "
                + host + " | " + module + " | 压测目标 |\n");
        long planId = planRepository.save(plan).getId();
        PersistentTaskScenarioRecord scenario = scenarioRepository.save(
                new PersistentTaskScenarioRecord(planId, null, "场景A", 0));
        scenario.bindScript(9L);
        scenarioRepository.save(scenario);
        seededProjectId = project.getId();
        return planId;
    }
}
