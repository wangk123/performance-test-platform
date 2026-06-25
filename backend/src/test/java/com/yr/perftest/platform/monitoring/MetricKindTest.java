package com.yr.perftest.platform.monitoring;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetricKindTest {
    @Test
    void serverMetricsUseServerResourceKind() {
        assertThat(MetricKind.SERVER_CPU.serverMetric()).isTrue();
        assertThat(MetricKind.SERVER_LOAD.serverMetric()).isTrue();
        assertThat(MetricKind.SERVER_MEM.serverMetric()).isTrue();
        assertThat(MetricKind.SERVER_DISK_IO.serverMetric()).isTrue();
        assertThat(MetricKind.SERVER_NET.serverMetric()).isTrue();
        assertThat(MetricKind.SERVER_TCP.serverMetric()).isTrue();
    }

    @Test
    void jvmMetricsUseJavaJmxAgentKind() {
        assertThat(MetricKind.JVM_HEAP_PCT.serverMetric()).isFalse();
        assertThat(MetricKind.JVM_MEMORY_BYTES.serverMetric()).isFalse();
        assertThat(MetricKind.JVM_GC.serverMetric()).isFalse();
        assertThat(MetricKind.JVM_THREADS.serverMetric()).isFalse();
        assertThat(MetricKind.JVM_CPU.serverMetric()).isFalse();
    }
}
