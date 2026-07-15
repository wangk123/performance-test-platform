package com.yr.perftest.platform.seed;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SeedCaptureAnalysisResultWriter {
    private static final int RESULT_ROWS_PER_CHUNK = 1000;

    private final PersistentSeedCaptureAnalysisResultRepository resultRepository;
    private final CaptureChunkStore chunkStore;
    private final DiskLowWaterGuard diskGuard;

    SeedCaptureAnalysisResultWriter(
            PersistentSeedCaptureAnalysisResultRepository resultRepository,
            CaptureChunkStore chunkStore,
            DiskLowWaterGuard diskGuard
    ) {
        this.resultRepository = resultRepository;
        this.chunkStore = chunkStore;
        this.diskGuard = diskGuard;
    }

    Metrics metrics(DiffChainResult chain, List<String> tableNames) {
        List<TableDiff> diffs = chain.intervals().stream()
                .flatMap(interval -> interval.tableDiffs().stream())
                .toList();
        int skippedTables = (int) diffs.stream()
                .filter(diff -> diff.status() == DiffStatus.UNCHANGED)
                .count();
        int fineChunks = (int) diffs.stream()
                .flatMap(diff -> diff.screeningEvidence().stream())
                .filter(evidence -> evidence.action() == ScreeningEvidence.Action.FINE_COMPARE)
                .count();
        long comparedRows = diffs.stream().mapToLong(TableDiff::comparedRows).sum();
        return new Metrics(tableNames.size(), comparedRows, skippedTables, fineChunks);
    }

    void persist(
            long analysisId,
            long projectId,
            long strategyId,
            DiffChainResult chain,
            List<String> tableNames,
            Metrics metrics,
            int candidateOperations,
            Runnable checkCancellation,
            ProgressReporter progressReporter
    ) {
        for (int tableIndex = 0; tableIndex < tableNames.size(); tableIndex++) {
            checkCancellation.run();
            String tableName = tableNames.get(tableIndex);
            List<Map<String, Object>> rows = detailRows(chain, tableName);
            String summary = SeedJson.write(tableSummary(chain, tableName));
            int chunkSeq = 0;
            if (rows.isEmpty()) {
                resultRepository.saveAndFlush(new PersistentSeedCaptureAnalysisResultRecord(
                        analysisId, tableName, chunkSeq, "TABLE_DIFF",
                        summary, null, null, 0
                ));
            }
            for (int start = 0; start < rows.size(); start += RESULT_ROWS_PER_CHUNK) {
                checkCancellation.run();
                diskGuard.checkDuringRun();
                int end = Math.min(start + RESULT_ROWS_PER_CHUNK, rows.size());
                CaptureChunkStore.ChunkManifest manifest;
                try (CaptureChunkStore.ChunkWriter writer = chunkStore.openAnalysisChunk(
                        projectId, strategyId, analysisId, tableName, chunkSeq
                )) {
                    rows.subList(start, end).forEach(writer::writeRow);
                    manifest = writer.commit();
                }
                resultRepository.saveAndFlush(new PersistentSeedCaptureAnalysisResultRecord(
                        analysisId, tableName, chunkSeq, "TABLE_DIFF", summary,
                        manifest.relativePath(), manifest.fileChecksum(), manifest.rowCount()
                ));
                chunkSeq++;
            }
            progressReporter.report(tableIndex + 1, tableName);
        }
    }

    Map<String, Object> summary(
            DiffChainResult chain,
            AdjacentInferenceResult inference,
            Metrics metrics,
            int candidateOperations
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("intervalCount", chain.intervals().size());
        summary.put("tableCount", metrics.totalTables());
        summary.put("comparedRows", metrics.comparedRows());
        summary.put("skippedTables", metrics.skippedTables());
        summary.put("fineScreenedChunks", metrics.fineScreenedChunks());
        summary.put("candidateOperationCount", candidateOperations);
        summary.put("warnings", chain.warnings());
        summary.put("risks", inference.risks());
        summary.put("tables", tableNames(chain).stream()
                .map(table -> tableSummary(chain, table))
                .toList());
        return summary;
    }

    Map<String, Map<String, String>> seedRows(DiffChainResult chain) {
        Map<String, Map<String, String>> rows = new LinkedHashMap<>();
        for (AdjacentDiff interval : chain.intervals()) {
            for (TableDiff diff : interval.tableDiffs()) {
                for (SnapshotDiffEngine.RowDiff rowDiff : diff.rowDiffs()) {
                    if (("INSERT".equals(rowDiff.kind()) || "UPDATE".equals(rowDiff.kind()))
                            && !rowDiff.after().isEmpty()) {
                        rows.put(diff.tableName(), rowDiff.after());
                    }
                }
            }
        }
        return rows;
    }

    private Map<String, Object> tableSummary(DiffChainResult chain, String tableName) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("tableName", tableName);
        summary.put("intervals", chain.intervals().stream()
                .map(interval -> interval.tableDiffs().stream()
                        .filter(diff -> tableName.equals(diff.tableName()))
                        .findFirst()
                        .map(this::diffSummary)
                        .orElseGet(() -> Map.of(
                                "status", DiffStatus.UNKNOWN,
                                "diagnostics", List.of("table data unavailable")
                        )))
                .toList());
        return summary;
    }

    private Map<String, Object> diffSummary(TableDiff diff) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("status", diff.status());
        summary.put("insertCount", diff.insertCount());
        summary.put("updateCount", diff.updateCount());
        summary.put("deleteCount", diff.deleteCount());
        summary.put("comparedRows", diff.comparedRows());
        summary.put("skippedRows", diff.skippedRows());
        summary.put("riskyNoPk", diff.riskyNoPk());
        summary.put("diagnostics", diff.diagnostics());
        summary.put("screeningEvidence", diff.screeningEvidence());
        return summary;
    }

    private List<Map<String, Object>> detailRows(DiffChainResult chain, String tableName) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int intervalIndex = 0; intervalIndex < chain.intervals().size(); intervalIndex++) {
            int currentInterval = intervalIndex;
            AdjacentDiff interval = chain.intervals().get(intervalIndex);
            interval.tableDiffs().stream()
                    .filter(diff -> tableName.equals(diff.tableName()))
                    .findFirst()
                    .ifPresent(diff -> diff.rowDiffs().forEach(rowDiff -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("intervalIndex", currentInterval);
                        row.put("beforeSampleId", interval.before().sampleId());
                        row.put("afterSampleId", interval.after().sampleId());
                        row.put("kind", rowDiff.kind());
                        row.put("primaryKey", rowDiff.primaryKey());
                        row.put("before", rowDiff.before());
                        row.put("after", rowDiff.after());
                        rows.add(row);
                    }));
        }
        return rows;
    }

    private List<String> tableNames(DiffChainResult chain) {
        return chain.intervals().stream()
                .flatMap(interval -> interval.tableDiffs().stream())
                .map(TableDiff::tableName)
                .distinct()
                .sorted()
                .toList();
    }

    @FunctionalInterface
    interface ProgressReporter {
        void report(int completedTables, String currentTable);
    }

    record Metrics(
            int totalTables,
            long comparedRows,
            int skippedTables,
            int fineScreenedChunks
    ) {
    }
}
