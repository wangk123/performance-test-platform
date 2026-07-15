package com.yr.perftest.platform.seed;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CaptureAnalysisStateMachineTest {
    @Test
    void persistsOnlyTheDeclaredAnalysisLifecycle() {
        PersistentSeedCaptureAnalysisRecord analysis =
                new PersistentSeedCaptureAnalysisRecord(1L, 2L, "[1,2,3]");

        assertThatThrownBy(() -> analysis.markStatus("SUCCEEDED"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("QUEUED")
                .hasMessageContaining("SUCCEEDED");

        analysis.markStatus("VALIDATING");
        analysis.markStatus("DIFFING");
        analysis.markStatus("INFERRING");
        analysis.markStatus("PERSISTING");
        analysis.complete("{\"candidateOperationCount\":1}", 8L);

        assertThat(analysis.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(analysis.getPhase()).isEqualTo("SUCCEEDED");
        assertThat(analysis.getTemplateId()).isEqualTo(8L);
        assertThat(analysis.getFinishedAt()).isNotNull();
    }

    @Test
    void cooperativelyCancelsAndRecordsFailureOrInterruptionAsTerminal() {
        PersistentSeedCaptureAnalysisRecord canceled =
                new PersistentSeedCaptureAnalysisRecord(1L, 2L, "[1,2,3]");
        canceled.markStatus("CANCEL_REQUESTED");
        canceled.cancel();

        assertThat(canceled.getStatus()).isEqualTo("CANCELED");
        assertThat(canceled.getFinishedAt()).isNotNull();

        PersistentSeedCaptureAnalysisRecord failed =
                new PersistentSeedCaptureAnalysisRecord(1L, 2L, "[1,2,3]");
        failed.fail("disk low water");
        assertThat(failed.getStatus()).isEqualTo("FAILED");

        PersistentSeedCaptureAnalysisRecord interrupted =
                new PersistentSeedCaptureAnalysisRecord(1L, 2L, "[1,2,3]");
        interrupted.markInterrupted("heartbeat expired");
        assertThat(interrupted.getStatus()).isEqualTo("INTERRUPTED");
    }

    @Test
    void persistsTableProgressAndHeartbeatWhileAnalysisIsActive() {
        PersistentSeedCaptureAnalysisRecord analysis =
                new PersistentSeedCaptureAnalysisRecord(1L, 2L, "[1,2,3]");
        Instant heartbeat = Instant.parse("2026-07-15T08:00:00Z");

        analysis.markStatus("VALIDATING");
        analysis.updateProgress(
                "DIFFING",
                2,
                5,
                "[\"shop.orders\"]",
                42,
                1,
                3,
                4,
                heartbeat
        );

        assertThat(analysis.getPhase()).isEqualTo("DIFFING");
        assertThat(analysis.getCompletedTables()).isEqualTo(2);
        assertThat(analysis.getTotalTables()).isEqualTo(5);
        assertThat(analysis.getCurrentTablesJson()).contains("shop.orders");
        assertThat(analysis.getComparedRows()).isEqualTo(42);
        assertThat(analysis.getSkippedTables()).isEqualTo(1);
        assertThat(analysis.getFineScreenedChunks()).isEqualTo(3);
        assertThat(analysis.getCandidateOperationCount()).isEqualTo(4);
        assertThat(analysis.getHeartbeatAt()).isEqualTo(heartbeat);
    }
}
