package com.yr.perftest.platform.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.execution.ExecutionConfig;
import com.yr.perftest.platform.execution.ExecutionMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExecutionConfigMergerTest {
    private final ExecutionConfigMerger merger = new ExecutionConfigMerger(new TaskJsonSupport(new ObjectMapper()));

    @Test
    void inheritsPlanDefaultsWhenScenarioOverridesAreNull() {
        PersistentTaskPlanRecord plan = new PersistentTaskPlanRecord(1L, "plan", "", "admin");
        plan.updateProfile("plan", "", 10L, "[11,12]", "[1,2]");

        PersistentTaskScenarioRecord scenario = new PersistentTaskScenarioRecord(1L, 100L, "scene", 0);
        scenario.updateProfile("scene", 100L, 50, 10, 60, 1, "{}", null, null, null);

        ExecutionConfig config = merger.merge(plan, scenario);
        assertEquals(10L, config.controllerNodeId());
        assertEquals(List.of(11L, 12L), config.workerNodeIds());
        assertEquals(List.of(1L, 2L), config.monitorTargetIds());
        assertEquals(50, config.threads());
        assertEquals(ExecutionMode.DISTRIBUTED, config.mode());
    }

    @Test
    void usesScenarioOverridesWhenPresent() {
        PersistentTaskPlanRecord plan = new PersistentTaskPlanRecord(1L, "plan", "", "admin");
        plan.updateProfile("plan", "", 10L, "[11]", "[1]");

        PersistentTaskScenarioRecord scenario = new PersistentTaskScenarioRecord(1L, 100L, "scene", 0);
        scenario.updateProfile("scene", 100L, 20, 5, 30, 2, "{\"k\":\"v\"}", 99L, "[88]", "[3]");

        ExecutionConfig config = merger.merge(plan, scenario);
        assertEquals(99L, config.controllerNodeId());
        assertEquals(List.of(88L), config.workerNodeIds());
        assertEquals(List.of(3L), config.monitorTargetIds());
        assertEquals(Map.of("k", "v"), config.jmeterProperties());
    }
}
