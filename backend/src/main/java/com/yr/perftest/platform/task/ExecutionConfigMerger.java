package com.yr.perftest.platform.task;

import com.yr.perftest.platform.execution.ExecutionConfig;
import com.yr.perftest.platform.execution.ExecutionMode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ExecutionConfigMerger {
    public ExecutionConfig merge(PersistentTaskPlanRecord plan, PersistentTaskScenarioRecord scenario) {
        Long controllerNodeId = scenario.getControllerNodeId() != null
                ? scenario.getControllerNodeId()
                : plan.getDefaultControllerNodeId();
        List<Long> workerNodeIds = scenario.getWorkerNodeIdsJson() != null
                ? taskJson.readLongList(scenario.getWorkerNodeIdsJson())
                : taskJson.readLongList(plan.getDefaultWorkerNodeIdsJson());
        List<Long> monitorTargetIds = scenario.getMonitorTargetIdsJson() != null
                ? taskJson.readLongList(scenario.getMonitorTargetIdsJson())
                : taskJson.readLongList(plan.getDefaultMonitorTargetIdsJson());
        return new ExecutionConfig(
                scenario.getThreads(),
                scenario.getRampUp(),
                scenario.getDuration(),
                scenario.getLoops(),
                taskJson.readStringMap(scenario.getJmeterPropertiesJson()),
                ExecutionMode.DISTRIBUTED,
                controllerNodeId,
                workerNodeIds,
                monitorTargetIds
        );
    }

    private final TaskJsonSupport taskJson;

    public ExecutionConfigMerger(TaskJsonSupport taskJson) {
        this.taskJson = taskJson;
    }
}
