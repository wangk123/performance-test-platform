package com.yr.perftest.platform.analysis;

import com.yr.perftest.platform.execution.TaskExecutionResult;
import com.yr.perftest.platform.execution.aggregate.MetricTick;
import com.yr.perftest.platform.facade.DataFacade;
import com.yr.perftest.platform.facade.DataSourceUnavailableException;
import com.yr.perftest.platform.facade.data.ExecutionSummary;
import com.yr.perftest.platform.facade.data.PrometheusMetricPoint;
import com.yr.perftest.platform.facade.query.Availability;
import com.yr.perftest.platform.facade.query.BoundedPage;
import com.yr.perftest.platform.facade.query.PageBudget;
import com.yr.perftest.platform.monitoring.MetricKind;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Service
public class AnalysisService {
    public static final String SCHEMA_VERSION = "1";
    private static final List<String> ALL_KINDS = List.of("trend", "anomaly", "error-cluster", "resource-saturation");
    private static final String DEFAULT_METRIC_SELECTOR = "SERVER_CPU";
    private static final int PROMETHEUS_STEP_SECONDS = 15;
    private static final double SATURATION_THRESHOLD = 0.9;
    private static final int MIN_SUSTAINED_POINTS = 2;

    private final DataFacade dataFacade;

    public AnalysisService(DataFacade dataFacade) {
        this.dataFacade = dataFacade;
    }

    public AnalysisReport analyze(long executionId, Instant from, Instant to, List<String> kinds, String metricSelector) {
        ExecutionSummary summary = dataFacade.getExecutionSummary(executionId);
        Instant effectiveFrom = from != null
                ? from
                : summary.startedAt() != null ? summary.startedAt() : summary.createdAt();
        Instant effectiveTo = to != null
                ? to
                : summary.endedAt() != null ? summary.endedAt() : Instant.now();
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new IllegalArgumentException("analysis time range is invalid");
        }
        List<String> selected = kinds == null || kinds.isEmpty() ? ALL_KINDS : kinds;
        String selector = metricSelector == null || metricSelector.isBlank()
                ? DEFAULT_METRIC_SELECTOR
                : metricSelector;
        try {
            MetricKind.valueOf(selector);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("analysis metric selector is invalid", exception);
        }
        PageBudget budget = PageBudget.defaults();

        SourceData<MetricTick> series = load("series", () ->
                dataFacade.queryMetricSeries(executionId, effectiveFrom, effectiveTo, "15s", null, budget));
        SourceData<TaskExecutionResult.Sample> failures = load("failure-sample", () ->
                dataFacade.queryFailureSamples(executionId, null, budget));
        SourceData<PrometheusMetricPoint> resources = load("prometheus", () ->
                dataFacade.queryPrometheus(executionId, selector, effectiveFrom, effectiveTo, PROMETHEUS_STEP_SECONDS, null, budget));

        List<AnalysisFact> facts = new ArrayList<>();
        Map<String, String> versions = new LinkedHashMap<>();
        if (selected.contains("trend")) {
            facts.add(new TrendAnalysis().analyze(series.items(), refs(series)));
            versions.put(TrendAnalysis.ALGORITHM_ID, TrendAnalysis.VERSION);
        }
        if (selected.contains("anomaly")) {
            facts.add(new AnomalyDetection().analyze(series.items(), refs(series)));
            versions.put(AnomalyDetection.ALGORITHM_ID, AnomalyDetection.VERSION);
        }
        if (selected.contains("error-cluster")) {
            facts.add(new ErrorClustering().analyze(failures.items(), refs(failures)));
            versions.put(ErrorClustering.ALGORITHM_ID, ErrorClustering.VERSION);
        }
        if (selected.contains("resource-saturation")) {
            facts.add(new ResourceSaturation().analyze(
                    resources.items(),
                    SATURATION_THRESHOLD,
                    MIN_SUSTAINED_POINTS,
                    series.items(),
                    refs(resources, series)
            ));
            versions.put(ResourceSaturation.ALGORITHM_ID, ResourceSaturation.VERSION);
        }
        return new AnalysisReport(
                SCHEMA_VERSION,
                executionId,
                effectiveFrom,
                effectiveTo,
                versions,
                facts,
                List.of(series.completeness(), failures.completeness(), resources.completeness())
        );
    }

    public AnalysisFact compare(long baselineExecutionId, long candidateExecutionId) {
        ExecutionComparison.ExecutionSide baseline = side(baselineExecutionId);
        ExecutionComparison.ExecutionSide candidate = side(candidateExecutionId);
        return new ExecutionComparison().compare(
                baseline,
                candidate,
                List.of("aggregate#" + baselineExecutionId, "aggregate#" + candidateExecutionId)
        );
    }

    private ExecutionComparison.ExecutionSide side(long executionId) {
        ExecutionSummary summary = dataFacade.getExecutionSummary(executionId);
        BoundedPage<TaskExecutionResult.AggregateRow> page =
                dataFacade.queryAggregateRows(executionId, null, PageBudget.defaults());
        return new ExecutionComparison.ExecutionSide(
                executionId,
                summary.scenarioId(),
                summary.durationMs(),
                page.items()
        );
    }

    private <T> SourceData<T> load(String sourceType, Supplier<BoundedPage<T>> query) {
        try {
            BoundedPage<T> page = query.get();
            Availability availability = page.availability();
            return new SourceData<>(
                    page.items(),
                    new SourceCompleteness(
                            sourceType,
                            availability != null && availability.present(),
                            availability != null && availability.truncated(),
                            availability == null || availability.missingReason() == null
                                    ? null
                                    : availability.missingReason().name()
                    ),
                    availability == null ? null : availability.sourceRef()
            );
        } catch (DataSourceUnavailableException exception) {
            return new SourceData<>(
                    List.of(),
                    new SourceCompleteness(sourceType, false, false, "SOURCE_UNAVAILABLE"),
                    null
            );
        }
    }

    private List<String> refs(SourceData<?>... sources) {
        List<String> refs = new ArrayList<>();
        for (SourceData<?> source : sources) {
            if (source.sourceRef() != null) {
                refs.add(source.sourceRef());
            }
        }
        return List.copyOf(refs);
    }

    private record SourceData<T>(List<T> items, SourceCompleteness completeness, String sourceRef) {
    }
}
