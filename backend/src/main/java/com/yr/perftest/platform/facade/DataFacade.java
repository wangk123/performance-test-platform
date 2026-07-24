package com.yr.perftest.platform.facade;

import com.yr.perftest.platform.facade.data.ExecutionSummary;
import com.yr.perftest.platform.execution.TaskExecutionResult;
import com.yr.perftest.platform.task.ScenarioExecution;
import com.yr.perftest.platform.task.ScenarioExecutionService;
import org.springframework.stereotype.Service;

@Service
public class DataFacade {
    private final FacadeGuard guard;
    private final ScenarioExecutionService scenarioExecutionService;

    public DataFacade(FacadeGuard guard, ScenarioExecutionService scenarioExecutionService) {
        this.guard = guard;
        this.scenarioExecutionService = scenarioExecutionService;
    }

    public ExecutionSummary getExecutionSummary(long executionId) {
        return guard.requirePrincipal(() -> {
            ScenarioExecution execution = scenarioExecutionService.getExecution(executionId);
            TaskExecutionResult result = scenarioExecutionService.getResult(executionId);
            TaskExecutionResult.Summary summary = result.summary() == null
                    ? TaskExecutionResult.empty().summary()
                    : result.summary();
            return new ExecutionSummary(
                    ExecutionSummary.SCHEMA_VERSION,
                    execution.id(),
                    execution.scenarioId(),
                    execution.planId(),
                    execution.projectId(),
                    execution.scenarioName(),
                    execution.executionName(),
                    execution.status(),
                    execution.createdAt(),
                    execution.startedAt(),
                    execution.endedAt(),
                    execution.durationMs(),
                    summary.samples(),
                    summary.throughput(),
                    summary.avgRt(),
                    summary.p95(),
                    summary.errorRate()
            );
        });
    }
}
