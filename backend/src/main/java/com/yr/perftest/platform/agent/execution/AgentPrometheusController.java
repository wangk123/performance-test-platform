package com.yr.perftest.platform.agent.execution;

import com.yr.perftest.platform.agent.AgentExceptionHandler;
import com.yr.perftest.platform.agent.contract.ApiResponse;
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
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/agent/executions")
public class AgentPrometheusController {
    private final DataFacade dataFacade;

    public AgentPrometheusController(DataFacade dataFacade) {
        this.dataFacade = dataFacade;
    }

    @GetMapping("/{executionId}/prometheus")
    public ApiResponse<PrometheusData> prometheus(
            @PathVariable long executionId,
            @RequestParam(required = false) String metricSelector,
            @RequestParam(required = false) String metric,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam int step,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "1000") int maxItems,
            @RequestParam(defaultValue = "1048576") long maxBytes,
            @RequestParam(defaultValue = "3000") long maxMillis
    ) {
        BoundedPage<?> page = dataFacade.queryPrometheus(
                executionId,
                resolveMetricSelector(metricSelector, metric),
                parseInstant(from),
                parseInstant(to),
                step,
                cursor,
                new PageBudget(maxItems, maxBytes, maxMillis)
        );
        return ApiResponse.paged(
                UUID.randomUUID().toString(),
                AgentExceptionHandler.SCHEMA_VERSION,
                new PrometheusData(page.items(), page.availability()),
                page.warnings(),
                page.truncated(),
                page.nextCursor()
        );
    }

    private String resolveMetricSelector(String metricSelector, String metric) {
        if (metricSelector != null && !metricSelector.isBlank()) {
            if (metric != null && !metric.isBlank() && !metricSelector.equals(metric)) {
                throw new IllegalArgumentException("prometheus metric selectors conflict");
            }
            return metricSelector;
        }
        return metric;
    }

    private Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            try {
                return Instant.ofEpochSecond(Long.parseLong(value));
            } catch (NumberFormatException numberFormatException) {
                throw new IllegalArgumentException("prometheus time is invalid", numberFormatException);
            }
        }
    }

    public record PrometheusData(List<?> items, Availability availability) {
    }
}
