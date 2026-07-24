package com.yr.perftest.platform.agent.execution;

import com.yr.perftest.platform.agent.AgentExceptionHandler;
import com.yr.perftest.platform.agent.contract.ApiResponse;
import com.yr.perftest.platform.execution.aggregate.MetricTick;
import com.yr.perftest.platform.facade.DataFacade;
import com.yr.perftest.platform.facade.query.Availability;
import com.yr.perftest.platform.facade.query.BoundedPage;
import com.yr.perftest.platform.facade.query.PageBudget;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/agent/executions")
public class AgentMetricSeriesController {
    private final DataFacade dataFacade;

    public AgentMetricSeriesController(DataFacade dataFacade) {
        this.dataFacade = dataFacade;
    }

    @GetMapping("/{executionId}/metrics/series")
    public ApiResponse<MetricSeriesData> metricSeries(
            @PathVariable long executionId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(defaultValue = "1s") String granularity,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "1000") int maxItems,
            @RequestParam(defaultValue = "1048576") long maxBytes,
            @RequestParam(defaultValue = "3000") long maxMillis
    ) {
        BoundedPage<MetricTick> page = dataFacade.queryMetricSeries(
                executionId,
                from,
                to,
                granularity,
                cursor,
                new PageBudget(maxItems, maxBytes, maxMillis)
        );
        return ApiResponse.paged(
                UUID.randomUUID().toString(),
                AgentExceptionHandler.SCHEMA_VERSION,
                new MetricSeriesData(page.items(), page.availability()),
                page.warnings(),
                page.truncated(),
                page.nextCursor()
        );
    }

    public record MetricSeriesData(List<MetricTick> items, Availability availability) {
    }
}
