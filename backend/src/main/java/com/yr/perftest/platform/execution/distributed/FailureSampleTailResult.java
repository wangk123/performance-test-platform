package com.yr.perftest.platform.execution.distributed;

public record FailureSampleTailResult(
        byte[] data,
        long newOffset,
        boolean eof
) {
    public static FailureSampleTailResult empty(long offset) {
        return new FailureSampleTailResult(new byte[0], offset, true);
    }
}
