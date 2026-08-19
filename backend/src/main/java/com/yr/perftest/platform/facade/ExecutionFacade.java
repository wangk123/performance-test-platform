package com.yr.perftest.platform.facade;

import com.yr.perftest.platform.facade.data.ExecutionPrecheckView;
import com.yr.perftest.platform.facade.data.ExecutionStartResult;
import com.yr.perftest.platform.facade.data.ExecutionStatusView;
import com.yr.perftest.platform.governance.ExecutionAuditService;
import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.identity.MachinePrincipal;
import com.yr.perftest.platform.identity.Principal;
import com.yr.perftest.platform.task.ExecutionControlService;
import com.yr.perftest.platform.task.ExecutionPrecheckService;
import com.yr.perftest.platform.task.ScenarioExecution;
import org.springframework.stereotype.Service;

@Service
public class ExecutionFacade {
    private final FacadeGuard guard;
    private final ExecutionControlService controlService;
    private final ExecutionPrecheckService precheckService;
    private final ExecutionAuditService executionAuditService;

    public ExecutionFacade(
            FacadeGuard guard,
            ExecutionControlService controlService,
            ExecutionPrecheckService precheckService,
            ExecutionAuditService executionAuditService
    ) {
        this.guard = guard;
        this.controlService = controlService;
        this.precheckService = precheckService;
        this.executionAuditService = executionAuditService;
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
            auditExecution(outcome.executionId(), "START", outcome.replayed());
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
            auditExecution(executionId, "STOP", false);
            return statusView(controlService.status(executionId));
        });
    }

    public ExecutionStatusView cancelExecution(long executionId) {
        return guard.requirePrincipal(() -> {
            controlService.cancel(executionId);
            auditExecution(executionId, "CANCEL", false);
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

    private void auditExecution(long executionId, String action, boolean replayed) {
        Principal principal = guard.context().principal();
        if (principal instanceof HumanPrincipal human) {
            executionAuditService.record(executionId, action, replayed, "HUMAN", human.username());
        } else if (principal instanceof MachinePrincipal machine) {
            executionAuditService.record(executionId, action, replayed, "MACHINE", Long.toString(machine.apiKeyId()));
        }
    }
}
