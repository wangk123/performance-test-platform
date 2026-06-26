package com.yr.perftest.platform.execution.aggregate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "execution_metric_series",
        indexes = {
                @Index(name = "idx_metric_series_exec_label_time", columnList = "execution_id, label, bucket_time_ms"),
                @Index(name = "idx_metric_series_exec_time", columnList = "execution_id, bucket_time_ms")
        }
)
public class PersistentExecutionMetricSeriesRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "execution_id", nullable = false)
    private Long executionId;

    @Column(name = "bucket_time_ms", nullable = false)
    private Long bucketTimeMs;

    @Column(name = "label", nullable = false, length = 256)
    private String label;

    @Column(name = "samples", nullable = false)
    private Long samples;

    @Column(name = "error_samples", nullable = false)
    private Long errorSamples;

    @Column(name = "throughput", nullable = false)
    private Double throughput;

    @Column(name = "avg_rt_ms", nullable = false)
    private Long avgRtMs;

    @Column(name = "p95_rt_ms", nullable = false)
    private Long p95RtMs;

    protected PersistentExecutionMetricSeriesRecord() {
    }

    public PersistentExecutionMetricSeriesRecord(
            Long executionId,
            Long bucketTimeMs,
            String label,
            Long samples,
            Long errorSamples,
            Double throughput,
            Long avgRtMs,
            Long p95RtMs
    ) {
        this.executionId = executionId;
        this.bucketTimeMs = bucketTimeMs;
        this.label = label;
        this.samples = samples;
        this.errorSamples = errorSamples;
        this.throughput = throughput;
        this.avgRtMs = avgRtMs;
        this.p95RtMs = p95RtMs;
    }

    public Long getId() {
        return id;
    }

    public Long getExecutionId() {
        return executionId;
    }

    public Long getBucketTimeMs() {
        return bucketTimeMs;
    }

    public String getLabel() {
        return label;
    }

    public Long getSamples() {
        return samples;
    }

    public Long getErrorSamples() {
        return errorSamples;
    }

    public Double getThroughput() {
        return throughput;
    }

    public Long getAvgRtMs() {
        return avgRtMs;
    }

    public Long getP95RtMs() {
        return p95RtMs;
    }
}
