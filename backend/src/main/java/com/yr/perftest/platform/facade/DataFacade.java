package com.yr.perftest.platform.facade;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.execution.TaskExecutionResult;
import com.yr.perftest.platform.execution.ExecutionValidationException;
import com.yr.perftest.platform.execution.TaskMetricSeries;
import com.yr.perftest.platform.execution.aggregate.MetricTick;
import com.yr.perftest.platform.facade.data.ExecutionSummary;
import com.yr.perftest.platform.facade.data.PrometheusMetricPoint;
import com.yr.perftest.platform.facade.query.Availability;
import com.yr.perftest.platform.facade.query.BoundedPage;
import com.yr.perftest.platform.facade.query.CursorCodec;
import com.yr.perftest.platform.facade.query.PageBudget;
import com.yr.perftest.platform.facade.query.PrometheusBoundedQuery;
import com.yr.perftest.platform.task.ScenarioExecution;
import com.yr.perftest.platform.task.ScenarioExecutionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class DataFacade {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final CursorCodec CURSOR_CODEC = new CursorCodec();

    private final FacadeGuard guard;
    private final ScenarioExecutionService scenarioExecutionService;
    private final PrometheusBoundedQuery prometheusBoundedQuery;

    public DataFacade(FacadeGuard guard, ScenarioExecutionService scenarioExecutionService) {
        this(guard, scenarioExecutionService, null);
    }

    @Autowired
    public DataFacade(
            FacadeGuard guard,
            ScenarioExecutionService scenarioExecutionService,
            PrometheusBoundedQuery prometheusBoundedQuery
    ) {
        this.guard = guard;
        this.scenarioExecutionService = scenarioExecutionService;
        this.prometheusBoundedQuery = prometheusBoundedQuery;
    }

    public ExecutionSummary getExecutionSummary(long executionId) {
        return guard.requirePrincipal(() -> {
            ScenarioExecution execution = scenarioExecutionService.getExecution(executionId);
            TaskExecutionResult result = scenarioExecutionService.getResult(executionId);
            TaskExecutionResult.Summary summary = result.summary() == null
                    ? TaskExecutionResult.empty().summary()
                    : result.summary();
            return new ExecutionSummary(
                    ExecutionSummary.SCHEMA_VERSION,
                    execution.id(),
                    execution.scenarioId(),
                    execution.planId(),
                    execution.projectId(),
                    execution.scenarioName(),
                    execution.executionName(),
                    execution.status(),
                    execution.createdAt(),
                    execution.startedAt(),
                    execution.endedAt(),
                    execution.durationMs(),
                    summary.samples(),
                    summary.throughput(),
                    summary.avgRt(),
                    summary.p95(),
                    summary.errorRate()
            );
        });
    }

    public BoundedPage<TaskExecutionResult.Sample> queryFailureSamples(
            long executionId,
            String cursor,
            PageBudget budget
    ) {
        return guard.requirePrincipal(() -> {
            budget.validate();
            scenarioExecutionService.getExecution(executionId);
            long lastId = decodeFailureSampleCursor(cursor);
            long startedAt = System.nanoTime();
            ScenarioExecutionService.FailureSampleSlice source;
            try {
                source = scenarioExecutionService.listFailureSamplesAfter(
                        executionId,
                        lastId,
                        budget.maxItems() == Integer.MAX_VALUE ? Integer.MAX_VALUE : budget.maxItems() + 1
                );
            } catch (ExecutionValidationException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new DataSourceUnavailableException("failure sample source is unavailable", exception);
            }

            List<TaskExecutionResult.Sample> items = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            long serializedBytes = 0;
            boolean truncated = false;
            for (TaskExecutionResult.Sample sample : source.samples()) {
                if (items.size() >= budget.maxItems()) {
                    warnings.add("budget:items");
                    truncated = true;
                    break;
                }
                long itemBytes = serializedSize(sample);
                if (serializedBytes + itemBytes > budget.maxBytes()) {
                    warnings.add("budget:bytes");
                    truncated = true;
                    break;
                }
                if (TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt) >= budget.maxMillis()) {
                    warnings.add("budget:millis");
                    truncated = true;
                    break;
                }
                items.add(sample);
                serializedBytes += itemBytes;
            }

            long pageLastId = items.isEmpty() ? lastId : items.get(items.size() - 1).id();
            String nextCursor = truncated ? CURSOR_CODEC.encode(Long.toString(pageLastId)) : null;
            String sourceRef = items.isEmpty()
                    ? source.sourceName()
                    : source.sourceName() + "#" + items.get(0).id() + "-" + pageLastId;
            boolean present = !source.samples().isEmpty();
            Availability availability = new Availability(
                    present,
                    null,
                    null,
                    null,
                    truncated,
                    sourceRef,
                    present ? null : Availability.MissingReason.NO_DATA
            );
            return new BoundedPage<>(List.copyOf(items), truncated, nextCursor, List.copyOf(warnings), availability);
        });
    }

    public BoundedPage<TaskExecutionResult.AggregateRow> queryAggregateRows(
            long executionId,
            String cursor,
            PageBudget budget
    ) {
        return guard.requirePrincipal(() -> {
            budget.validate();
            scenarioExecutionService.getExecution(executionId);
            TaskExecutionResult result;
            try {
                result = scenarioExecutionService.getResult(executionId);
            } catch (ExecutionValidationException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new DataSourceUnavailableException("aggregate source is unavailable", exception);
            }

            List<TaskExecutionResult.AggregateRow> rows = result.aggregateRows() == null
                    ? List.of()
                    : result.aggregateRows();
            int afterIndex = decodeAggregateCursor(cursor);
            if (afterIndex >= rows.size()) {
                throw new IllegalArgumentException("aggregate cursor is invalid");
            }

            int firstIndex = afterIndex + 1;
            long startedAt = System.nanoTime();
            List<TaskExecutionResult.AggregateRow> items = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            long serializedBytes = 0;
            boolean truncated = false;
            for (int index = firstIndex; index < rows.size(); index++) {
                if (items.size() >= budget.maxItems()) {
                    warnings.add("budget:items");
                    truncated = true;
                    break;
                }
                TaskExecutionResult.AggregateRow row = rows.get(index);
                long itemBytes = serializedSize(row);
                if (serializedBytes + itemBytes > budget.maxBytes()) {
                    warnings.add("budget:bytes");
                    truncated = true;
                    break;
                }
                if (TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt) >= budget.maxMillis()) {
                    warnings.add("budget:millis");
                    truncated = true;
                    break;
                }
                items.add(row);
                serializedBytes += itemBytes;
            }

            int pageLastIndex = items.isEmpty() ? afterIndex : firstIndex + items.size() - 1;
            String nextCursor = truncated ? CURSOR_CODEC.encode(Integer.toString(pageLastIndex)) : null;
            String sourceRef = items.isEmpty()
                    ? "aggregate"
                    : "aggregate#" + firstIndex + "-" + pageLastIndex;
            boolean present = !rows.isEmpty();
            Availability availability = new Availability(
                    present,
                    null,
                    null,
                    null,
                    truncated,
                    sourceRef,
                    present ? null : Availability.MissingReason.NO_DATA
            );
            return new BoundedPage<>(List.copyOf(items), truncated, nextCursor, List.copyOf(warnings), availability);
        });
    }

    public BoundedPage<MetricTick> queryMetricSeries(
            long executionId,
            Instant from,
            Instant to,
            String granularity,
            String cursor,
            PageBudget budget
    ) {
        return guard.requirePrincipal(() -> {
            budget.validate();
            if (from.isAfter(to)) {
                throw new IllegalArgumentException("metric series time range is invalid");
            }
            scenarioExecutionService.getExecution(executionId);
            TaskMetricSeries source;
            try {
                source = scenarioExecutionService.getMonitoring(executionId);
            } catch (ExecutionValidationException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new DataSourceUnavailableException("metric series source is unavailable", exception);
            }

            long lastBucketTimeMs = decodeMetricSeriesCursor(cursor);
            long fromMs = from.toEpochMilli();
            long toMs = to.toEpochMilli();
            List<MetricTick> ticks = source.ticks() == null
                    ? List.of()
                    : source.ticks().stream()
                    .filter(tick -> tick.bucketTimeMs() >= fromMs)
                    .filter(tick -> tick.bucketTimeMs() <= toMs)
                    .filter(tick -> tick.bucketTimeMs() > lastBucketTimeMs)
                    .sorted(Comparator.comparingLong(MetricTick::bucketTimeMs))
                    .toList();

            long startedAt = System.nanoTime();
            List<MetricTick> items = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            long serializedBytes = 0;
            boolean truncated = false;
            for (MetricTick tick : ticks) {
                if (items.size() >= budget.maxItems()) {
                    warnings.add("budget:items");
                    truncated = true;
                    break;
                }
                long itemBytes = serializedSize(tick);
                if (serializedBytes + itemBytes > budget.maxBytes()) {
                    warnings.add("budget:bytes");
                    truncated = true;
                    break;
                }
                if (TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt) >= budget.maxMillis()) {
                    warnings.add("budget:millis");
                    truncated = true;
                    break;
                }
                items.add(tick);
                serializedBytes += itemBytes;
            }

            long pageLastBucketTimeMs = items.isEmpty()
                    ? lastBucketTimeMs
                    : items.get(items.size() - 1).bucketTimeMs();
            String nextCursor = truncated
                    ? CURSOR_CODEC.encode(Long.toString(pageLastBucketTimeMs))
                    : null;
            boolean present = !items.isEmpty();
            Availability availability = new Availability(
                    present,
                    present ? Instant.ofEpochMilli(items.get(0).bucketTimeMs()) : null,
                    present ? Instant.ofEpochMilli(pageLastBucketTimeMs) : null,
                    granularity,
                    truncated,
                    present
                            ? "metric-series#" + items.get(0).bucketTimeMs() + "-" + pageLastBucketTimeMs
                            : "metric-series",
                    present ? null : Availability.MissingReason.NO_DATA
            );
            return new BoundedPage<>(List.copyOf(items), truncated, nextCursor, List.copyOf(warnings), availability);
        });
    }

    public BoundedPage<PrometheusMetricPoint> queryPrometheus(
            long executionId,
            String metricSelector,
            Instant from,
            Instant to,
            int stepSeconds,
            String cursor,
            PageBudget budget
    ) {
        return guard.requirePrincipal(() -> {
            scenarioExecutionService.getExecution(executionId);
            return prometheusBoundedQuery.query(
                    executionId,
                    metricSelector,
                    from,
                    to,
                    stepSeconds,
                    cursor,
                    budget
            );
        });
    }

    private long decodeFailureSampleCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            long lastId = Long.parseLong(CURSOR_CODEC.decode(cursor));
            if (lastId < 0) {
                throw new IllegalArgumentException("failure sample cursor is invalid");
            }
            return lastId;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("failure sample cursor is invalid", exception);
        }
    }

    private int decodeAggregateCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return -1;
        }
        try {
            int index = Integer.parseInt(CURSOR_CODEC.decode(cursor));
            if (index < -1) {
                throw new IllegalArgumentException("aggregate cursor is invalid");
            }
            return index;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("aggregate cursor is invalid", exception);
        }
    }

    private long decodeMetricSeriesCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return Long.MIN_VALUE;
        }
        try {
            return Long.parseLong(CURSOR_CODEC.decode(cursor));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("metric series cursor is invalid", exception);
        }
    }

    private long serializedSize(TaskExecutionResult.Sample sample) {
        try {
            return OBJECT_MAPPER.writeValueAsBytes(sample).length;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failure sample cannot be serialized", exception);
        }
    }

    private long serializedSize(TaskExecutionResult.AggregateRow row) {
        try {
            return OBJECT_MAPPER.writeValueAsBytes(row).length;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("aggregate row cannot be serialized", exception);
        }
    }

    private long serializedSize(MetricTick tick) {
        try {
            return OBJECT_MAPPER.writeValueAsBytes(tick).length;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("metric tick cannot be serialized", exception);
        }
    }
}
