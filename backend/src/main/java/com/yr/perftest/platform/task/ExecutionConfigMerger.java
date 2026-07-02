package com.yr.perftest.platform.task;

import com.yr.perftest.platform.execution.ExecutionConfig;
import com.yr.perftest.platform.execution.ExecutionMode;
import com.yr.perftest.platform.execution.ExecutionValidationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ExecutionConfigMerger {
    public ExecutionConfig merge(PersistentTaskPlanRecord plan, PersistentTaskScenarioRecord scenario) {
        return merge(plan, scenario, null);
    }

    public ExecutionConfig merge(
            PersistentTaskPlanRecord plan,
            PersistentTaskScenarioRecord scenario,
            Long threadGroupConfigId
    ) {
        return merge(plan, scenario, threadGroupConfigId, null);
    }

    public ExecutionConfig merge(
            PersistentTaskPlanRecord plan,
            PersistentTaskScenarioRecord scenario,
            Long threadGroupConfigId,
            Integer threadGroupPresetSortOrder
    ) {
        Long controllerNodeId = scenario.getControllerNodeId() != null
                ? scenario.getControllerNodeId()
                : plan.getDefaultControllerNodeId();
        List<Long> workerNodeIds = scenario.getWorkerNodeIdsJson() != null
                ? taskJson.readLongList(scenario.getWorkerNodeIdsJson())
                : taskJson.readLongList(plan.getDefaultWorkerNodeIdsJson());
        List<Long> monitorTargetIds = scenario.getMonitorTargetIdsJson() != null
                ? taskJson.readLongList(scenario.getMonitorTargetIdsJson())
                : taskJson.readLongList(plan.getDefaultMonitorTargetIdsJson());
        int threads = 0;
        int rampUp = 0;
        int duration = 0;
        int loops = 0;
        Long selectedConfigId = null;
        String stepId = null;
        String stepName = null;
        if (threadGroupPresetSortOrder != null || threadGroupConfigId != null) {
            List<ScenarioThreadGroupConfig> configs = configSupport.readStored(scenario.getThreadGroupConfigsJson());
            List<ScenarioThreadGroupConfig> preset = threadGroupPresetSortOrder != null
                    ? configSupport.presetConfigsBySortOrder(configs, threadGroupPresetSortOrder)
                    : configSupport.presetConfigs(configs, threadGroupConfigId);
            if (preset.isEmpty()) {
                throw new ExecutionValidationException("thread group config does not exist");
            }
            ScenarioThreadGroupConfig selected = preset.get(0);
            threads = preset.stream().mapToInt(ScenarioThreadGroupConfig::threads).sum();
            rampUp = preset.stream().mapToInt(ScenarioThreadGroupConfig::rampUp).max().orElse(0);
            duration = preset.stream().mapToInt(ScenarioThreadGroupConfig::duration).max().orElse(0);
            loops = 1;
            selectedConfigId = selected.id();
            stepId = preset.size() == 1 ? selected.stepId() : null;
            stepName = preset.size() == 1 ? selected.stepName() : null;
            return new ExecutionConfig(
                    threads,
                    rampUp,
                    duration,
                    loops,
                    taskJson.readStringMap(scenario.getJmeterPropertiesJson()),
                    ExecutionMode.DISTRIBUTED,
                    controllerNodeId,
                    workerNodeIds,
                    monitorTargetIds,
                    selectedConfigId,
                    selected.sortOrder(),
                    stepId,
                    stepName
            );
        }
        return new ExecutionConfig(
                threads,
                rampUp,
                duration,
                loops,
                taskJson.readStringMap(scenario.getJmeterPropertiesJson()),
                ExecutionMode.DISTRIBUTED,
                controllerNodeId,
                workerNodeIds,
                monitorTargetIds,
                selectedConfigId,
                null,
                stepId,
                stepName
        );
    }

    private final TaskJsonSupport taskJson;
    private final ScenarioThreadGroupConfigSupport configSupport;

    public ExecutionConfigMerger(TaskJsonSupport taskJson, ScenarioThreadGroupConfigSupport configSupport) {
        this.taskJson = taskJson;
        this.configSupport = configSupport;
    }
}
