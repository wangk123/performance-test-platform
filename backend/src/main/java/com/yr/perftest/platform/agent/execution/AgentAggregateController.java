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
public class AgentAggregateController {
    private final DataFacade dataFacade;

    public AgentAggregateController(DataFacade dataFacade) {
        this.dataFacade = dataFacade;
    }

    @GetMapping("/{executionId}/aggregate")
    public ApiResponse<AggregateData> aggregate(
            @PathVariable long executionId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "1000") int maxItems,
            @RequestParam(defaultValue = "1048576") long maxBytes,
            @RequestParam(defaultValue = "3000") long maxMillis
    ) {
        BoundedPage<TaskExecutionResult.AggregateRow> page = dataFacade.queryAggregateRows(
                executionId,
                cursor,
                new PageBudget(maxItems, maxBytes, maxMillis)
        );
        return ApiResponse.paged(
                UUID.randomUUID().toString(),
                AgentExceptionHandler.SCHEMA_VERSION,
                new AggregateData(page.items(), page.availability()),
                page.warnings(),
                page.truncated(),
                page.nextCursor()
        );
    }

    public record AggregateData(List<TaskExecutionResult.AggregateRow> items, Availability availability) {
    }
}
