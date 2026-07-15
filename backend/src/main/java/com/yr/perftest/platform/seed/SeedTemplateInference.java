package com.yr.perftest.platform.seed;

import com.fasterxml.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class SeedTemplateInference {
    private SeedTemplateInference() {
    }

    static SeedTemplateDraft infer(PersistentSeedCaptureSessionRecord session, List<Map<String, Object>> samples) {
        List<TemplateOperation> operations = new ArrayList<>();
        List<String> tables = SeedJson.stringList(session.getTableSetJson());
        for (String table : tables) {
            TableMetadata meta = null;
            boolean risky = false;
            Map<String, List<String>> columnValues = new LinkedHashMap<>();
            Map<String, String> insertSeed = new LinkedHashMap<>();
            Map<String, String> updateKeys = new LinkedHashMap<>();
            Map<String, String> updateSets = new LinkedHashMap<>();
            boolean hasInsert = false;
            boolean hasUpdate = false;
            for (Map<String, Object> sample : samples) {
                Map<String, Object> tablesMap = castMap(sample.get("tables"));
                Map<String, Object> tableSample = tablesMap == null ? null : castMap(tablesMap.get(table));
                if (tableSample == null) {
                    continue;
                }
                risky = risky || Boolean.TRUE.equals(tableSample.get("riskyNoPk"));
                if (tableSample.get("metadata") != null) {
                    meta = SeedJson.read(SeedJson.write(tableSample.get("metadata")), new TypeReference<>() {
                    });
                }
                List<Map<String, Object>> diffs = SeedJson.read(SeedJson.write(tableSample.get("diffs")), new TypeReference<>() {
                });
                for (Map<String, Object> diff : diffs) {
                    String kind = String.valueOf(diff.get("kind"));
                    Map<String, String> after = castStringMap(diff.get("after"));
                    if ("INSERT".equals(kind)) {
                        hasInsert = true;
                        for (Map.Entry<String, String> e : after.entrySet()) {
                            columnValues.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue());
                            insertSeed.putIfAbsent(e.getKey(), e.getValue());
                        }
                    } else if ("UPDATE".equals(kind)) {
                        hasUpdate = true;
                        Map<String, String> before = castStringMap(diff.get("before"));
                        for (Map.Entry<String, String> e : after.entrySet()) {
                            if (meta != null && meta.primaryKeyColumns().contains(e.getKey())) {
                                updateKeys.put(e.getKey(), e.getValue());
                            } else if (!Objects.equals(before.get(e.getKey()), e.getValue())) {
                                updateSets.put(e.getKey(), e.getValue());
                                columnValues.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue());
                            }
                        }
                    }
                }
            }
            if (meta == null) {
                continue;
            }
            if (risky) {
                operations.add(new TemplateOperation("INSERT", table, true, List.of()));
                continue;
            }
            if (hasInsert) {
                List<TemplateColumn> columns = new ArrayList<>();
                for (String col : insertSeed.keySet()) {
                    InferredColumn inferred = RoleInferenceEngine.inferColumn(
                            new ColumnSamples(col, columnValues.getOrDefault(col, List.of(insertSeed.get(col)))),
                            meta,
                            List.of()
                    );
                    columns.add(toColumn(inferred));
                }
                operations.add(new TemplateOperation("INSERT", table, false, columns));
            }
            if (hasUpdate) {
                List<TemplateColumn> columns = new ArrayList<>();
                for (String col : updateKeys.keySet()) {
                    columns.add(new TemplateColumn(col, FieldRole.UPDATE_KEY, Confidence.HIGH, "primary/business key", null, null, null, true));
                }
                for (String col : updateSets.keySet()) {
                    InferredColumn inferred = RoleInferenceEngine.inferColumn(
                            new ColumnSamples(col, columnValues.getOrDefault(col, List.of(updateSets.get(col)))),
                            meta,
                            List.of()
                    );
                    columns.add(new TemplateColumn(
                            col,
                            FieldRole.UPDATE_SET,
                            inferred.confidence(),
                            inferred.rationale(),
                            null,
                            null,
                            inferred.suggestedGenerator(),
                            false
                    ));
                }
                operations.add(new TemplateOperation("UPDATE", table, false, columns));
            }
        }
        return new SeedTemplateDraft(operations);
    }

    static Map<String, Map<String, String>> extractSeedRows(List<Map<String, Object>> samples) {
        Map<String, Map<String, String>> seedRows = new LinkedHashMap<>();
        if (samples.isEmpty()) {
            return seedRows;
        }
        Map<String, Object> last = samples.get(samples.size() - 1);
        Map<String, Object> tables = castMap(last.get("tables"));
        if (tables == null) {
            return seedRows;
        }
        for (Map.Entry<String, Object> entry : tables.entrySet()) {
            Map<String, Object> tableSample = castMap(entry.getValue());
            if (tableSample == null) {
                continue;
            }
            List<Map<String, Object>> diffs = SeedJson.read(SeedJson.write(tableSample.get("diffs")), new TypeReference<>() {
            });
            for (Map<String, Object> diff : diffs) {
                if ("INSERT".equals(String.valueOf(diff.get("kind"))) || "UPDATE".equals(String.valueOf(diff.get("kind")))) {
                    Map<String, String> after = castStringMap(diff.get("after"));
                    if (!after.isEmpty()) {
                        seedRows.put(entry.getKey(), after);
                        break;
                    }
                }
            }
        }
        return seedRows;
    }

    private static TemplateColumn toColumn(InferredColumn inferred) {
        return new TemplateColumn(
                inferred.column(),
                inferred.role(),
                inferred.confidence(),
                inferred.rationale(),
                inferred.refTable(),
                inferred.refColumn(),
                inferred.suggestedGenerator(),
                false
        );
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    static Map<String, String> castStringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            result.put(String.valueOf(e.getKey()), e.getValue() == null ? null : String.valueOf(e.getValue()));
        }
        return result;
    }
}
