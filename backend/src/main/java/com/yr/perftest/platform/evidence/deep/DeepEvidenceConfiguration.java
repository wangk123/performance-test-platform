package com.yr.perftest.platform.evidence.deep;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.yr.perftest.platform.evidence.EvidenceSource;
import com.yr.perftest.platform.monitoring.PrometheusQueryClient;

/**
 * 深度证据源装配（T11）：五类源各自注册为独立 {@link EvidenceSource}，
 * 由 {@code EvidenceService} 统一收集，确保每源单独具备可用性与关联键声明。
 */
@Configuration
@EnableConfigurationProperties(DeepEvidenceProperties.class)
public class DeepEvidenceConfiguration {
    @Bean
    public PrometheusDeepProbe dbMetricsDeepProbe(
            DeepEvidenceProperties properties,
            PrometheusQueryClient prometheusQueryClient
    ) {
        return new PrometheusDeepProbe(properties, prometheusQueryClient);
    }

    @Bean
    public EvidenceSource dbMetricsDeepEvidenceSource(
            DeepEvidenceProperties properties,
            PrometheusDeepProbe probe
    ) {
        return new DeepEvidenceSource(DeepEvidenceKind.DB_METRICS, properties, probe);
    }

    @Bean
    public EvidenceSource traceDeepEvidenceSource(DeepEvidenceProperties properties) {
        return new DeepEvidenceSource(
                DeepEvidenceKind.TRACE,
                properties,
                new UnavailableDeepProbe(DeepEvidenceKind.TRACE, "pending-otel-or-skywalking-selection")
        );
    }

    @Bean
    public EvidenceSource appLogDeepEvidenceSource(DeepEvidenceProperties properties) {
        return new DeepEvidenceSource(
                DeepEvidenceKind.APP_LOG,
                properties,
                new UnavailableDeepProbe(DeepEvidenceKind.APP_LOG, "pending-log-platform-selection")
        );
    }

    @Bean
    public EvidenceSource slowSqlDeepEvidenceSource(DeepEvidenceProperties properties) {
        return new DeepEvidenceSource(
                DeepEvidenceKind.SLOW_SQL,
                properties,
                new UnavailableDeepProbe(DeepEvidenceKind.SLOW_SQL, "pending-sql-probe-selection")
        );
    }

    @Bean
    public EvidenceSource profilingDeepEvidenceSource(DeepEvidenceProperties properties) {
        return new DeepEvidenceSource(
                DeepEvidenceKind.PROFILING,
                properties,
                new UnavailableDeepProbe(DeepEvidenceKind.PROFILING, "pending-jfr-probe-selection")
        );
    }
}
