package com.yr.perftest.platform.seed;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class AdjacentDiffInference {
    private AdjacentDiffInference() {
    }

    public static AdjacentInferenceResult infer(DiffChainResult chain) {
        Objects.requireNonNull(chain, "chain");
        int intervalCount = chain.intervals().size();
        Map<String, TableAccumulator> accumulators = new LinkedHashMap<>();
        for (int intervalIndex = 0; intervalIndex < chain.intervals().size(); intervalIndex++) {
            AdjacentDiff interval = chain.intervals().get(intervalIndex);
            for (TableDiff diff : interval.tableDiffs()) {
                TableAccumulator accumulator = accumulators.computeIfAbsent(
                        diff.tableName(),
                        TableAccumulator::new
                );
                accumulator.totalIntervals++;
                accumulator.metadata = metadataFor(interval, diff.tableName(), accumulator.metadata);
                accumulator.risky = accumulator.risky || diff.riskyNoPk();
                if (diff.status() == DiffStatus.UNKNOWN) {
                    accumulator.addRisks(diff.diagnostics());
                    continue;
                }
                accumulator.knownIntervals++;
                for (SnapshotDiffEngine.RowDiff rowDiff : diff.rowDiffs()) {
                    if ("DELETE".equals(rowDiff.kind())) {
                        accumulator.addRisk("DELETE observed; retained as diagnostic only");
                    } else if ("INSERT".equals(rowDiff.kind())) {
                        accumulator.recordOperation("INSERT", intervalIndex);
                        accumulator.recordInsertFields(rowDiff.after());
                    } else if ("UPDATE".equals(rowDiff.kind())) {
                        accumulator.recordOperation("UPDATE", intervalIndex);
                        accumulator.recordUpdateFields(rowDiff);
                    }
                }
                accumulator.addRisks(diff.diagnostics());
                if (diff.riskyNoPk()) {
                    accumulator.addRisk("no primary key; executable inference is disabled");
                }
            }
        }

        List<AdjacentTableInference> tables = new ArrayList<>();
        List<TemplateOperation> templateOperations = new ArrayList<>();
        List<String> overallRisks = new ArrayList<>();
        chain.warnings().forEach(warning -> overallRisks.add(warning.message()));
        for (TableAccumulator accumulator : accumulators.values()) {
            AdjacentTableInference table = accumulator.toInference(intervalCount);
            tables.add(table);
            table.risks().forEach(risk -> overallRisks.add(accumulator.table + ": " + risk));
            templateOperations.addAll(accumulator.templateOperations(intervalCount));
        }
        return new AdjacentInferenceResult(
                new SeedTemplateDraft(templateOperations),
                tables,
                distinct(overallRisks)
        );
    }

    private static TableMetadata metadataFor(
            AdjacentDiff interval,
            String tableName,
            TableMetadata fallback
    ) {
        TableSnapshotView before = interval.before().tables().get(tableName);
        if (before != null && before.metadata() != null) {
            return before.metadata();
        }
        TableSnapshotView after = interval.after().tables().get(tableName);
        if (after != null && after.metadata() != null) {
            return after.metadata();
        }
        return fallback;
    }

    private static Confidence confidence(double coverage) {
        if (coverage >= 1.0) {
            return Confidence.HIGH;
        }
        if (coverage >= 0.5) {
            return Confidence.MEDIUM;
        }
        return Confidence.LOW;
    }

    private static List<String> distinct(List<String> values) {
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    private static final class TableAccumulator {
        private final String table;
        private TableMetadata metadata;
        private int totalIntervals;
        private int knownIntervals;
        private boolean risky;
        private final Map<String, Set<Integer>> operationIntervals = new LinkedHashMap<>();
        private final Map<String, Map<String, List<String>>> fieldValues = new LinkedHashMap<>();
        private final Set<String> risks = new LinkedHashSet<>();

        private TableAccumulator(String table) {
            this.table = table;
        }

        private void recordOperation(String type, int intervalIndex) {
            operationIntervals
                    .computeIfAbsent(type, ignored -> new LinkedHashSet<>())
                    .add(intervalIndex);
        }

        private void recordInsertFields(Map<String, String> after) {
            after.forEach((column, value) -> addField("INSERT", column, value));
        }

        private void recordUpdateFields(SnapshotDiffEngine.RowDiff diff) {
            Set<String> primaryKeyColumns = metadata == null
                    ? Set.of()
                    : new LinkedHashSet<>(metadata.primaryKeyColumns());
            diff.after().forEach((column, value) -> {
                if (!primaryKeyColumns.contains(column)
                        && !Objects.equals(diff.before().get(column), value)) {
                    addField("UPDATE", column, value);
                }
            });
        }

        private void addField(String operation, String column, String value) {
            fieldValues
                    .computeIfAbsent(operation, ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(column, ignored -> new ArrayList<>())
                    .add(value);
        }

        private void addRisks(List<String> values) {
            values.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .forEach(this::addRisk);
        }

        private void addRisk(String risk) {
            risks.add(risk);
        }

        private AdjacentTableInference toInference(int intervalCount) {
            double coverage = ratio(knownIntervals, intervalCount);
            List<AdjacentOperationInference> operations = operationInferences(intervalCount);
            String rationale = "known coverage " + knownIntervals + "/" + intervalCount
                    + " intervals; operations observed in "
                    + operationIntervals.values().stream().mapToInt(Set::size).max().orElse(0)
                    + "/" + intervalCount + " intervals";
            return new AdjacentTableInference(
                    table,
                    confidence(coverage),
                    rationale,
                    coverage,
                    risky,
                    operations,
                    new ArrayList<>(risks)
            );
        }

        private List<AdjacentOperationInference> operationInferences(int intervalCount) {
            List<AdjacentOperationInference> result = new ArrayList<>();
            for (String type : List.of("INSERT", "UPDATE")) {
                Set<Integer> observedIntervals = operationIntervals.get(type);
                if (observedIntervals == null) {
                    continue;
                }
                int observed = observedIntervals.size();
                double coverage = ratio(observed, intervalCount);
                boolean operationRisky = risky;
                List<InferredColumn> columns = new ArrayList<>();
                if ("UPDATE".equals(type) && risky) {
                    addRisk("UPDATE identity is unreliable without a primary key");
                    continue;
                }
                if ("UPDATE".equals(type) && metadata != null) {
                    for (String primaryKey : metadata.primaryKeyColumns()) {
                        columns.add(new InferredColumn(
                                primaryKey,
                                FieldRole.UPDATE_KEY,
                                Confidence.HIGH,
                                "primary key",
                                null,
                                null,
                                null
                        ));
                    }
                }
                Map<String, List<String>> values = fieldValues.getOrDefault(type, Map.of());
                if (!operationRisky && metadata != null) {
                    for (Map.Entry<String, List<String>> entry : values.entrySet()) {
                        columns.add(RoleInferenceEngine.inferColumn(
                                new ColumnSamples(entry.getKey(), entry.getValue()),
                                metadata,
                                List.of()
                        ));
                    }
                }
                result.add(new AdjacentOperationInference(
                        type,
                        confidence(coverage),
                        type + " observed in " + observed + "/" + intervalCount + " intervals",
                        coverage,
                        operationRisky,
                        columns
                ));
            }
            return result;
        }

        private List<TemplateOperation> templateOperations(int intervalCount) {
            List<TemplateOperation> result = new ArrayList<>();
            for (AdjacentOperationInference operation : operationInferences(intervalCount)) {
                if (operation.risky()) {
                    result.add(new TemplateOperation(operation.type(), table, true, List.of()));
                    continue;
                }
                result.add(new TemplateOperation(
                        operation.type(),
                        table,
                        false,
                        operation.columns().stream()
                                .map(SeedTemplateInference::toColumn)
                                .toList()
                ));
            }
            return result;
        }

        private static double ratio(int numerator, int denominator) {
            return denominator == 0 ? 0 : (double) numerator / denominator;
        }
    }
}
