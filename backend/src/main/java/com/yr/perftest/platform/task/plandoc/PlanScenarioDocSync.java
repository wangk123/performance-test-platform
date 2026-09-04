package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskScenarioRecord;
import com.yr.perftest.platform.task.PersistentTaskScenarioRepository;
import com.yr.perftest.platform.task.ScenarioThreadGroupConfig;
import com.yr.perftest.platform.task.ScenarioThreadGroupConfigSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 场景实体 → 文档七章节回写：只动"事实"（标题/目的/设置表），保留自由文本与执行记录。 */
@Service
public class PlanScenarioDocSync {
    private final PersistentTaskPlanRepository planRepository;
    private final PersistentTaskScenarioRepository scenarioRepository;
    private final ScenarioThreadGroupConfigSupport configSupport;

    public PlanScenarioDocSync(PersistentTaskPlanRepository planRepository,
                               PersistentTaskScenarioRepository scenarioRepository,
                               ScenarioThreadGroupConfigSupport configSupport) {
        this.planRepository = planRepository;
        this.scenarioRepository = scenarioRepository;
        this.configSupport = configSupport;
    }

    @Transactional
    public void syncPlanScenarios(long planId) {
        PersistentTaskPlanRecord plan = planRepository.findById(planId).orElseThrow();
        String base = plan.getBody() == null ? "" : plan.getBody();
        String body = base;
        for (PersistentTaskScenarioRecord scenario : scenarioRepository.findAllByPlanIdOrderBySortOrderAscIdAsc(planId)) {
            List<ScenarioThreadGroupConfig> configs = configSupport.readStored(scenario.getThreadGroupConfigsJson());
            body = PlanMarkdownSupport.upsertScenarioFacts(body, scenario.getName(), generatedBlockOf(scenario, configs));
        }
        if (!body.equals(base)) {
            plan.updateBody(body);
        }
    }

    @Transactional
    public void onScenarioDeleted(long planId, String scenarioName) {
        PersistentTaskPlanRecord plan = planRepository.findById(planId).orElseThrow();
        String base = plan.getBody();
        if (base == null) {
            return;
        }
        String body = PlanMarkdownSupport.removeScenarioBlock(base, scenarioName);
        if (!body.equals(base)) {
            plan.updateBody(body);
        }
    }

    private String generatedBlockOf(PersistentTaskScenarioRecord scenario, List<ScenarioThreadGroupConfig> configs) {
        String type = scenario.getTestType() == null ? "UNSPECIFIED" : scenario.getTestType().name();
        StringBuilder block = new StringBuilder()
                .append("### S").append(scenario.getSortOrder() + 1).append(' ')
                .append(scenario.getName()).append(" · ").append(type).append("\n\n")
                .append("**场景目的**：").append(scenario.getPurpose() == null || scenario.getPurpose().isBlank()
                        ? "（待填写）" : scenario.getPurpose().trim()).append("\n\n")
                .append("**测试方法**：（自由编辑，实体同步不触碰此处）\n\n")
                .append("**交易范围**：（自由编辑，实体同步不触碰此处）\n\n")
                .append("**场景设置**（由场景执行配置生成，勿手改）：\n\n")
                .append("| 用户数 | 持续时长 | 加载方式 | 退出方式 |\n|---|---|---|---|\n");
        for (List<ScenarioThreadGroupConfig> preset : configSupport.groupPresetsBySortOrder(configs)) {
            int threads = preset.stream().mapToInt(ScenarioThreadGroupConfig::threads).sum();
            int duration = preset.stream().mapToInt(ScenarioThreadGroupConfig::duration).max().orElse(0);
            int rampUp = preset.get(0).rampUp();
            block.append("| ").append(threads)
                    .append(" | ").append(duration <= 0 ? "不限" : duration + " 秒")
                    .append(" | ").append(rampUp == 0 ? "同时加载" : "匀速加载 " + rampUp + " 秒")
                    .append(" | 同时退出 |\n");
        }
        return block.toString();
    }
}
