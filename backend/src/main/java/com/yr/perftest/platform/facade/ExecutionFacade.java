package com.yr.perftest.platform.facade;

import com.yr.perftest.platform.facade.data.ExecutionPrecheckView;
import com.yr.perftest.platform.facade.data.ExecutionStartResult;
import com.yr.perftest.platform.facade.data.ExecutionStatusView;
import com.yr.perftest.platform.task.ExecutionControlService;
import com.yr.perftest.platform.task.ExecutionPrecheckService;
import com.yr.perftest.platform.task.ScenarioExecution;
import org.springframework.stereotype.Service;

/**
 * agent 面的执行控制 adapter：主体校验 + 视图映射。
 * 幂等、审计、预检语义全部由 {@link ExecutionControlService} 与 {@link ExecutionPrecheckService} 拥有，
 * UI 面与 agent 面共用同一条控制 seam。
 */
@Service
public class ExecutionFacade {
    private final FacadeGuard guard;
    private final ExecutionControlService controlService;
    private final ExecutionPrecheckService precheckService;

    public ExecutionFacade(
            FacadeGuard guard,
            ExecutionControlService controlService,
            ExecutionPrecheckService precheckService
    ) {
        this.guard = guard;
        this.controlService = controlService;
        this.precheckService = precheckService;
    }

    public ExecutionStartResult startExecution(
            long scenarioId,
            String executionName,
            Long threadGroupConfigId,
            Integer threadGroupPresetSortOrder,
            String idempotencyKey
    ) {
        return guard.requirePrincipal(() -> {
            ExecutionControlService.StartOutcome outcome = controlService.start(
                    new ExecutionControlService.StartCommand(
                            scenarioId, executionName, threadGroupConfigId, threadGroupPresetSortOrder),
                    idempotencyKey
            );
            return new ExecutionStartResult(
                    ExecutionStartResult.SCHEMA_VERSION,
                    outcome.executionId(),
                    outcome.status().name(),
                    outcome.replayed()
            );
        });
    }

    public ExecutionStatusView stopExecution(long executionId) {
        return guard.requirePrincipal(() -> {
            controlService.stop(executionId);
            return statusView(controlService.status(executionId));
        });
    }

    public ExecutionStatusView cancelExecution(long executionId) {
        return guard.requirePrincipal(() -> {
            controlService.cancel(executionId);
            return statusView(controlService.status(executionId));
        });
    }

    public ExecutionStatusView getExecutionStatus(long executionId) {
        return guard.requirePrincipal(() -> statusView(controlService.status(executionId)));
    }

    public ExecutionPrecheckView precheckExecution(
            long scenarioId,
            Long threadGroupConfigId,
            Integer threadGroupPresetSortOrder
    ) {
        return guard.requirePrincipal(() -> {
            ExecutionPrecheckService.PrecheckReport report =
                    precheckService.precheck(scenarioId, threadGroupConfigId, threadGroupPresetSortOrder);
            return new ExecutionPrecheckView(
                    ExecutionPrecheckView.SCHEMA_VERSION,
                    report.valid(),
                    report.errors(),
                    report.warnings(),
                    report.threads(),
                    report.durationSeconds(),
                    report.workerCount(),
                    report.monitorTargetCount(),
                    report.queueAhead(),
                    report.nodes().stream()
                            .map(node -> new ExecutionPrecheckView.NodeView(
                                    node.nodeId(), node.name(), node.role(), node.status(), node.message()))
                            .toList()
            );
        });
    }

    private ExecutionStatusView statusView(ScenarioExecution execution) {
        return new ExecutionStatusView(
                ExecutionStatusView.SCHEMA_VERSION,
                execution.id(),
                execution.status().name(),
                execution.createdAt(),
                execution.startedAt(),
                execution.endedAt(),
                execution.durationMs(),
                execution.errorMessage()
        );
    }
}
