package com.yr.perftest.platform.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * 执行终态 trace 摘要快照（Task 6）：收尾时按耗时 Top500 拉取的 TraceBrief JSON。
 */
@Entity
@Table(
        name = "execution_trace_snapshot",
        uniqueConstraints = @UniqueConstraint(columnNames = "execution_id")
)
public class PersistentExecutionTraceSnapshotRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "execution_id", nullable = false)
    private Long executionId;

    @Lob
    @Column(name = "traces_json", nullable = false)
    private String tracesJson;

    @Column(name = "total", nullable = false)
    private Integer total;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    protected PersistentExecutionTraceSnapshotRecord() {
    }

    public PersistentExecutionTraceSnapshotRecord(
            Long executionId,
            String tracesJson,
            Integer total,
            Instant capturedAt
    ) {
        this.executionId = executionId;
        this.tracesJson = tracesJson;
        this.total = total;
        this.capturedAt = capturedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getExecutionId() {
        return executionId;
    }

    public String getTracesJson() {
        return tracesJson;
    }

    public Integer getTotal() {
        return total;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }
}
