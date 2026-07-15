package com.yr.perftest.platform.seed;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CaptureSampleStateMachineTest {
    @Test
    void followsQueuedPreparingCapturingAndSuccessPath() {
        PersistentSeedCaptureSampleRecord sample = sample();

        sample.markPreparing();
        sample.markCapturing();
        sample.succeed();

        assertThat(sample.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(sample.getPhase()).isEqualTo("SUCCEEDED");
        assertThat(sample.getCaptureFinishedAt()).isNotNull();
    }

    @Test
    void supportsCooperativeCancelFailureAndInterruptionPaths() {
        PersistentSeedCaptureSampleRecord cancel = sample();
        cancel.markPreparing();
        cancel.markCapturing();
        cancel.requestCancel();
        cancel.cancel();

        PersistentSeedCaptureSampleRecord failure = sample();
        failure.markPreparing();
        failure.fail("source unavailable", true);

        PersistentSeedCaptureSampleRecord interrupted = sample();
        interrupted.markPreparing();
        interrupted.markInterrupted("service restarted");

        assertThat(cancel.getStatus()).isEqualTo("CANCELED");
        assertThat(cancel.getIncomplete()).isTrue();
        assertThat(failure.getStatus()).isEqualTo("FAILED");
        assertThat(failure.getErrorMessage()).isEqualTo("source unavailable");
        assertThat(interrupted.getStatus()).isEqualTo("INTERRUPTED");
        assertThat(interrupted.getErrorMessage()).isEqualTo("service restarted");
    }

    @Test
    void rejectsSkippingCapturePhase() {
        PersistentSeedCaptureSampleRecord sample = sample();

        assertThatThrownBy(() -> sample.markCapturing())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("QUEUED")
                .hasMessageContaining("CAPTURING");
    }

    @Test
    void updatesHeartbeatWithoutChangingLifecycleStatus() {
        PersistentSeedCaptureSampleRecord sample = sample();
        Instant heartbeat = Instant.parse("2026-07-15T07:30:00Z");

        sample.updateHeartbeat(heartbeat);

        assertThat(sample.getStatus()).isEqualTo("QUEUED");
        assertThat(sample.getHeartbeatAt()).isEqualTo(heartbeat);
    }

    private static PersistentSeedCaptureSampleRecord sample() {
        return new PersistentSeedCaptureSampleRecord(
                1L,
                2L,
                3L,
                1,
                "QUEUED",
                Instant.parse("2026-07-15T07:00:00Z"),
                null,
                "{}",
                1
        );
    }
}
