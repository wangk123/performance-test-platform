package com.yr.perftest.platform.seed;

record CaptureTableWork(
        long projectId,
        long strategyId,
        String table,
        TableMetadata metadata,
        PersistentSeedCaptureSampleTableRecord sample,
        CapturePartitionPlanner.Partition partition,
        String schemaHash
) {
}
