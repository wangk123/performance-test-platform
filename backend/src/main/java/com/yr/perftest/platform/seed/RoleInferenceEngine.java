package com.yr.perftest.platform.seed;

import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

public final class RoleInferenceEngine {
    private static final Pattern MOBILE = Pattern.compile("^1\\d{10}$");

    private RoleInferenceEngine() {
    }

    public static InferredColumn inferColumn(ColumnSamples samples, TableMetadata meta, List<CrossTableHit> crossHits) {
        String column = samples.column();
        List<String> values = samples.values() == null ? List.of() : samples.values();
        FkRef fk = meta.foreignKeys() == null ? null : meta.foreignKeys().get(column);
        if (fk != null) {
            return new InferredColumn(column, FieldRole.FK_REF, Confidence.HIGH,
                    "formal foreign key to " + fk.table() + "." + fk.column(),
                    fk.table(), fk.column(), null);
        }
        boolean allSame = !values.isEmpty() && values.stream().distinct().count() == 1;
        boolean allDifferent = values.size() > 1 && new HashSet<>(values).size() == values.size();
        boolean unique = meta.uniqueColumns() != null && meta.uniqueColumns().contains(column);
        if (allDifferent && unique) {
            return new InferredColumn(column, FieldRole.UNIQUE_REGEN, Confidence.HIGH,
                    "differs every sample and unique constraint", null, null, null);
        }
        if (looksLikeMobile(values)) {
            return new InferredColumn(column, FieldRole.FORMATTED_RAND, Confidence.MEDIUM,
                    "values match mobile morphology", null, null, "randomMobile");
        }
        if (allSame) {
            return new InferredColumn(column, FieldRole.LITERAL, Confidence.HIGH,
                    "identical across samples", null, null, null);
        }
        if (allDifferent) {
            return new InferredColumn(column, FieldRole.UNIQUE_REGEN, Confidence.LOW,
                    "differs every sample without unique index", null, null, null);
        }
        return new InferredColumn(column, FieldRole.LITERAL, Confidence.LOW,
                "insufficient signal", null, null, null);
    }

    private static boolean looksLikeMobile(List<String> values) {
        if (values.isEmpty()) {
            return false;
        }
        return values.stream().allMatch(v -> v != null && MOBILE.matcher(v).matches());
    }
}
