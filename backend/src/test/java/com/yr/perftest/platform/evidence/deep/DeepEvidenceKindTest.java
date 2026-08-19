package com.yr.perftest.platform.evidence.deep;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DeepEvidenceKindTest {
    @Test
    void kindsDeclareUniqueKeysAndClocks() {
        Set<String> keys = new HashSet<>();
        for (DeepEvidenceKind kind : DeepEvidenceKind.values()) {
            keys.add(kind.key());
            assertThat(kind.sourceClock()).isIn("prometheus", "target");
        }
        assertThat(keys).hasSize(DeepEvidenceKind.values().length);
    }

    @Test
    void highImpactKindsRequireApproval() {
        assertThat(DeepEvidenceKind.TRACE.requiresApproval()).isTrue();
        assertThat(DeepEvidenceKind.PROFILING.requiresApproval()).isTrue();
        assertThat(DeepEvidenceKind.DB_METRICS.requiresApproval()).isFalse();
        assertThat(DeepEvidenceKind.APP_LOG.requiresApproval()).isFalse();
    }

    @Test
    void onlyTraceCapableKindsSupportTraceIdCorrelation() {
        Set<DeepEvidenceKind> traceCapable = Set.of(DeepEvidenceKind.TRACE, DeepEvidenceKind.APP_LOG);
        for (DeepEvidenceKind kind : DeepEvidenceKind.values()) {
            assertThat(kind.traceIdCapable()).isEqualTo(traceCapable.contains(kind));
        }
    }
}
