package com.yr.perftest.platform.task;

import com.yr.perftest.platform.execution.ExecutionConflictException;
import com.yr.perftest.platform.execution.ExecutionStatus;
import com.yr.perftest.platform.execution.ExecutionValidationException;
import com.yr.perftest.platform.execution.IdempotencyService;
import com.yr.perftest.platform.execution.RequestHashing;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExecutionControlService {
    private final ScenarioExecutionService scenarioExecutionService;
    private final PersistentScenarioExecutionRepository executionRepository;
    private final ScenarioExecutionRuntime executionRuntime;
    private final IdempotencyService idempotencyService;

    public ExecutionControlService(
            ScenarioExecutionService scenarioExecutionService,
            PersistentScenarioExecutionRepository executionRepository,
            ScenarioExecutionRuntime executionRuntime,
            IdempotencyService idempotencyService
    ) {
        this.scenarioExecutionService = scenarioExecutionService;
        this.executionRepository = executionRepository;
        this.executionRuntime = executionRuntime;
        this.idempotencyService = idempotencyService;
    }

    @Transactional
    public StartOutcome start(StartCommand command, String idempotencyKey) {
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
                ).id()
        );
        ScenarioExecution execution = scenarioExecutionService.getExecution(result.executionId());
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
    }

    @Transactional
    public void cancel(long executionId) {
        PersistentScenarioExecutionRecord execution = requireExecution(executionId);
        if (isFinished(execution.getStatus())) {
            throw new ExecutionConflictException("execution already finished");
        }
        executionRuntime.requestStop(executionId);
        execution.markCancelled();
    }

    @Transactional(readOnly = true)
    public ScenarioExecution status(long executionId) {
        return scenarioExecutionService.getExecution(executionId);
    }

    private PersistentScenarioExecutionRecord requireExecution(long executionId) {
        return executionRepository.findById(executionId)
                .orElseThrow(() -> new ExecutionValidationException("execution does not exist"));
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
