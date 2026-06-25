package com.yr.perftest.platform.monitoring;

import com.yr.perftest.platform.execution.ExecutionValidationException;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRecord;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRepository;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.PersistentTaskScenarioRecord;
import com.yr.perftest.platform.task.PersistentTaskScenarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class TargetMetricsService {
    private final PrometheusQueryClient prometheusQueryClient;
    private final PersistentExecutionMonitorBindingRepository bindingRepository;
    private final PersistentMonitorTargetRepository targetRepository;
    private final PersistentScenarioExecutionRepository executionRepository;
    private final PersistentTaskScenarioRepository scenarioRepository;
    private final PersistentTaskPlanRepository planRepository;

    public TargetMetricsService(
            PrometheusQueryClient prometheusQueryClient,
            PersistentExecutionMonitorBindingRepository bindingRepository,
            PersistentMonitorTargetRepository targetRepository,
            PersistentScenarioExecutionRepository executionRepository,
            PersistentTaskScenarioRepository scenarioRepository,
            PersistentTaskPlanRepository planRepository
    ) {
        this.prometheusQueryClient = prometheusQueryClient;
        this.bindingRepository = bindingRepository;
        this.targetRepository = targetRepository;
        this.executionRepository = executionRepository;
        this.scenarioRepository = scenarioRepository;
        this.planRepository = planRepository;
    }

    @Transactional(readOnly = true)
    public TargetMetricsQueryResult querySeries(
            long executionId,
            MetricKind kind,
            List<Long> targetIds,
            String itemId,
            Integer stepSeconds
    ) {
        ExecutionContext context = loadContext(executionId);
        if (context.boundTargetIds().isEmpty()) {
            return new TargetMetricsQueryResult(kind, kind.unit(), List.of());
        }
        List<Long> filteredTargetIds = filterTargetIds(context.boundTargetIds(), targetIds);
        if (filteredTargetIds.isEmpty()) {
            return new TargetMetricsQueryResult(kind, kind.unit(), List.of());
        }
        String filter = buildFilter(context.projectId(), filteredTargetIds, kind, itemId);
        int step = stepSeconds != null ? stepSeconds : resolveStep(context.startTime(), context.endTime());
        long start = context.startTime().getEpochSecond();
        long end = context.endTime().getEpochSecond();
        if (end <= start) {
            end = Instant.now().getEpochSecond();
        }
        List<MetricSeries> merged = new ArrayList<>();
        for (int index = 0; index < kind.promqlTemplates().size(); index++) {
            String promql = formatPromql(kind, filter, index);
            List<MetricSeries> series = prometheusQueryClient.queryRange(promql, start, end, step);
            merged.addAll(annotateSeries(series, kind, index));
        }
        return new TargetMetricsQueryResult(kind, kind.unit(), merged);
    }

    private ExecutionContext loadContext(long executionId) {
        PersistentScenarioExecutionRecord execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new ExecutionValidationException("execution does not exist"));
        PersistentTaskScenarioRecord scenario = scenarioRepository.findById(execution.getScenarioId())
                .orElseThrow(() -> new ExecutionValidationException("scenario does not exist"));
        PersistentTaskPlanRecord plan = planRepository.findById(scenario.getPlanId())
                .orElseThrow(() -> new ExecutionValidationException("task plan does not exist"));
        List<Long> boundTargetIds = bindingRepository.findAllByExecutionIdOrderByIdAsc(execution.getId()).stream()
                .map(binding -> binding.getTargetId())
                .toList();
        Instant startTime = bindingRepository.findAllByExecutionIdOrderByIdAsc(execution.getId()).stream()
                .map(binding -> binding.getStartTime())
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(execution.getStartTime() != null ? execution.getStartTime() : Instant.now().minus(Duration.ofMinutes(5)));
        Instant endTime = execution.getEndTime() != null ? execution.getEndTime() : Instant.now();
        if (startTime == null) {
            startTime = endTime.minus(Duration.ofMinutes(5));
        }
        return new ExecutionContext(plan.getProjectId(), boundTargetIds, startTime, endTime);
    }

    private List<Long> filterTargetIds(List<Long> boundTargetIds, List<Long> requested) {
        if (requested == null || requested.isEmpty()) {
            return boundTargetIds;
        }
        return requested.stream().filter(boundTargetIds::contains).toList();
    }

    private String buildFilter(long projectId, List<Long> targetIds, MetricKind kind, String itemId) {
        String targetFilter = targetIds.stream().map(String::valueOf).collect(Collectors.joining("|"));
        StringBuilder builder = new StringBuilder();
        builder.append("project_id=\"").append(projectId).append("\"");
        builder.append(",target_id=~\"").append(targetFilter).append("\"");
        if (kind.serverMetric()) {
            builder.append(",target_kind=\"SERVER_RESOURCE\"");
        } else {
            builder.append(",target_kind=\"JAVA_JMX_AGENT\"");
            if (itemId != null && !itemId.isBlank()) {
                builder.append(",item_id=\"").append(itemId).append("\"");
            }
        }
        return builder.toString();
    }

    private String formatPromql(MetricKind kind, String filter, int templateIndex) {
        return kind.promqlTemplates().get(templateIndex).replace("%s", filter);
    }

    private List<MetricSeries> annotateSeries(List<MetricSeries> series, MetricKind kind, int templateIndex) {
        List<MetricSeries> annotated = new ArrayList<>();
        for (MetricSeries item : series) {
            String displayName = resolveAnnotatedName(kind, templateIndex, item);
            int yAxisIndex = resolveYAxisIndex(kind, templateIndex);
            annotated.add(new MetricSeries(displayName, item.labels(), item.points(), yAxisIndex));
        }
        return annotated;
    }

    private String resolveAnnotatedName(MetricKind kind, int templateIndex, MetricSeries series) {
        Map<String, String> labels = series.labels();
        return switch (kind) {
            case SERVER_LOAD -> switch (templateIndex) {
                case 1 -> prefixServer(labels, "Load 5m");
                case 2 -> prefixServer(labels, "Load 15m");
                default -> prefixServer(labels, "Load 1m");
            };
            case SERVER_NET -> switch (templateIndex) {
                case 1 -> prefixServer(labels, "Transmit");
                default -> prefixServer(labels, "Receive");
            };
            case SERVER_TCP -> switch (templateIndex) {
                case 1 -> prefixServer(labels, "Retrans/s");
                default -> prefixServer(labels, "Connections");
            };
            case JVM_MEMORY_BYTES -> labels.getOrDefault("area", "memory").equals("nonheap")
                    ? "Non-Heap"
                    : "Heap";
            case JVM_GC -> labels.getOrDefault("__name__", "").contains("_sum")
                    ? labels.getOrDefault("gc", "GC") + " time/s"
                    : labels.getOrDefault("gc", "GC") + " count/s";
            case JVM_THREADS -> {
                String metric = labels.getOrDefault("__name__", "").toLowerCase();
                String threadLabel = metric.contains("daemon") ? "Daemon"
                        : metric.contains("peak") ? "Peak" : "Current";
                yield labels.getOrDefault("service", labels.getOrDefault("item_name", threadLabel)) + " · " + threadLabel;
            }
            default -> prefixServer(labels, series.displayName());
        };
    }

    private String prefixServer(Map<String, String> labels, String metricLabel) {
        String server = labels.getOrDefault("server", labels.getOrDefault("instance", ""));
        return server.isBlank() ? metricLabel : server + " · " + metricLabel;
    }

    private int resolveYAxisIndex(MetricKind kind, int templateIndex) {
        if (kind == MetricKind.SERVER_TCP && templateIndex == 1) {
            return 1;
        }
        if (kind == MetricKind.JVM_GC && templateIndex == 1) {
            return 1;
        }
        return 0;
    }

    private int resolveStep(Instant start, Instant end) {
        long durationSeconds = Duration.between(start, end).getSeconds();
        return durationSeconds < 600 ? 5 : 15;
    }

    private record ExecutionContext(long projectId, List<Long> boundTargetIds, Instant startTime, Instant endTime) {
    }
}
