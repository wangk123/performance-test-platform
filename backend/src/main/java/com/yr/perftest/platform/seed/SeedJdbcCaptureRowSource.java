package com.yr.perftest.platform.seed;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SeedJdbcCaptureRowSource implements CaptureRowSource {
    private final Connection connection;

    public SeedJdbcCaptureRowSource(Connection connection) {
        this.connection = connection;
    }

    @Override
    public List<String> listTables(String database) throws SQLException {
        return SeedJdbcSupport.listTables(connection, database);
    }

    @Override
    public TableMetadata readMetadata(String qualifiedTable) throws SQLException {
        return SeedJdbcSupport.readMetadata(connection, qualifiedTable);
    }

    @Override
    public Optional<CapturePartitionPlanner.NumericPrimaryKeyRange> numericPrimaryKeyRange(
            TableMetadata metadata
    ) throws SQLException {
        if (metadata.primaryKeyColumns().size() != 1) {
            return Optional.empty();
        }
        String column = identifier(metadata.primaryKeyColumns().get(0));
        String sql = "SELECT MIN(" + column + "), MAX(" + column + ") FROM " + qualified(metadata.table());
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                return Optional.empty();
            }
            Object minimum = resultSet.getObject(1);
            Object maximum = resultSet.getObject(2);
            if (!(minimum instanceof Number min) || !(maximum instanceof Number max)) {
                return Optional.empty();
            }
            return Optional.of(new CapturePartitionPlanner.NumericPrimaryKeyRange(
                    metadata.primaryKeyColumns().get(0),
                    min.longValue(),
                    max.longValue()
            ));
        }
    }

    @Override
    public void readBatches(
            CapturePartitionPlanner.Partition partition,
            int batchRows,
            CaptureBatchConsumer consumer
    ) throws Exception {
        if (batchRows < 1) {
            throw new IllegalArgumentException("batchRows must be positive");
        }
        if (partition.mode() == CapturePartitionPlanner.Mode.KEYSET) {
            readKeysetBatches(partition, batchRows, consumer);
            return;
        }
        StringBuilder sql = new StringBuilder("SELECT * FROM ")
                .append(qualified(partition.table()));
        List<Object> parameters = new ArrayList<>();
        if (partition.mode() == CapturePartitionPlanner.Mode.RANGE) {
            String column = identifier(partition.primaryKeyColumns().get(0));
            sql.append(" WHERE ").append(column).append(" >= ? AND ")
                    .append(column).append(" <= ?");
            parameters.add(Long.valueOf(partition.lowerBound()));
            parameters.add(Long.valueOf(partition.upperBound()));
        }
        if (!partition.primaryKeyColumns().isEmpty()) {
            sql.append(" ORDER BY ").append(orderBy(partition.primaryKeyColumns()));
        }
        readResultSet(sql.toString(), parameters, batchRows, consumer);
    }

    private void readKeysetBatches(
            CapturePartitionPlanner.Partition partition,
            int batchRows,
            CaptureBatchConsumer consumer
    ) throws Exception {
        List<Object> lastKey = null;
        while (true) {
            StringBuilder sql = new StringBuilder("SELECT * FROM ")
                    .append(qualified(partition.table()));
            List<Object> parameters = new ArrayList<>();
            if (lastKey != null) {
                sql.append(" WHERE ").append(keysetPredicate(partition.primaryKeyColumns(), lastKey, parameters));
            }
            sql.append(" ORDER BY ").append(orderBy(partition.primaryKeyColumns()))
                    .append(" LIMIT ").append(batchRows);
            List<Map<String, Object>> rows = readAll(sql.toString(), parameters, batchRows);
            if (rows.isEmpty()) {
                return;
            }
            if (!consumer.accept(rows)) {
                return;
            }
            Map<String, Object> lastRow = rows.get(rows.size() - 1);
            lastKey = partition.primaryKeyColumns().stream()
                    .map(lastRow::get)
                    .toList();
            if (rows.size() < batchRows) {
                return;
            }
        }
    }

    private List<Map<String, Object>> readAll(
            String sql,
            List<Object> parameters,
            int batchRows
    ) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>(batchRows);
        try (PreparedStatement statement = prepare(sql, parameters, batchRows);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                rows.add(readRow(resultSet));
            }
        }
        return rows;
    }

    private void readResultSet(
            String sql,
            List<Object> parameters,
            int batchRows,
            CaptureBatchConsumer consumer
    ) throws Exception {
        try (PreparedStatement statement = prepare(sql, parameters, batchRows);
             ResultSet resultSet = statement.executeQuery()) {
            List<Map<String, Object>> batch = new ArrayList<>(batchRows);
            while (resultSet.next()) {
                batch.add(readRow(resultSet));
                if (batch.size() == batchRows) {
                    if (!consumer.accept(List.copyOf(batch))) {
                        return;
                    }
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                consumer.accept(List.copyOf(batch));
            }
        }
    }

    private PreparedStatement prepare(String sql, List<Object> parameters, int batchRows)
            throws SQLException {
        PreparedStatement statement = connection.prepareStatement(
                sql,
                ResultSet.TYPE_FORWARD_ONLY,
                ResultSet.CONCUR_READ_ONLY
        );
        statement.setFetchSize(batchRows);
        for (int i = 0; i < parameters.size(); i++) {
            statement.setObject(i + 1, parameters.get(i));
        }
        return statement;
    }

    private static Map<String, Object> readRow(ResultSet resultSet) throws SQLException {
        ResultSetMetaData metadata = resultSet.getMetaData();
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 1; i <= metadata.getColumnCount(); i++) {
            row.put(metadata.getColumnLabel(i), resultSet.getObject(i));
        }
        return row;
    }

    private static String keysetPredicate(
            List<String> columns,
            List<Object> lastKey,
            List<Object> parameters
    ) {
        List<String> alternatives = new ArrayList<>();
        for (int greaterAt = 0; greaterAt < columns.size(); greaterAt++) {
            List<String> terms = new ArrayList<>();
            for (int equalAt = 0; equalAt < greaterAt; equalAt++) {
                terms.add(identifier(columns.get(equalAt)) + " = ?");
                parameters.add(lastKey.get(equalAt));
            }
            terms.add(identifier(columns.get(greaterAt)) + " > ?");
            parameters.add(lastKey.get(greaterAt));
            alternatives.add("(" + String.join(" AND ", terms) + ")");
        }
        return String.join(" OR ", alternatives);
    }

    private static String orderBy(List<String> columns) {
        return columns.stream().map(SeedJdbcCaptureRowSource::identifier).reduce(
                (left, right) -> left + ", " + right
        ).orElseThrow();
    }

    private static String qualified(String table) {
        int separator = table.indexOf('.');
        if (separator <= 0 || separator == table.length() - 1 || table.indexOf('.', separator + 1) >= 0) {
            throw new IllegalArgumentException("table must be database.table: " + table);
        }
        return identifier(table.substring(0, separator))
                + "." + identifier(table.substring(separator + 1));
    }

    private static String identifier(String value) {
        if (value == null || value.isBlank() || value.indexOf('`') >= 0
                || value.indexOf('.') >= 0 || value.indexOf(';') >= 0) {
            throw new IllegalArgumentException("invalid SQL identifier");
        }
        return "`" + value + "`";
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }
}
