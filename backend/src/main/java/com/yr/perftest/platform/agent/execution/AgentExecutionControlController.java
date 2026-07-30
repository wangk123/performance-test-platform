package com.yr.perftest.platform.agent.execution;

import com.yr.perftest.platform.agent.AgentExceptionHandler;
import com.yr.perftest.platform.agent.contract.ApiResponse;
import com.yr.perftest.platform.facade.ExecutionFacade;
import com.yr.perftest.platform.facade.data.ExecutionPrecheckView;
import com.yr.perftest.platform.facade.data.ExecutionStartResult;
import com.yr.perftest.platform.facade.data.ExecutionStatusView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/agent")
public class AgentExecutionControlController {
    private final ExecutionFacade executionFacade;

    public AgentExecutionControlController(ExecutionFacade executionFacade) {
        this.executionFacade = executionFacade;
    }

    @PostMapping("/scenarios/{scenarioId}/executions")
    public ApiResponse<ExecutionStartResult> start(
            @PathVariable long scenarioId,
            @RequestBody(required = false) StartExecutionRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        ExecutionStartResult result = executionFacade.startExecution(
                scenarioId,
                request == null ? null : request.executionName(),
                request == null ? null : request.threadGroupConfigId(),
                request == null ? null : request.threadGroupPresetSortOrder(),
                idempotencyKey
        );
        return ApiResponse.success(UUID.randomUUID().toString(), AgentExceptionHandler.SCHEMA_VERSION, result);
    }

    @PostMapping("/scenarios/{scenarioId}/precheck")
    public ApiResponse<ExecutionPrecheckView> precheck(
            @PathVariable long scenarioId,
            @RequestBody(required = false) PrecheckRequest request
    ) {
        ExecutionPrecheckView view = executionFacade.precheckExecution(
                scenarioId,
                request == null ? null : request.threadGroupConfigId(),
                request == null ? null : request.threadGroupPresetSortOrder()
        );
        return ApiResponse.success(UUID.randomUUID().toString(), AgentExceptionHandler.SCHEMA_VERSION, view);
    }

    @PostMapping("/executions/{executionId}/stop")
    public ApiResponse<ExecutionStatusView> stop(@PathVariable long executionId) {
        return ApiResponse.success(
                UUID.randomUUID().toString(),
                AgentExceptionHandler.SCHEMA_VERSION,
                executionFacade.stopExecution(executionId)
        );
    }

    @PostMapping("/executions/{executionId}/cancel")
    public ApiResponse<ExecutionStatusView> cancel(@PathVariable long executionId) {
        return ApiResponse.success(
                UUID.randomUUID().toString(),
                AgentExceptionHandler.SCHEMA_VERSION,
                executionFacade.cancelExecution(executionId)
        );
    }

    @GetMapping("/executions/{executionId}/status")
    public ApiResponse<ExecutionStatusView> status(@PathVariable long executionId) {
        return ApiResponse.success(
                UUID.randomUUID().toString(),
                AgentExceptionHandler.SCHEMA_VERSION,
                executionFacade.getExecutionStatus(executionId)
        );
    }

    public record StartExecutionRequest(
            String executionName,
            Long threadGroupConfigId,
            Integer threadGroupPresetSortOrder
    ) {
    }

    public record PrecheckRequest(Long threadGroupConfigId, Integer threadGroupPresetSortOrder) {
    }
}
