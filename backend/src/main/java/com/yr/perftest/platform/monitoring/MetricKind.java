package com.yr.perftest.platform.monitoring;

import java.util.List;

public enum MetricKind {
    SERVER_CPU("percent", true, List.of(
            "1 - avg by (instance, target_id, server) (rate(node_cpu_seconds_total{mode=\"idle\",%s}[1m]))"
    )),
    SERVER_LOAD("", true, List.of(
            "node_load1{%s}",
            "node_load5{%s}",
            "node_load15{%s}"
    )),
    SERVER_MEM("percent", true, List.of(
            "1 - (node_memory_MemAvailable_bytes{%s} / node_memory_MemTotal_bytes{%s})"
    )),
    SERVER_DISK_IO("percentunit", true, List.of(
            "rate(node_disk_io_time_seconds_total{%s}[1m])"
    )),
    SERVER_NET("Bps", true, List.of(
            "sum by (instance, target_id, server) (rate(node_network_receive_bytes_total{device!=\"lo\",%s}[1m]))",
            "sum by (instance, target_id, server) (rate(node_network_transmit_bytes_total{device!=\"lo\",%s}[1m]))"
    )),
    SERVER_TCP("", true, List.of(
            "node_netstat_Tcp_CurrEstab{%s}",
            "rate(node_netstat_Tcp_RetransSegs{%s}[1m])"
    )),
    JVM_HEAP_PCT("percent", false, List.of(
            "jvm_memory_bytes_used{area=\"heap\",%s} / jvm_memory_bytes_max{area=\"heap\",%s}"
    )),
    JVM_MEMORY_BYTES("bytes", false, List.of(
            "jvm_memory_bytes_used{area=\"heap\",%s}",
            "jvm_memory_bytes_used{area=\"nonheap\",%s}"
    )),
    JVM_GC("", false, List.of(
            "rate(jvm_gc_collection_seconds_count{%s}[1m])",
            "rate(jvm_gc_collection_seconds_sum{%s}[1m])"
    )),
    JVM_THREADS("", false, List.of(
            "jvm_threads_threadcount{%s}",
            "jvm_threads_daemonthreadcount{%s}",
            "jvm_threads_peakthreadcount{%s}"
    )),
    JVM_CPU("percent", false, List.of(
            "rate(process_cpu_seconds_total{%s}[1m])"
    ));

    private final String unit;
    private final boolean serverMetric;
    private final List<String> promqlTemplates;

    MetricKind(String unit, boolean serverMetric, List<String> promqlTemplates) {
        this.unit = unit;
        this.serverMetric = serverMetric;
        this.promqlTemplates = promqlTemplates;
    }

    public String unit() {
        return unit;
    }

    public boolean serverMetric() {
        return serverMetric;
    }

    public List<String> promqlTemplates() {
        return promqlTemplates;
    }
}
