package com.yr.perftest.platform.task;

import com.yr.perftest.platform.execution.ExecutionConflictException;
import com.yr.perftest.platform.execution.ExecutionStatus;
import com.yr.perftest.platform.execution.ExecutionValidationException;
import com.yr.perftest.platform.execution.IdempotencyService;
import com.yr.perftest.platform.execution.RequestHashing;
import com.yr.perftest.platform.governance.ExecutionAuditService;
import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.identity.MachinePrincipal;
import com.yr.perftest.platform.identity.Principal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExecutionControlService {
    private final ScenarioExecutionService scenarioExecutionService;
    private final ExecutionQueryService executionQueryService;
    private final com.yr.perftest.platform.auxscript.AuxScriptLifecycle auxScriptLifecycle;
    private final PersistentScenarioExecutionRepository executionRepository;
    private final ScenarioExecutionRuntime executionRuntime;
    private final IdempotencyService idempotencyService;
    private final ExecutionAuditService executionAuditService;
    private final com.yr.perftest.platform.task.plandoc.PlanWorkflowService planWorkflowService;

    public ExecutionControlService(
            ScenarioExecutionService scenarioExecutionService,
            ExecutionQueryService executionQueryService,
            com.yr.perftest.platform.auxscript.AuxScriptLifecycle auxScriptLifecycle,
            PersistentScenarioExecutionRepository executionRepository,
            ScenarioExecutionRuntime executionRuntime,
            IdempotencyService idempotencyService,
            ExecutionAuditService executionAuditService,
            com.yr.perftest.platform.task.plandoc.PlanWorkflowService planWorkflowService
    ) {
        this.scenarioExecutionService = scenarioExecutionService;
        this.executionQueryService = executionQueryService;
        this.auxScriptLifecycle = auxScriptLifecycle;
        this.executionRepository = executionRepository;
        this.executionRuntime = executionRuntime;
        this.idempotencyService = idempotencyService;
        this.executionAuditService = executionAuditService;
        this.planWorkflowService = planWorkflowService;
    }

    @Transactional
    public StartOutcome start(StartCommand command, String idempotencyKey) {
        long planId = planWorkflowService.assertExecutionAllowed(command.scenarioId()); // 门禁+首执行环境检查（设计 §10.1/§10.2）
        String requestHash = RequestHashing.sha256(
                hashField(command.scenarioId())
                        + hashField(command.executionName())
                        + hashField(command.threadGroupConfigId())
                        + hashField(command.threadGroupPresetSortOrder()));
        IdempotencyService.IdempotentExecution result = idempotencyService.execute(
                idempotencyKey,
                requestHash,
                () -> scenarioExecutionService.triggerExecution(
                        command.scenarioId(),
                        command.executionName(),
                        command.threadGroupConfigId(),
                        command.threadGroupPresetSortOrder()
                )
        );
        ScenarioExecution execution = executionQueryService.getExecution(result.executionId());
        planWorkflowService.onExecutionStarted(planId); // 置 EXECUTION/RUNNING + 报告作废
        audit(result.executionId(), "START", result.replayed());
        return new StartOutcome(execution.id(), execution.status(), result.replayed());
    }

    @Transactional
    public void stop(long executionId) {
        PersistentScenarioExecutionRecord execution = requireExecution(executionId);
        if (isFinished(execution.getStatus())) {
            throw new ExecutionConflictException("execution already finished");
        }
        if (execution.getStatus() == ExecutionStatus.STOPPING) {
            return;
        }
        scenarioExecutionService.stopExecution(executionId);
        audit(executionId, "STOP", false);
    }

    @Transactional
    public void cancel(long executionId) {
        PersistentScenarioExecutionRecord execution = requireExecution(executionId);
        if (isFinished(execution.getStatus())) {
            throw new ExecutionConflictException("execution already finished");
        }
        executionRuntime.requestStop(executionId);
        execution.markCancelled();
        audit(executionId, "CANCEL", false);
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        auxScriptLifecycle.afterExecutionFinished(executionId);
                    }
                });
    }

    @Transactional(readOnly = true)
    public ScenarioExecution status(long executionId) {
        return executionQueryService.getExecution(executionId);
    }

    private PersistentScenarioExecutionRecord requireExecution(long executionId) {
        return executionRepository.findById(executionId)
                .orElseThrow(() -> new ExecutionValidationException("execution does not exist"));
    }

    private void audit(long executionId, String action, boolean replayed) {
        Principal principal = currentPrincipalOrNull();
        if (principal == null) {
            return;
        }
        if (principal instanceof HumanPrincipal human) {
            executionAuditService.record(executionId, action, replayed, "HUMAN", human.username());
        } else if (principal instanceof MachinePrincipal machine) {
            executionAuditService.record(executionId, action, replayed, "MACHINE", Long.toString(machine.apiKeyId()));
        }
    }

    private Principal currentPrincipalOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Principal principal)) {
            return null;
        }
        return principal;
    }

    private boolean isFinished(ExecutionStatus status) {
        return status == ExecutionStatus.SUCCESS
                || status == ExecutionStatus.FAILED
                || status == ExecutionStatus.CANCELLED
                || status == ExecutionStatus.INTERRUPTED;
    }

    private static String hashField(Object value) {
        if (value == null) {
            return "-;";
        }
        String text = String.valueOf(value);
        return text.length() + ":" + text + ";";
    }

    public record StartCommand(
            long scenarioId,
            String executionName,
            Long threadGroupConfigId,
            Integer threadGroupPresetSortOrder
    ) {
    }

    public record StartOutcome(long executionId, ExecutionStatus status, boolean replayed) {
    }
}
