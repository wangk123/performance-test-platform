package com.yr.perftest.platform.seed;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CloneBatchPlanner {
    private CloneBatchPlanner() {
    }

    public static List<PlannedStatement> plan(
            List<TemplateOperation> operations,
            Map<String, Map<String, String>> seedRows,
            ValueGenerator generator,
            Map<String, String> idMap
    ) {
        return operations.stream()
                .filter(op -> !op.riskyNoPk())
                .map(op -> planOne(op, seedRows.getOrDefault(op.table(), Map.of()), generator, idMap))
                .toList();
    }

    private static PlannedStatement planOne(
            TemplateOperation op,
            Map<String, String> seedRow,
            ValueGenerator generator,
            Map<String, String> idMap
    ) {
        Map<String, String> values = new LinkedHashMap<>();
        Map<String, String> where = new LinkedHashMap<>();
        for (TemplateColumn col : op.columns()) {
            if (col.role() == FieldRole.IGNORE) {
                continue;
            }
            String value = resolve(op.table(), col, seedRow, generator, idMap);
            if (col.role() == FieldRole.UPDATE_KEY) {
                where.put(col.name(), value);
            } else if ("UPDATE".equals(op.type())) {
                if (col.role() == FieldRole.UPDATE_SET) {
                    values.put(col.name(), value);
                }
            } else {
                values.put(col.name(), value);
            }
        }
        if ("UPDATE".equals(op.type())) {
            return new PlannedStatement("UPDATE", op.table(), values, where);
        }
        return new PlannedStatement("INSERT", op.table(), values, Map.of());
    }

    private static String resolve(
            String table,
            TemplateColumn col,
            Map<String, String> seedRow,
            ValueGenerator generator,
            Map<String, String> idMap
    ) {
        String old = seedRow.get(col.name());
        if (col.role() == FieldRole.FK_REF || col.role() == FieldRole.BIZ_KEY) {
            String refTable = col.refTable() == null ? table : col.refTable();
            String refColumn = col.refColumn() == null ? col.name() : col.refColumn();
            String keyed = refTable + "." + refColumn + "=" + old;
            if (old != null && idMap.containsKey(keyed)) {
                return idMap.get(keyed);
            }
            String latest = refTable + "." + refColumn;
            if (idMap.containsKey(latest)) {
                return idMap.get(latest);
            }
        }
        if (col.role() == FieldRole.UNIQUE_REGEN || col.role() == FieldRole.FORMATTED_RAND) {
            String mapKey = table + "." + col.name();
            String generated = generator.generate(mapKey);
            if (old != null) {
                idMap.put(mapKey + "=" + old, generated);
            }
            idMap.put(mapKey, generated);
            return generated;
        }
        if (col.role() == FieldRole.TIMESTAMP && (col.generator() == null || "NOW".equalsIgnoreCase(col.generator()))) {
            return String.valueOf(System.currentTimeMillis());
        }
        return old;
    }
}
