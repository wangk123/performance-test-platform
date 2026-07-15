package com.yr.perftest.platform.seed;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class AdjacentSampleDiffEngine {
    private AdjacentSampleDiffEngine() {
    }

    public static DiffChainResult analyze(List<SampleSnapshotView> samples) {
        Objects.requireNonNull(samples, "samples");
        if (samples.size() < 3) {
            throw new IllegalArgumentException("at least three terminal samples are required");
        }

        List<SampleSnapshotView> ordered = new ArrayList<>(samples);
        ordered.forEach(AdjacentSampleDiffEngine::requireTerminal);
        long strategyId = ordered.get(0).strategyId();
        if (ordered.stream().anyMatch(sample -> sample.strategyId() != strategyId)) {
            throw new IllegalArgumentException("all samples must belong to one strategy");
        }
        ordered.sort(
                Comparator.comparing(SampleSnapshotView::captureStartedAt)
                        .thenComparingInt(SampleSnapshotView::sampleSeq)
        );

        List<DiffWarning> warnings = new ArrayList<>();
        for (int i = 1; i < ordered.size(); i++) {
            SampleSnapshotView previous = ordered.get(i - 1);
            SampleSnapshotView current = ordered.get(i);
            if (current.sampleSeq() != previous.sampleSeq() + 1) {
                warnings.add(new DiffWarning(
                        DiffWarning.Code.SEQUENCE_GAP,
                        "sample sequence gap between "
                                + previous.sampleSeq() + " and " + current.sampleSeq()
                ));
            }
            if (current.configVersion() != previous.configVersion()) {
                warnings.add(new DiffWarning(
                        DiffWarning.Code.INCOMPATIBLE_STRATEGY_VERSION,
                        "strategy configuration changed between samples "
                                + previous.sampleId() + " and " + current.sampleId()
                ));
            }
        }

        List<AdjacentDiff> intervals = new ArrayList<>(ordered.size() - 1);
        for (int i = 0; i < ordered.size() - 1; i++) {
            SampleSnapshotView before = ordered.get(i);
            SampleSnapshotView after = ordered.get(i + 1);
            List<TableDiff> tableDiffs = AdjacentTableDiffEngine.diffTables(before, after);
            tableDiffs.stream()
                    .filter(table -> table.diagnostics().stream()
                            .anyMatch(message -> message.startsWith("incompatible schema")))
                    .forEach(table -> warnings.add(new DiffWarning(
                            DiffWarning.Code.INCOMPATIBLE_SCHEMA,
                            "incompatible schema for " + table.tableName()
                    )));
            intervals.add(new AdjacentDiff(i, before, after, tableDiffs));
        }
        return new DiffChainResult(ordered, intervals, warnings);
    }

    private static void requireTerminal(SampleSnapshotView sample) {
        if (!sample.terminal()) {
            throw new IllegalArgumentException(
                    "sample " + sample.sampleId() + " is not terminal"
            );
        }
    }
}
