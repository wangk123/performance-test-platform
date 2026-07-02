package com.yr.perftest.platform.task;

import com.yr.perftest.platform.execution.ExecutionConfig;
import com.yr.perftest.platform.execution.ExecutionMode;
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
        if (threadGroupConfigId != null) {
            List<ScenarioThreadGroupConfig> configs = configSupport.readStored(scenario.getThreadGroupConfigsJson());
            ScenarioThreadGroupConfig selected = configSupport.requireConfig(configs, threadGroupConfigId);
            threads = selected.threads();
            rampUp = selected.rampUp();
            duration = selected.duration();
            loops = 1;
            selectedConfigId = selected.id();
            stepId = selected.stepId();
            stepName = selected.stepName();
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
