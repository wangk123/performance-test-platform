package com.yr.perftest.platform.analysis;

import com.yr.perftest.platform.execution.aggregate.MetricTick;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AnomalyDetection {
    public static final String ALGORITHM_ID = "anomaly";
    public static final String VERSION = "1";

    public AnalysisFact analyze(List<MetricTick> ticks, List<String> evidenceRefs) {
        List<MetricTick> usable = (ticks == null ? List.<MetricTick>of() : ticks).stream()
                .filter(tick -> tick != null && tick.overall() != null)
                .sorted(Comparator.comparingLong(MetricTick::bucketTimeMs))
                .toList();
        List<Double> values = usable.stream()
                .map(tick -> (double) tick.overall().avgRtMs())
                .toList();
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = values.stream().mapToDouble(value -> (value - mean) * (value - mean)).average().orElse(0);
        double stddev = Math.sqrt(variance);
        double threshold = mean + 3 * stddev;

        List<Map<String, Object>> intervals = new ArrayList<>();
        int index = 0;
        while (index < usable.size()) {
            if (stddev == 0 || values.get(index) <= threshold) {
                index++;
                continue;
            }
            int start = index;
            long max = usable.get(index).overall().avgRtMs();
            while (index < usable.size() && values.get(index) > threshold) {
                max = Math.max(max, usable.get(index).overall().avgRtMs());
                index++;
            }
            Map<String, Object> interval = new LinkedHashMap<>();
            interval.put("fromMs", usable.get(start).bucketTimeMs());
            interval.put("toMs", usable.get(index - 1).bucketTimeMs());
            interval.put("points", index - start);
            interval.put("maxAvgRtMs", max);
            intervals.add(interval);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mean", AnalysisMath.round4(mean));
        data.put("stddev", AnalysisMath.round4(stddev));
        data.put("threshold", AnalysisMath.round4(threshold));
        data.put("intervals", List.copyOf(intervals));
        data.put("kneePointMs", detectKnee(usable, values));
        return new AnalysisFact(
                ALGORITHM_ID,
                VERSION,
                "anomaly",
                "avg response time anomaly intervals and sustained knee point",
                data,
                evidenceRefs
        );
    }

    private Long detectKnee(List<MetricTick> usable, List<Double> values) {
        for (int index = 3; index < values.size(); index++) {
            double baseline = median(values.subList(0, index));
            if (baseline <= 0 || values.get(index) < 2 * baseline) {
                continue;
            }
            boolean sustained = true;
            for (int follow = index; follow < values.size(); follow++) {
                if (values.get(follow) < 1.5 * baseline) {
                    sustained = false;
                    break;
                }
            }
            if (sustained) {
                return usable.get(index).bucketTimeMs();
            }
        }
        return null;
    }

    private double median(List<Double> values) {
        List<Double> sorted = values.stream().sorted().toList();
        int midpoint = sorted.size() / 2;
        return sorted.size() % 2 == 1
                ? sorted.get(midpoint)
                : (sorted.get(midpoint - 1) + sorted.get(midpoint)) / 2;
    }
}
