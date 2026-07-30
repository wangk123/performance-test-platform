package com.yr.perftest.platform.execution.distributed;

import com.yr.perftest.platform.execution.ExecutionStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RunnerTerminalGuardTest {
    @Test
    void terminalStatusesAreGuarded() {
        assertThat(DistributedJmeterExecutionRunner.isTerminalStatus(ExecutionStatus.SUCCESS)).isTrue();
        assertThat(DistributedJmeterExecutionRunner.isTerminalStatus(ExecutionStatus.FAILED)).isTrue();
        assertThat(DistributedJmeterExecutionRunner.isTerminalStatus(ExecutionStatus.CANCELLED)).isTrue();
        assertThat(DistributedJmeterExecutionRunner.isTerminalStatus(ExecutionStatus.INTERRUPTED)).isTrue();
    }

    @Test
    void activeStatusesAreNotGuarded() {
        assertThat(DistributedJmeterExecutionRunner.isTerminalStatus(ExecutionStatus.QUEUED)).isFalse();
        assertThat(DistributedJmeterExecutionRunner.isTerminalStatus(ExecutionStatus.RUNNING)).isFalse();
        assertThat(DistributedJmeterExecutionRunner.isTerminalStatus(ExecutionStatus.STOPPING)).isFalse();
    }
}
