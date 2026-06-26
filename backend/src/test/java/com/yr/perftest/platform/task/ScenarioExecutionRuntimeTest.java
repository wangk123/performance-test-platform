package com.yr.perftest.platform.task;

import com.yr.perftest.platform.execution.ExecutionStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioExecutionRuntimeTest {
    @Test
    void tracksStopRequestsPerExecution() {
        ScenarioExecutionRuntime runtime = new ScenarioExecutionRuntime();
        runtime.register(1L);
        runtime.register(2L);

        assertFalse(runtime.isStopRequested(1L));
        assertTrue(runtime.requestStop(1L));
        assertTrue(runtime.isStopRequested(1L));
        assertFalse(runtime.isStopRequested(2L));

        runtime.clear(1L);
        assertFalse(runtime.isStopRequested(1L));
    }
}
