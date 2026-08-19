package com.yr.perftest.platform.evidence.deep;

import com.yr.perftest.platform.evidence.CorrelationKey;
import com.yr.perftest.platform.facade.query.Availability;
import com.yr.perftest.platform.facade.query.PageBudget;
import com.yr.perftest.platform.monitoring.MetricSeries;
import com.yr.perftest.platform.monitoring.MetricSeriesPoint;
import com.yr.perftest.platform.monitoring.PrometheusQueryClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * db-metrics 深度证据探针（T11）：基于现有 Prometheus exporter，运行可配置的
 * 连接池/慢查询计数/锁等待 promql 模板，输出有界摘要。
 */
public class PrometheusDeepProbe implements DeepEvidenceProbe {
    public static final Map<String, String> DEFAULT_TEMPLATES = Map.of(
            "connection-pool", "sum by (instance) (jdbc_connections_active)",
            "slow-query-count", "sum by (instance) (rate(jdbc_slow_queries_total[5m]))",
            "lock-waits", "sum by (instance) (jdbc_lock_waits_total)"
    );

    private final DeepEvidenceProperties properties;
    private final PrometheusQueryClient prometheusQueryClient;

    public PrometheusDeepProbe(DeepEvidenceProperties properties, PrometheusQueryClient prometheusQueryClient) {
        this.properties = properties;
        this.prometheusQueryClient = prometheusQueryClient;
    }

    @Override
    public DeepEvidenceKind kind() {
        return DeepEvidenceKind.DB_METRICS;
    }

    @Override
    public DeepProbeResult probe(CorrelationKey key, PageBudget budget) {
        DeepEvidenceProperties.KindConfig config = properties.forKind(kind());
        Map<String, String> templates = config.getPromqlTemplates().isEmpty()
                ? DEFAULT_TEMPLATES
                : config.getPromqlTemplates();
        int stepSeconds = 15;
        Map<String, Object> metrics = new LinkedHashMap<>();
        List<MetricSeriesPoint> points = new ArrayList<>();
        boolean anyQuerySucceeded = false;
        for (Map.Entry<String, String> template : templates.entrySet()) {
            try {
                List<MetricSeries> series = prometheusQueryClient.queryRange(
                        template.getValue(),
                        key.from().getEpochSecond(),
                        key.to().getEpochSecond(),
                        stepSeconds
                );
                anyQuerySucceeded = true;
                int count = 0;
                for (MetricSeries item : series) {
                    if (item.points() == null) {
                        continue;
                    }
                    for (MetricSeriesPoint point : item.points()) {
                        points.add(point);
                        count++;
                    }
                }
                metrics.put(template.getKey(), Map.of("series", series.size(), "points", count));
            } catch (Exception exception) {
                metrics.put(template.getKey(), Map.of("series", 0, "points", 0, "error", exception.getMessage()));
            }
        }
        if (points.isEmpty()) {
            String sourceRef = "prometheus:db-metrics?queries=" + templates.size();
            // 全部查询失败 = 数据源不可达；查询成功但无点 = 确实无数据
            Availability.MissingReason missingReason = anyQuerySucceeded
                    ? Availability.MissingReason.NO_DATA
                    : Availability.MissingReason.SOURCE_UNAVAILABLE;
            return new DeepProbeResult(
                    new Availability(
                            false,
                            null,
                            null,
                            stepSeconds + "s",
                            false,
                            sourceRef,
                            missingReason
                    ),
                    metrics,
                    sourceRef
            );
        }
        points.sort((first, second) -> Long.compare(first.timestamp(), second.timestamp()));
        boolean truncated = points.size() > budget.maxItems();
        List<MetricSeriesPoint> bounded = truncated ? points.subList(0, budget.maxItems()) : points;
        String sourceRef = "prometheus:db-metrics?queries=" + templates.size()
                + "#" + bounded.get(0).timestamp() + "-" + bounded.get(bounded.size() - 1).timestamp();
        return new DeepProbeResult(
                new Availability(
                        true,
                        java.time.Instant.ofEpochSecond(bounded.get(0).timestamp()),
                        java.time.Instant.ofEpochSecond(bounded.get(bounded.size() - 1).timestamp()),
                        stepSeconds + "s",
                        truncated,
                        sourceRef,
                        null
                ),
                metrics,
                sourceRef
        );
    }
}
