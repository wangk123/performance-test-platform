package com.yr.perftest.platform.seed;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SeedJdbcSupport {
    private SeedJdbcSupport() {
    }

    public static Connection open(PersistentSeedDatasourceRecord ds, SeedCredentialCipher cipher) throws SQLException {
        String url = "jdbc:mysql://" + ds.getHost() + ":" + ds.getPort() + "/" + ds.getDatabaseName()
                + "?useSSL=false&allowPublicKeyRetrieval=true&useCursorFetch=true&characterEncoding=utf8";
        return DriverManager.getConnection(url, ds.getUsername(), cipher.decrypt(ds.getPasswordEnc()));
    }

    public static boolean testConnection(PersistentSeedDatasourceRecord ds, SeedCredentialCipher cipher) {
        try (Connection connection = open(ds, cipher); Statement statement = connection.createStatement()) {
            statement.execute("SELECT 1");
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public static String testConnectionMessage(PersistentSeedDatasourceRecord ds, SeedCredentialCipher cipher) {
        try (Connection connection = open(ds, cipher); Statement statement = connection.createStatement()) {
            statement.execute("SELECT 1");
            return "OK";
        } catch (Exception ex) {
            return ex.getMessage() == null ? "connection failed" : ex.getMessage();
        }
    }

    public static List<String> listTables(Connection connection, String database) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE' ORDER BY TABLE_NAME"
        )) {
            ps.setString(1, database);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tables.add(database + "." + rs.getString(1));
                }
            }
        }
        return tables;
    }

    public static TableMetadata readMetadata(Connection connection, String qualifiedTable) throws SQLException {
        String[] parts = split(qualifiedTable);
        String schema = parts[0];
        String table = parts[1];
        List<String> pk = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=? AND TABLE_NAME=? AND CONSTRAINT_NAME='PRIMARY' ORDER BY ORDINAL_POSITION"
        )) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    pk.add(rs.getString(1));
                }
            }
        }
        Map<String, String> primaryKeyTypes = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COLUMN_NAME, DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=? AND TABLE_NAME=?"
        )) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (pk.contains(rs.getString(1))) {
                        primaryKeyTypes.put(rs.getString(1), rs.getString(2));
                    }
                }
            }
        }
        Set<String> unique = new LinkedHashSet<>(pk);
        try (PreparedStatement ps = connection.prepareStatement(
                """
                SELECT kcu.COLUMN_NAME
                FROM information_schema.TABLE_CONSTRAINTS tc
                JOIN information_schema.KEY_COLUMN_USAGE kcu
                  ON tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME
                 AND tc.TABLE_SCHEMA = kcu.TABLE_SCHEMA
                 AND tc.TABLE_NAME = kcu.TABLE_NAME
                WHERE tc.TABLE_SCHEMA=? AND tc.TABLE_NAME=? AND tc.CONSTRAINT_TYPE='UNIQUE'
                """
        )) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    unique.add(rs.getString(1));
                }
            }
        }
        Map<String, FkRef> fks = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(
                """
                SELECT kcu.COLUMN_NAME, kcu.REFERENCED_TABLE_SCHEMA, kcu.REFERENCED_TABLE_NAME, kcu.REFERENCED_COLUMN_NAME
                FROM information_schema.KEY_COLUMN_USAGE kcu
                WHERE kcu.TABLE_SCHEMA=? AND kcu.TABLE_NAME=? AND kcu.REFERENCED_TABLE_NAME IS NOT NULL
                """
        )) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String refSchema = rs.getString(2);
                    String refTable = rs.getString(3);
                    fks.put(rs.getString(1), new FkRef(refSchema + "." + refTable, rs.getString(4)));
                }
            }
        }
        return new TableMetadata(qualifiedTable, pk, unique, fks, primaryKeyTypes);
    }

    public static Map<String, Map<String, String>> snapshotTable(Connection connection, String qualifiedTable, List<String> pkColumns) throws SQLException {
        String[] parts = split(qualifiedTable);
        Map<String, Map<String, String>> rows = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM `" + parts[0] + "`.`" + parts[1] + "`")) {
            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();
            while (rs.next()) {
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 1; i <= cols; i++) {
                    Object value = rs.getObject(i);
                    row.put(meta.getColumnLabel(i), value == null ? null : String.valueOf(value));
                }
                rows.put(SnapshotDiffEngine.pkKey(row, pkColumns), row);
            }
        }
        return rows;
    }

    public static void executeInsert(Connection connection, PlannedStatement statement) throws SQLException {
        String[] parts = split(statement.table());
        List<String> columns = new ArrayList<>(statement.values().keySet());
        String placeholders = String.join(",", columns.stream().map(c -> "?").toList());
        String sql = "INSERT INTO `" + parts[0] + "`.`" + parts[1] + "` (`"
                + String.join("`,`", columns) + "`) VALUES (" + placeholders + ")";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int idx = 1;
            for (String col : columns) {
                ps.setObject(idx++, statement.values().get(col));
            }
            ps.executeUpdate();
        }
    }

    public static void executeUpdate(Connection connection, PlannedStatement statement) throws SQLException {
        String[] parts = split(statement.table());
        List<String> sets = new ArrayList<>(statement.values().keySet());
        List<String> wheres = new ArrayList<>(statement.where().keySet());
        if (sets.isEmpty() || wheres.isEmpty()) {
            throw new SeedValidationException("invalid update statement for " + statement.table());
        }
        String sql = "UPDATE `" + parts[0] + "`.`" + parts[1] + "` SET "
                + String.join(", ", sets.stream().map(c -> "`" + c + "`=?").toList())
                + " WHERE "
                + String.join(" AND ", wheres.stream().map(c -> "`" + c + "`=?").toList());
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int idx = 1;
            for (String col : sets) {
                ps.setObject(idx++, statement.values().get(col));
            }
            for (String col : wheres) {
                ps.setObject(idx++, statement.where().get(col));
            }
            ps.executeUpdate();
        }
    }

    private static String[] split(String qualified) {
        int idx = qualified.indexOf('.');
        if (idx <= 0 || idx == qualified.length() - 1) {
            throw new SeedValidationException("table must be database.table: " + qualified);
        }
        return new String[]{qualified.substring(0, idx), qualified.substring(idx + 1)};
    }
}
