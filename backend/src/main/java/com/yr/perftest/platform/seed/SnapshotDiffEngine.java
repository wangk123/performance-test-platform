package com.yr.perftest.platform.seed;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class SnapshotDiffEngine {
    private SnapshotDiffEngine() {
    }

    public record RowDiff(String kind, Map<String, String> primaryKey, Map<String, String> before, Map<String, String> after) {
    }

    public static Map<String, RowDiff> diff(
            Map<String, Map<String, String>> beforeByPk,
            Map<String, Map<String, String>> afterByPk
    ) {
        Map<String, RowDiff> diffs = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : afterByPk.entrySet()) {
            String pk = entry.getKey();
            Map<String, String> after = entry.getValue();
            Map<String, String> before = beforeByPk.get(pk);
            if (before == null) {
                diffs.put(pk, new RowDiff("INSERT", pkMap(after), Map.of(), after));
            } else if (!before.equals(after)) {
                diffs.put(pk, new RowDiff("UPDATE", pkMap(after), before, after));
            }
        }
        return diffs;
    }

    public static String pkKey(Map<String, String> row, java.util.List<String> pkColumns) {
        if (pkColumns == null || pkColumns.isEmpty()) {
            return String.valueOf(Objects.hash(row));
        }
        return pkColumns.stream().map(col -> col + "=" + row.get(col)).collect(Collectors.joining("|"));
    }

    private static Map<String, String> pkMap(Map<String, String> row) {
        return row;
    }
}
