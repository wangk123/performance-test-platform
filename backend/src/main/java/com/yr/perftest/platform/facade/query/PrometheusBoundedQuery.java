package com.yr.perftest.platform.facade.query;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.facade.DataSourceUnavailableException;
import com.yr.perftest.platform.facade.data.PrometheusMetricPoint;
import com.yr.perftest.platform.monitoring.MetricKind;
import com.yr.perftest.platform.monitoring.MetricSeries;
import com.yr.perftest.platform.monitoring.MetricSeriesPoint;
import com.yr.perftest.platform.monitoring.MonitoringValidationException;
import com.yr.perftest.platform.monitoring.TargetMetricsQueryResult;
import com.yr.perftest.platform.monitoring.TargetMetricsService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class PrometheusBoundedQuery {
    private static final CursorCodec CURSOR_CODEC = new CursorCodec();

    private final TargetMetricsService targetMetricsService;
    private final ObjectMapper objectMapper;

    public PrometheusBoundedQuery(TargetMetricsService targetMetricsService, ObjectMapper objectMapper) {
        this.targetMetricsService = targetMetricsService;
        this.objectMapper = objectMapper;
    }

    public BoundedPage<PrometheusMetricPoint> query(
            long executionId,
            String metricSelector,
            Instant from,
            Instant to,
            int stepSeconds,
            String cursor,
            PageBudget budget
    ) {
        validate(from, to, stepSeconds, budget);
        MetricKind kind = parseKind(metricSelector);
        long segmentStart = decodeCursor(cursor, from, to);
        String sourceRef = "prometheus:" + kind.name() + "?step=" + stepSeconds;
        TargetMetricsQueryResult source;
        try {
            source = targetMetricsService.querySeries(executionId, kind, null, null, stepSeconds);
        } catch (MonitoringValidationException exception) {
            Availability availability = new Availability(
                    false,
                    null,
                    null,
                    stepSeconds + "s",
                    false,
                    sourceRef,
                    Availability.MissingReason.SOURCE_UNAVAILABLE
            );
            throw new DataSourceUnavailableException("prometheus source is unavailable", exception, availability);
        }

        List<PrometheusMetricPoint> points = flatten(source, segmentStart, from.getEpochSecond(), to.getEpochSecond());
        long startedAt = System.nanoTime();
        long serializedBytes = 0;
        List<PrometheusMetricPoint> items = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (PrometheusMetricPoint point : points) {
            if (items.size() >= budget.maxItems()) {
                warnings.add("budget:items");
                break;
            }
            long itemBytes = serializedSize(point);
            if (serializedBytes + itemBytes > budget.maxBytes()) {
                warnings.add("budget:bytes");
                break;
            }
            if (TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt) >= budget.maxMillis()) {
                warnings.add("budget:millis");
                break;
            }
            items.add(point);
            serializedBytes += itemBytes;
        }

        boolean truncated = items.size() < points.size();
        long nextStart = items.isEmpty()
                ? segmentStart
                : alignNext(items.get(items.size() - 1).timestamp(), stepSeconds);
        String nextCursor = truncated ? CURSOR_CODEC.encode(Long.toString(nextStart)) : null;
        boolean present = !items.isEmpty();
        Availability availability = new Availability(
                present,
                present ? Instant.ofEpochSecond(items.get(0).timestamp()) : null,
                present ? Instant.ofEpochSecond(items.get(items.size() - 1).timestamp()) : null,
                stepSeconds + "s",
                truncated,
                sourceRef,
                present ? null : Availability.MissingReason.NO_DATA
        );
        return new BoundedPage<>(List.copyOf(items), truncated, nextCursor, List.copyOf(warnings), availability);
    }

    private void validate(Instant from, Instant to, int stepSeconds, PageBudget budget) {
        budget.validate();
        if (from == null || to == null || from.isAfter(to)) {
            throw new IllegalArgumentException("prometheus time range is invalid");
        }
        if (stepSeconds <= 0) {
            throw new IllegalArgumentException("prometheus step must be positive");
        }
    }

    private MetricKind parseKind(String metricSelector) {
        if (metricSelector == null || metricSelector.isBlank()) {
            throw new IllegalArgumentException("prometheus metric selector is required");
        }
        try {
            return MetricKind.valueOf(metricSelector);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("prometheus metric selector is invalid", exception);
        }
    }

    private long decodeCursor(String cursor, Instant from, Instant to) {
        if (cursor == null || cursor.isBlank()) {
            return from.getEpochSecond();
        }
        try {
            long start = Long.parseLong(CURSOR_CODEC.decode(cursor));
            if (start < from.getEpochSecond() || start > to.getEpochSecond()) {
                throw new IllegalArgumentException("prometheus cursor is invalid");
            }
            return start;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("prometheus cursor is invalid", exception);
        }
    }

    private List<PrometheusMetricPoint> flatten(
            TargetMetricsQueryResult source,
            long segmentStart,
            long requestedStart,
            long requestedEnd
    ) {
        List<PrometheusMetricPoint> points = new ArrayList<>();
        List<MetricSeries> seriesList = source.series() == null ? List.of() : source.series();
        for (MetricSeries series : seriesList) {
            Map<String, String> labels = series.labels() == null ? Map.of() : series.labels();
            List<MetricSeriesPoint> seriesPoints = series.points() == null ? List.of() : series.points();
            for (MetricSeriesPoint point : seriesPoints) {
                if (point.timestamp() >= segmentStart
                        && point.timestamp() >= requestedStart
                        && point.timestamp() <= requestedEnd) {
                    points.add(new PrometheusMetricPoint(
                            series.displayName(),
                            labels,
                            point.timestamp(),
                            point.value(),
                            series.yAxisIndex()
                    ));
                }
            }
        }
        points.sort(Comparator.comparingLong(PrometheusMetricPoint::timestamp)
                .thenComparing(PrometheusMetricPoint::displayName, Comparator.nullsFirst(String::compareTo)));
        return points;
    }

    private long alignNext(long timestamp, int stepSeconds) {
        return Math.floorDiv(timestamp, stepSeconds) * (long) stepSeconds + stepSeconds;
    }

    private long serializedSize(PrometheusMetricPoint point) {
        try {
            return objectMapper.writeValueAsBytes(point).length;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("prometheus point cannot be serialized", exception);
        }
    }
}
