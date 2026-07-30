package com.yr.perftest.platform.analysis;

import com.yr.perftest.platform.execution.aggregate.MetricTick;
import com.yr.perftest.platform.facade.data.PrometheusMetricPoint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class ResourceSaturation {
    public static final String ALGORITHM_ID = "resource-saturation";
    public static final String VERSION = "2";

    public AnalysisFact analyze(
            List<PrometheusMetricPoint> points,
            double saturationThreshold,
            int minSustainedPoints,
            List<MetricTick> loadTicks,
            List<String> evidenceRefs
    ) {
        List<PrometheusMetricPoint> usable = points == null ? List.of() : points.stream()
                .filter(point -> point != null)
                .toList();
        Map<String, List<PrometheusMetricPoint>> bySeries = new TreeMap<>();
        for (PrometheusMetricPoint point : usable) {
            bySeries.computeIfAbsent(
                    point.displayName() == null ? "" : point.displayName(),
                    key -> new ArrayList<>()
            ).add(point);
        }

        List<Map<String, Object>> windows = new ArrayList<>();
        for (Map.Entry<String, List<PrometheusMetricPoint>> entry : bySeries.entrySet()) {
            List<PrometheusMetricPoint> series = entry.getValue().stream()
                    .sorted(Comparator.comparingLong(PrometheusMetricPoint::timestamp))
                    .toList();
            int index = 0;
            while (index < series.size()) {
                if (series.get(index).value() < saturationThreshold) {
                    index++;
                    continue;
                }
                int start = index;
                double max = series.get(index).value();
                while (index < series.size() && series.get(index).value() >= saturationThreshold) {
                    max = Math.max(max, series.get(index).value());
                    index++;
                }
                if (index - start >= minSustainedPoints) {
                    Map<String, Object> window = new LinkedHashMap<>();
                    window.put("series", entry.getKey());
                    window.put("fromEpochSec", series.get(start).timestamp());
                    window.put("toEpochSec", series.get(index - 1).timestamp());
                    window.put("points", index - start);
                    window.put("maxValue", AnalysisMath.round4(max));
                    windows.add(window);
                }
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("threshold", saturationThreshold);
        data.put("windows", List.copyOf(windows));
        data.put("correlation", correlation(usable, loadTicks));
        return new AnalysisFact(
                ALGORITHM_ID,
                VERSION,
                "resource-saturation",
                "sustained resource saturation windows and load correlation",
                data,
                evidenceRefs
        );
    }

    private Map<String, Object> correlation(List<PrometheusMetricPoint> points, List<MetricTick> loadTicks) {
        Map<Long, double[]> resourceBySecond = new TreeMap<>();
        for (PrometheusMetricPoint point : points) {
            double[] accumulator = resourceBySecond.computeIfAbsent(point.timestamp(), key -> new double[2]);
            accumulator[0] += point.value();
            accumulator[1] += 1;
        }
        List<Long> resourceSeconds = new ArrayList<>(resourceBySecond.keySet());
        long stepSeconds = deriveStepSeconds(resourceSeconds);
        long origin = resourceSeconds.isEmpty() ? 0 : resourceSeconds.get(0);

        Map<Long, double[]> throughputByCell = new TreeMap<>();
        if (loadTicks != null) {
            for (MetricTick tick : loadTicks) {
                if (tick == null || tick.overall() == null) {
                    continue;
                }
                long cell = Math.floorDiv(tick.bucketTimeMs() / 1000 - origin, stepSeconds);
                double[] accumulator = throughputByCell.computeIfAbsent(cell, key -> new double[2]);
                accumulator[0] += tick.overall().throughput();
                accumulator[1] += 1;
            }
        }
        List<Double> resourceValues = new ArrayList<>();
        List<Double> throughputValues = new ArrayList<>();
        for (Map.Entry<Long, double[]> entry : resourceBySecond.entrySet()) {
            double[] throughput = throughputByCell.get(Math.floorDiv(entry.getKey() - origin, stepSeconds));
            if (throughput == null) {
                continue;
            }
            resourceValues.add(entry.getValue()[0] / entry.getValue()[1]);
            throughputValues.add(throughput[0] / throughput[1]);
        }
        Map<String, Object> correlation = new LinkedHashMap<>();
        correlation.put("alignedPairs", resourceValues.size());
        correlation.put("pearson", resourceValues.size() < 3
                ? null
                : AnalysisMath.round4(pearson(resourceValues, throughputValues)));
        return correlation;
    }

    private long deriveStepSeconds(List<Long> sortedSeconds) {
        long step = Long.MAX_VALUE;
        for (int index = 1; index < sortedSeconds.size(); index++) {
            long delta = sortedSeconds.get(index) - sortedSeconds.get(index - 1);
            if (delta > 0 && delta < step) {
                step = delta;
            }
        }
        return step == Long.MAX_VALUE ? 1 : step;
    }

    private double pearson(List<Double> xs, List<Double> ys) {
        double meanX = xs.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double meanY = ys.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double numerator = 0;
        double sumX = 0;
        double sumY = 0;
        for (int index = 0; index < xs.size(); index++) {
            double dx = xs.get(index) - meanX;
            double dy = ys.get(index) - meanY;
            numerator += dx * dy;
            sumX += dx * dx;
            sumY += dy * dy;
        }
        if (sumX == 0 || sumY == 0) {
            return 0;
        }
        return numerator / Math.sqrt(sumX * sumY);
    }
}
