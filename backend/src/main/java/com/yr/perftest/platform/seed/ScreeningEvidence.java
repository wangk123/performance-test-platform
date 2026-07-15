package com.yr.perftest.platform.seed;

import java.util.Objects;

public record ScreeningEvidence(
        String scope,
        Action action,
        String reason,
        boolean openedSnapshot
) {
    public ScreeningEvidence {
        scope = Objects.requireNonNull(scope, "scope");
        action = Objects.requireNonNull(action, "action");
        reason = Objects.requireNonNull(reason, "reason");
    }

    public enum Action {
        SKIPPED_TABLE,
        SKIPPED_CHUNK,
        FINE_COMPARE,
        UNKNOWN
    }
}
