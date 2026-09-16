package com.yr.perftest.platform.envcheck;

import com.yr.perftest.platform.envcheck.EnvironmentCheckRunner.TargetsPreview;
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
    @Autowired EnvCheckFixService fixService;
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

    /** 修复链路正向分流：LOCAL / 不可修 REMOTE 项跳过（不触网）；认证失败主机的 apply 归 failed 不假报成功。 */
    @Test
    void fixRoutesSkipsAndAuthFailures() {
        long planId = seedPlanWithEnvTable("127.0.0.1", "订单服务");
        credentials.save(seededProjectId, "owner", new EnvCheckCredentialService.CredentialInput(
                "127.0.0.1", 22, "nobody", "bad-pass", null, null, null));
        var settings = new com.yr.perftest.platform.task.plandoc.PrecheckSettings(
                true, List.of("doc.metrics-defined", "os.ulimit", "os.disk-usage"));
        workflow.updatePrecheckSettings(planId, OWNER, settings);
        var run = runner.run(planId, "owner", false);
        var outcome = fixService.apply(run.getId(), List.of(
                new EnvCheckFixService.FixRequest(null, "doc.metrics-defined"),
                new EnvCheckFixService.FixRequest("127.0.0.1", "os.disk-usage"),
                new EnvCheckFixService.FixRequest("127.0.0.1", "os.ulimit")), "owner");
        assertThat(outcome.fixed()).isEmpty();
        assertThat(outcome.skipped()).containsExactlyInAnyOrder(
                "doc.metrics-defined@null", "os.disk-usage@127.0.0.1");
        assertThat(outcome.failed()).containsExactly("os.ulimit@127.0.0.1");
    }

    /** previewTargets：三态凭据合成 + applicable 按 appliesTo 匹配（mysql 项只匹配模块列含 mysql 的机器）。 */
    @Test
    void previewTargetsComposesCredentialStateAndApplicability() {
        long planId = seedPlanWithEnvTable("10.0.0.1", "mysql 主库");
        long projectId = seededProjectId;
        // 项目池凭据：10.0.0.1；计划覆盖：不存在
        credentials.save(projectId, "owner", new EnvCheckCredentialService.CredentialInput(
                "10.0.0.1", 22, "perf", "pw", null, null, null));
        // 同项目再起一个计划：10.0.0.2 无凭据（projects.code 唯一，不能重复 seed 整个项目）
        long planNoCred = seedExtraPlan(projectId, "10.0.0.2", "redis");

        TargetsPreview preview = runner.previewTargets(planId);
        assertThat(preview.total()).isEqualTo(1);
        assertThat(preview.ready()).isEqualTo(1);
        assertThat(preview.missing()).isEmpty();
        assertThat(preview.targets()).hasSize(1);
        assertThat(preview.targets().get(0).host()).isEqualTo("10.0.0.1");
        assertThat(preview.targets().get(0).credential()).isEqualTo("POOL");
        // 默认设置（未保存过 precheckJson）勾选集为 DEFAULT_ITEMS（全 LOCAL），远程项 0
        assertThat(preview.targets().get(0).applicableRemoteItems()).isZero();

        TargetsPreview noCred = runner.previewTargets(planNoCred);
        assertThat(noCred.total()).isEqualTo(1);
        assertThat(noCred.ready()).isZero();
        assertThat(noCred.missing()).containsExactly("10.0.0.2");
        assertThat(noCred.targets().get(0).credential()).isEqualTo("MISSING");
    }

    /** 计划覆盖优先于项目池：credential = PLAN_OVERRIDE。 */
    @Test
    void previewTargetsMarksPlanOverride() {
        long planId = seedPlanWithEnvTable("10.0.0.3", "app");
        long projectId = seededProjectId;
        credentials.save(projectId, "owner", new EnvCheckCredentialService.CredentialInput(
                "10.0.0.3", 22, "perf", "pw", null, null, null));
        credentials.save(projectId, "owner", new EnvCheckCredentialService.CredentialInput(
                "10.0.0.3", 36000, "deploy", null, "FAKE-PEM", null, planId));

        TargetsPreview preview = runner.previewTargets(planId);
        assertThat(preview.targets().get(0).credential()).isEqualTo("PLAN_OVERRIDE");
    }

    /** 文档无「环境部署信息」表：空清单不报错。 */
    @Test
    void previewTargetsEmptyWhenDocHasNoEnvTable() {
        PersistentProjectRecord project = projectRepository.save(
                new PersistentProjectRecord("P2", "项目二", "", "owner"));
        memberRepository.save(new PersistentProjectMemberRecord(project.getId(), "owner", ProjectRole.OWNER));
        PersistentTaskPlanRecord plan = planRepository.save(
                new PersistentTaskPlanRecord(project.getId(), "计划二", null, "owner"));
        plan.updateBody("## 五、测试资源\n\n（未写部署表）\n");
        long planId = planRepository.save(plan).getId();

        TargetsPreview preview = runner.previewTargets(planId);
        assertThat(preview.total()).isZero();
        assertThat(preview.targets()).isEmpty();
    }

    /** applicable 计数：勾选含远程项时，带 mysql 标签的项只对模块列含 mysql 的机器计数。 */
    @Test
    void previewTargetsCountsApplicableRemoteItems() {
        long planId = seedPlanWithEnvTable("10.0.0.4", "mysql 主库");
        // 打开环境检查并勾选两项远程：middleware.mysql（appliesTo={mysql}）+ os.ulimit（通用）
        planRepository.findById(planId).ifPresent(p -> {
            p.updatePrecheckJson("{\"enabled\":true,\"items\":[\"middleware.mysql\",\"os.ulimit\"]}");
            planRepository.save(p);
        });

        TargetsPreview preview = runner.previewTargets(planId);
        // middleware.mysql 项 appliesTo={mysql} 匹配「mysql 主库」，os.ulimit 通用 → 2
        assertThat(preview.targets().get(0).applicableRemoteItems()).isEqualTo(2);
    }

    /** 在既有项目下补建一个仅含「环境部署信息」表（host/module）的计划，返回 planId。 */
    private long seedExtraPlan(long projectId, String host, String module) {
        PersistentTaskPlanRecord plan = planRepository.save(
                new PersistentTaskPlanRecord(projectId, "计划二", null, "owner"));
        plan.updateBody("## 五、测试资源\n\n### 环境部署信息\n\n| 地址 | 模块 | 说明 |\n|---|---|---|\n| "
                + host + " | " + module + " | 压测目标 |\n");
        return planRepository.save(plan).getId();
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
