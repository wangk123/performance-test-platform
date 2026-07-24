package com.yr.perftest.platform.agent.execution;

import com.yr.perftest.platform.agent.AgentExceptionHandler;
import com.yr.perftest.platform.agent.contract.ApiResponse;
import com.yr.perftest.platform.execution.TaskExecutionResult;
import com.yr.perftest.platform.facade.DataFacade;
import com.yr.perftest.platform.facade.query.Availability;
import com.yr.perftest.platform.facade.query.BoundedPage;
import com.yr.perftest.platform.facade.query.PageBudget;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/agent/executions")
public class AgentFailureSampleController {
    private final DataFacade dataFacade;

    public AgentFailureSampleController(DataFacade dataFacade) {
        this.dataFacade = dataFacade;
    }

    @GetMapping("/{executionId}/failure-samples")
    public ApiResponse<FailureSampleData> failureSamples(
            @PathVariable long executionId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "1000") int maxItems,
            @RequestParam(defaultValue = "1048576") long maxBytes,
            @RequestParam(defaultValue = "3000") long maxMillis
    ) {
        BoundedPage<TaskExecutionResult.Sample> page = dataFacade.queryFailureSamples(
                executionId,
                cursor,
                new PageBudget(maxItems, maxBytes, maxMillis)
        );
        return ApiResponse.paged(
                UUID.randomUUID().toString(),
                AgentExceptionHandler.SCHEMA_VERSION,
                new FailureSampleData(page.items(), page.availability()),
                page.warnings(),
                page.truncated(),
                page.nextCursor()
        );
    }

    public record FailureSampleData(List<TaskExecutionResult.Sample> items, Availability availability) {
    }
}
