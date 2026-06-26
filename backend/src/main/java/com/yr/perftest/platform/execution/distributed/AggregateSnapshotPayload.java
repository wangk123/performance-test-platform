package com.yr.perftest.platform.execution.distributed;

import java.util.List;

public record AggregateSnapshotPayload(
        List<byte[]> snapshots,
        long mtime,
        boolean changed
) {
    public static AggregateSnapshotPayload noop(long mtime) {
        return new AggregateSnapshotPayload(List.of(), mtime, false);
    }

    public static AggregateSnapshotPayload changed(List<byte[]> snapshots, long mtime) {
        return new AggregateSnapshotPayload(snapshots, mtime, true);
    }
}
