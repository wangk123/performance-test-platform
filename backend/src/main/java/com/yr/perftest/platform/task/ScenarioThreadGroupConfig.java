package com.yr.perftest.platform.task;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScenarioThreadGroupConfig(
        long id,
        String stepId,
        String stepName,
        int threads,
        int rampUp,
        int duration,
        int sortOrder,
        ThreadGroupConfigSummary latestSummary
) {
    public ScenarioThreadGroupConfig withoutSummary() {
        return new ScenarioThreadGroupConfig(id, stepId, stepName, threads, rampUp, duration, sortOrder, null);
    }
}
