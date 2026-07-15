package com.yr.perftest.platform.seed;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface CaptureRowSource extends AutoCloseable {
    List<String> listTables(String database) throws Exception;

    TableMetadata readMetadata(String qualifiedTable) throws Exception;

    void readBatches(
            CapturePartitionPlanner.Partition partition,
            int batchRows,
            CaptureBatchConsumer consumer
    ) throws Exception;

    default Optional<CapturePartitionPlanner.NumericPrimaryKeyRange> numericPrimaryKeyRange(
            TableMetadata metadata
    ) throws Exception {
        return Optional.empty();
    }

    @Override
    default void close() throws Exception {
    }
}
