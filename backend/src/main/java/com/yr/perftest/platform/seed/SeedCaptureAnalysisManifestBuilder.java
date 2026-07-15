package com.yr.perftest.platform.seed;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SeedCaptureAnalysisManifestBuilder {
    private final PersistentSeedCaptureSampleTableRepository tableRepository;
    private final PersistentSeedCaptureChunkRepository chunkRepository;

    SeedCaptureAnalysisManifestBuilder(
            PersistentSeedCaptureSampleTableRepository tableRepository,
            PersistentSeedCaptureChunkRepository chunkRepository
    ) {
        this.tableRepository = tableRepository;
        this.chunkRepository = chunkRepository;
    }

    Map<String, Object> build(
            PersistentSeedCaptureAnalysisRecord analysis,
            PersistentSeedCaptureStrategyRecord strategy,
            List<PersistentSeedCaptureSampleRecord> samples
    ) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("analysisId", analysis.getId());
        manifest.put("projectId", analysis.getProjectId());
        manifest.put("strategyId", strategy.getId());
        manifest.put("strategyVersion", samples.stream()
                .map(PersistentSeedCaptureSampleRecord::getConfigVersion)
                .distinct()
                .toList());
        manifest.put("samples", samples.stream().map(this::sample).toList());
        return manifest;
    }

    private Map<String, Object> sample(PersistentSeedCaptureSampleRecord sample) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("id", sample.getId());
        manifest.put("sampleSeq", sample.getSampleSeq());
        manifest.put("captureStartedAt", sample.getCaptureStartedAt());
        manifest.put("status", sample.getStatus());
        manifest.put("configVersion", sample.getConfigVersion());
        manifest.put("incomplete", sample.getIncomplete());
        List<Map<String, Object>> tables = new ArrayList<>();
        for (PersistentSeedCaptureSampleTableRecord table :
                tableRepository.findBySampleIdOrderByTableNameAsc(sample.getId())) {
            Map<String, Object> tableManifest = new LinkedHashMap<>();
            tableManifest.put("tableName", table.getTableName());
            tableManifest.put("schemaHash", table.getSchemaHash());
            tableManifest.put("rowCount", table.getRowCount());
            tableManifest.put("contentHash", table.getContentHash());
            tableManifest.put("status", table.getStatus());
            tableManifest.put("incomplete", table.getIncomplete());
            tableManifest.put("chunks", chunkRepository
                    .findBySampleIdAndTableNameOrderByChunkSeqAsc(sample.getId(), table.getTableName())
                    .stream()
                    .map(this::chunk)
                    .toList());
            tables.add(tableManifest);
        }
        manifest.put("tables", tables);
        return manifest;
    }

    private Map<String, Object> chunk(PersistentSeedCaptureChunkRecord chunk) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("chunkSeq", chunk.getChunkSeq());
        manifest.put("rowCount", chunk.getRowCount());
        manifest.put("contentHash", chunk.getContentHash());
        manifest.put("relativePath", chunk.getRelativePath());
        manifest.put("fileChecksum", chunk.getFileChecksum());
        manifest.put("status", chunk.getStatus());
        return manifest;
    }
}
