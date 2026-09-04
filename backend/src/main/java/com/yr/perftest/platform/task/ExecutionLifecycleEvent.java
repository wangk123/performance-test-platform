package com.yr.perftest.platform.task;

import com.yr.perftest.platform.execution.ExecutionStatus;

/** 场景执行进入终态（SUCCESS/FAILED/CANCELLED/INTERRUPTED）后发布。 */
public record ExecutionLifecycleEvent(long executionId, ExecutionStatus terminalStatus) {
}
