package com.yr.perftest.platform.agent.execution;

import com.yr.perftest.platform.agent.AgentExceptionHandler;
import com.yr.perftest.platform.agent.contract.ApiResponse;
import com.yr.perftest.platform.facade.DataFacade;
import com.yr.perftest.platform.facade.data.ExecutionSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/agent/executions")
public class AgentExecutionController {
    private final DataFacade dataFacade;

    public AgentExecutionController(DataFacade dataFacade) {
        this.dataFacade = dataFacade;
    }

    @GetMapping("/{executionId}/summary")
    public ApiResponse<ExecutionSummary> summary(@PathVariable long executionId) {
        ExecutionSummary summary = dataFacade.getExecutionSummary(executionId);
        return ApiResponse.success(UUID.randomUUID().toString(), AgentExceptionHandler.SCHEMA_VERSION, summary);
    }
}
