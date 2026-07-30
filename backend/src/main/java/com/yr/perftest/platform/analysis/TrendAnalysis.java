package com.yr.perftest.platform.analysis;

import com.yr.perftest.platform.execution.aggregate.MetricTick;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TrendAnalysis {
    public static final String ALGORITHM_ID = "trend";
    public static final String VERSION = "1";

    public AnalysisFact analyze(List<MetricTick> ticks, List<String> evidenceRefs) {
        List<MetricTick> usable = (ticks == null ? List.<MetricTick>of() : ticks).stream()
                .filter(tick -> tick != null && tick.overall() != null)
                .sorted(Comparator.comparingLong(MetricTick::bucketTimeMs))
                .toList();
        int midpoint = usable.size() / 2;
        List<MetricTick> firstHalf = usable.stream().limit(midpoint).toList();
        List<MetricTick> secondHalf = usable.stream().skip(midpoint).toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tickCount", usable.size());
        data.put("avgRtMs", metricTrend(avgRt(firstHalf), avgRt(secondHalf)));
        data.put("throughput", metricTrend(meanThroughput(firstHalf), meanThroughput(secondHalf)));
        data.put("errorRate", metricTrend(errorRate(firstHalf), errorRate(secondHalf)));
        return new AnalysisFact(
                ALGORITHM_ID,
                VERSION,
                "trend",
                "response time / throughput / error rate trend of second half vs first half",
                data,
                evidenceRefs
        );
    }

    private Map<String, Object> metricTrend(double first, double second) {
        Double deltaPct = AnalysisMath.deltaPct(first, second);
        Map<String, Object> trend = new LinkedHashMap<>();
        trend.put("first", AnalysisMath.round4(first));
        trend.put("second", AnalysisMath.round4(second));
        trend.put("deltaPct", deltaPct);
        trend.put("direction", AnalysisMath.direction(deltaPct));
        return trend;
    }

    private double avgRt(List<MetricTick> half) {
        long samples = half.stream().mapToLong(tick -> tick.overall().samples()).sum();
        if (samples > 0) {
            double weighted = half.stream()
                    .mapToDouble(tick -> (double) tick.overall().avgRtMs() * tick.overall().samples())
                    .sum();
            return weighted / samples;
        }
        return half.stream().mapToDouble(tick -> tick.overall().avgRtMs()).average().orElse(0);
    }

    private double meanThroughput(List<MetricTick> half) {
        return half.stream().mapToDouble(tick -> tick.overall().throughput()).average().orElse(0);
    }

    private double errorRate(List<MetricTick> half) {
        long samples = half.stream().mapToLong(tick -> tick.overall().samples()).sum();
        long errors = half.stream().mapToLong(tick -> tick.overall().errorSamples()).sum();
        return samples == 0 ? 0 : (double) errors / samples;
    }
}
