package com.yr.perftest.platform.monitoring;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "execution_monitor_binding")
public class PersistentExecutionMonitorBindingRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long executionId;

    @Column(nullable = false)
    private Long targetId;

    private Instant startTime;

    private Instant endTime;

    protected PersistentExecutionMonitorBindingRecord() {
    }

    public PersistentExecutionMonitorBindingRecord(Long executionId, Long targetId) {
        this.executionId = executionId;
        this.targetId = targetId;
    }

    public Long getExecutionId() {
        return executionId;
    }

    public Long getTargetId() {
        return targetId;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void markStart(Instant startTime) {
        this.startTime = startTime;
    }

    public void markEnd(Instant endTime) {
        this.endTime = endTime;
    }
}
