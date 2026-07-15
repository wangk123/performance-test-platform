package com.yr.perftest.platform.seed;

import java.util.Objects;

public record DiffWarning(Code code, String message) {
    public DiffWarning {
        code = Objects.requireNonNull(code, "code");
        message = Objects.requireNonNull(message, "message");
    }

    public enum Code {
        SEQUENCE_GAP,
        INCOMPATIBLE_STRATEGY_VERSION,
        INCOMPATIBLE_STRATEGY,
        INCOMPATIBLE_SCHEMA
    }
}
