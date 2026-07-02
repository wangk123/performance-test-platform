package com.yr.perftest.platform.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.execution.ExecutionConfig;
import com.yr.perftest.platform.execution.ExecutionMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ExecutionConfigMergerTest {
    private final ExecutionConfigMerger merger = new ExecutionConfigMerger(
            new TaskJsonSupport(new ObjectMapper()),
            new ScenarioThreadGroupConfigSupport(new ObjectMapper(), null)
    );

    @Test
    void inheritsPlanDefaultsWhenScenarioOverridesAreNull() {
        PersistentTaskPlanRecord plan = new PersistentTaskPlanRecord(1L, "plan", "", "admin");
        plan.updateProfile("plan", "", 10L, "[11,12]", "[1,2]");

        PersistentTaskScenarioRecord scenario = new PersistentTaskScenarioRecord(1L, 100L, "scene", 0);

        ExecutionConfig config = merger.merge(plan, scenario);
        assertEquals(10L, config.controllerNodeId());
        assertEquals(List.of(11L, 12L), config.workerNodeIds());
        assertEquals(List.of(1L, 2L), config.monitorTargetIds());
        assertEquals(0, config.threads());
        assertEquals(ExecutionMode.DISTRIBUTED, config.mode());
    }

    @Test
    void usesScenarioOverridesWhenPresent() {
        PersistentTaskPlanRecord plan = new PersistentTaskPlanRecord(1L, "plan", "", "admin");
        plan.updateProfile("plan", "", 10L, "[11]", "[1]");

        PersistentTaskScenarioRecord scenario = new PersistentTaskScenarioRecord(1L, 100L, "scene", 0);
        scenario.updateProfile("scene", 100L, "{\"k\":\"v\"}", 99L, "[88]", "[3]", "[]");

        ExecutionConfig config = merger.merge(plan, scenario);
        assertEquals(99L, config.controllerNodeId());
        assertEquals(List.of(88L), config.workerNodeIds());
        assertEquals(List.of(3L), config.monitorTargetIds());
        assertEquals(Map.of("k", "v"), config.jmeterProperties());
    }

    @Test
    void appliesSelectedThreadGroupConfig() {
        PersistentTaskPlanRecord plan = new PersistentTaskPlanRecord(1L, "plan", "", "admin");
        plan.updateProfile("plan", "", 10L, "[11]", "[1]");

        PersistentTaskScenarioRecord scenario = new PersistentTaskScenarioRecord(1L, 100L, "scene", 0);
        scenario.updateProfile(
                "scene",
                100L,
                "{}",
                null,
                null,
                null,
                "[{\"id\":7,\"stepId\":\"thread-0\",\"stepName\":\"Login\",\"threads\":200,\"rampUp\":30,\"duration\":300,\"sortOrder\":0}]"
        );

        ExecutionConfig config = merger.merge(plan, scenario, 7L);
        assertEquals(200, config.threads());
        assertEquals(30, config.rampUp());
        assertEquals(300, config.duration());
        assertEquals(7L, config.threadGroupConfigId());
        assertEquals("thread-0", config.stepId());
        assertEquals("Login", config.stepName());
    }

    @Test
    void leavesThreadFieldsZeroWithoutSelection() {
        PersistentTaskPlanRecord plan = new PersistentTaskPlanRecord(1L, "plan", "", "admin");
        PersistentTaskScenarioRecord scenario = new PersistentTaskScenarioRecord(1L, 100L, "scene", 0);
        scenario.updateProfile(
                "scene",
                100L,
                "{}",
                null,
                null,
                null,
                "[{\"id\":7,\"stepId\":\"thread-0\",\"stepName\":\"Login\",\"threads\":200,\"rampUp\":30,\"duration\":300,\"sortOrder\":0}]"
        );

        ExecutionConfig config = merger.merge(plan, scenario, null);
        assertEquals(0, config.threads());
        assertNull(config.threadGroupConfigId());
    }
}
