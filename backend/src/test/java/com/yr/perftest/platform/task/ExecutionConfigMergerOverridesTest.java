package com.yr.perftest.platform.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.execution.ExecutionConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionConfigMergerOverridesTest {
    private final ExecutionConfigMerger merger = new ExecutionConfigMerger(
            new TaskJsonSupport(new ObjectMapper()),
            new ScenarioThreadGroupConfigSupport(new ObjectMapper(), null)
    );

    private PersistentTaskPlanRecord plan() {
        PersistentTaskPlanRecord plan = new PersistentTaskPlanRecord(1L, "plan", "", "admin");
        plan.updateProfile("plan", "", 10L, "[11]", "[1]");
        return plan;
    }

    private PersistentTaskScenarioRecord scenario() {
        PersistentTaskScenarioRecord scenario = new PersistentTaskScenarioRecord(1L, 100L, "scene", 0);
        scenario.updateProfile(
                "scene",
                100L,
                "{}",
                null,
                null,
                null,
                "[{\"id\":9,\"stepId\":\"thread-0\",\"stepName\":\"TG1\",\"threads\":100,\"rampUp\":30,\"duration\":600,\"sortOrder\":1}]"
        );
        return scenario;
    }

    @Test
    void overridesReplacePresetThreadValues() {
        ExecutionConfig config = merger.merge(plan(), scenario(), null, 1, new ThreadGroupOverrides(300, 60, 900));
        assertThat(config.threads()).isEqualTo(300);
        assertThat(config.rampUp()).isEqualTo(60);
        assertThat(config.duration()).isEqualTo(900);
    }

    @Test
    void nullOverridesKeepPresetValues() {
        ExecutionConfig config = merger.merge(plan(), scenario(), null, 1, null);
        assertThat(config.threads()).isEqualTo(100);
    }

    @Test
    void partialOverridesOnlyReplaceGiven() {
        ExecutionConfig config = merger.merge(plan(), scenario(), null, 1, new ThreadGroupOverrides(250, null, null));
        assertThat(config.threads()).isEqualTo(250);
        assertThat(config.rampUp()).isEqualTo(30);
    }
}
