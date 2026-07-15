package com.yr.perftest.platform.seed;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Locale;
import java.util.Set;

public final class CapturePartitionPlanner {
    public List<Partition> plan(TableMetadata metadata, int batchRows) {
        Objects.requireNonNull(metadata, "metadata");
        validateBatchRows(batchRows);
        List<String> primaryKeyColumns = metadata.primaryKeyColumns();
        if (primaryKeyColumns == null || primaryKeyColumns.isEmpty()) {
            return List.of(Partition.singleStream(metadata.table(), batchRows));
        }
        Map<String, String> primaryKeyTypes = metadata.primaryKeyTypes() == null
                ? Map.of()
                : metadata.primaryKeyTypes();
        boolean keyset = primaryKeyColumns.size() > 1
                || primaryKeyTypes.values().stream()
                .anyMatch(type -> keyType(type) != KeyType.NUMERIC);
        if (keyset) {
            return plan(
                    metadata,
                    primaryKeyColumns.stream()
                            .map(column -> new PrimaryKeyColumn(
                                    column,
                                    keyType(primaryKeyTypes.get(column))
                            ))
                            .toList(),
                    batchRows
            );
        }
        return List.of(Partition.table(metadata.table(), primaryKeyColumns, batchRows));
    }

    public List<Partition> plan(
            TableMetadata metadata,
            NumericPrimaryKeyRange range,
            int batchRows
    ) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(range, "range");
        validateBatchRows(batchRows);
        if (metadata.primaryKeyColumns() == null
                || metadata.primaryKeyColumns().size() != 1
                || !metadata.primaryKeyColumns().get(0).equals(range.column())) {
            throw new IllegalArgumentException("numeric range requires the single primary key column");
        }
        if (range.minInclusive() > range.maxInclusive()) {
            return List.of();
        }
        List<Partition> partitions = new ArrayList<>();
        long lower = range.minInclusive();
        int sequence = 0;
        while (lower <= range.maxInclusive()) {
            long width = batchRows - 1L;
            long upper = lower > Long.MAX_VALUE - width
                    ? Long.MAX_VALUE
                    : lower + width;
            upper = Math.min(upper, range.maxInclusive());
            partitions.add(Partition.range(
                    metadata.table(),
                    sequence++,
                    range.column(),
                    lower,
                    upper,
                    batchRows
            ));
            if (upper == Long.MAX_VALUE) {
                break;
            }
            lower = upper + 1;
        }
        return List.copyOf(partitions);
    }

    public List<Partition> plan(
            TableMetadata metadata,
            List<PrimaryKeyColumn> primaryKeyColumns,
            int batchRows
    ) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(primaryKeyColumns, "primaryKeyColumns");
        validateBatchRows(batchRows);
        if (primaryKeyColumns.isEmpty()) {
            return List.of(Partition.singleStream(metadata.table(), batchRows));
        }
        boolean keyset = primaryKeyColumns.size() > 1
                || primaryKeyColumns.stream().anyMatch(column -> column.type() != KeyType.NUMERIC);
        if (keyset) {
            return List.of(Partition.keyset(
                    metadata.table(),
                    primaryKeyColumns.stream().map(PrimaryKeyColumn::name).toList(),
                    batchRows
            ));
        }
        return List.of(Partition.table(
                metadata.table(),
                primaryKeyColumns.stream().map(PrimaryKeyColumn::name).toList(),
                batchRows
        ));
    }

    private static void validateBatchRows(int batchRows) {
        if (batchRows < 1) {
            throw new IllegalArgumentException("batchRows must be positive");
        }
    }

    private static KeyType keyType(String databaseType) {
        if (databaseType == null) {
            return KeyType.OTHER;
        }
        String type = databaseType.toLowerCase(Locale.ROOT);
        return Set.of(
                "tinyint",
                "smallint",
                "mediumint",
                "int",
                "integer",
                "bigint",
                "decimal",
                "numeric",
                "float",
                "double",
                "real"
        ).contains(type) ? KeyType.NUMERIC : KeyType.STRING;
    }

    public enum Mode {
        TABLE,
        RANGE,
        KEYSET,
        SINGLE_STREAM
    }

    public enum KeyType {
        NUMERIC,
        STRING,
        OTHER
    }

    public record PrimaryKeyColumn(String name, KeyType type) {
        public PrimaryKeyColumn {
            if (name == null || name.isBlank() || type == null) {
                throw new IllegalArgumentException("primary key column is required");
            }
        }
    }

    public record NumericPrimaryKeyRange(String column, long minInclusive, long maxInclusive) {
        public NumericPrimaryKeyRange {
            if (column == null || column.isBlank()) {
                throw new IllegalArgumentException("numeric primary key column is required");
            }
        }
    }

    public record Partition(
            String table,
            Mode mode,
            int sequence,
            int maxRows,
            List<String> primaryKeyColumns,
            String lowerBound,
            String upperBound,
            boolean riskyNoPk
    ) {
        private static Partition table(String table, List<String> primaryKeyColumns, int batchRows) {
            return new Partition(
                    table,
                    Mode.TABLE,
                    0,
                    batchRows,
                    List.copyOf(primaryKeyColumns),
                    null,
                    null,
                    false
            );
        }

        private static Partition keyset(String table, List<String> primaryKeyColumns, int batchRows) {
            return new Partition(
                    table,
                    Mode.KEYSET,
                    0,
                    batchRows,
                    List.copyOf(primaryKeyColumns),
                    null,
                    null,
                    false
            );
        }

        private static Partition singleStream(String table, int batchRows) {
            return new Partition(table, Mode.SINGLE_STREAM, 0, batchRows, List.of(), null, null, true);
        }

        private static Partition range(
                String table,
                int sequence,
                String column,
                long lowerBound,
                long upperBound,
                int batchRows
        ) {
            return new Partition(
                    table,
                    Mode.RANGE,
                    sequence,
                    batchRows,
                    List.of(column),
                    Long.toString(lowerBound),
                    Long.toString(upperBound),
                    false
            );
        }
    }
}
