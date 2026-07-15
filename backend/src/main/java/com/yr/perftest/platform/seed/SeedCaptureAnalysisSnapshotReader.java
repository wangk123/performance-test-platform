package com.yr.perftest.platform.seed;

import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class SeedCaptureAnalysisSnapshotReader {
    private final PersistentSeedCaptureSampleRepository sampleRepository;
    private final PersistentSeedCaptureSampleTableRepository tableRepository;
    private final PersistentSeedCaptureChunkRepository chunkRepository;
    private final CaptureChunkStore chunkStore;

    SeedCaptureAnalysisSnapshotReader(
            PersistentSeedCaptureSampleRepository sampleRepository,
            PersistentSeedCaptureSampleTableRepository tableRepository,
            PersistentSeedCaptureChunkRepository chunkRepository,
            CaptureChunkStore chunkStore
    ) {
        this.sampleRepository = sampleRepository;
        this.tableRepository = tableRepository;
        this.chunkRepository = chunkRepository;
        this.chunkStore = chunkStore;
    }

    List<PersistentSeedCaptureSampleRecord> inputSamples(
            PersistentSeedCaptureAnalysisRecord analysis
    ) {
        List<Long> ids = SeedJson.read(
                analysis.getInputSampleIdsJson(),
                new TypeReference<List<Long>>() {
                }
        );
        return ids.stream()
                .map(id -> sampleRepository.findByIdAndProjectId(id, analysis.getProjectId())
                        .orElseThrow(() -> new SeedValidationException(
                                "analysis input sample not found: " + id
                        )))
                .sorted(Comparator.comparing(PersistentSeedCaptureSampleRecord::getCaptureStartedAt)
                        .thenComparing(PersistentSeedCaptureSampleRecord::getSampleSeq))
                .toList();
    }

    List<String> tableNames(List<SampleSnapshotView> snapshots) {
        return snapshots.stream()
                .flatMap(snapshot -> snapshot.tables().keySet().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .sorted()
                .toList();
    }

    SampleSnapshotView snapshot(PersistentSeedCaptureSampleRecord sample) {
        Map<String, TableSnapshotView> tables = new LinkedHashMap<>();
        for (PersistentSeedCaptureSampleTableRecord table :
                tableRepository.findBySampleIdOrderByTableNameAsc(sample.getId())) {
            TableMetadata metadata = metadata(table.getTableName(), table.getSchemaJson());
            List<ChunkSnapshotView> chunks = chunkRepository
                    .findBySampleIdAndTableNameOrderByChunkSeqAsc(
                            sample.getId(),
                            table.getTableName()
                    )
                    .stream()
                    .map(record -> chunk(record))
                    .toList();
            boolean complete = "SUCCEEDED".equals(table.getStatus())
                    && !Boolean.TRUE.equals(table.getIncomplete())
                    && chunks.stream().allMatch(ChunkSnapshotView::checksumVerified);
            tables.put(table.getTableName(), new TableSnapshotView(
                    table.getTableName(),
                    metadata,
                    table.getSchemaHash(),
                    table.getRowCount(),
                    table.getContentHash(),
                    complete,
                    chunks
            ));
        }
        return new SampleSnapshotView(
                sample.getId(),
                sample.getStrategyId(),
                sample.getSampleSeq(),
                sample.getCaptureStartedAt(),
                sample.getConfigVersion(),
                sample.getStatus(),
                !"SUCCEEDED".equals(sample.getStatus())
                        || Boolean.TRUE.equals(sample.getIncomplete()),
                tables
        );
    }

    private ChunkSnapshotView chunk(PersistentSeedCaptureChunkRecord record) {
        CaptureChunkStore.ChunkManifest manifest = new CaptureChunkStore.ChunkManifest(
                record.getRelativePath(),
                record.getRowCount(),
                record.getContentHash(),
                record.getFileChecksum(),
                record.getByteSize()
        );
        boolean verified = false;
        if ("READY".equals(record.getStatus())
                && record.getRelativePath() != null
                && record.getFileChecksum() != null) {
            try {
                verified = chunkStore.verifyChecksum(manifest);
            } catch (RuntimeException ignored) {
                verified = false;
            }
        }
        return new ChunkSnapshotView(
                record.getChunkSeq(),
                record.getPkRangeStart(),
                record.getPkRangeEnd(),
                record.getRowCount(),
                record.getContentHash(),
                verified,
                () -> chunkStore.readRows(manifest)
        );
    }

    private TableMetadata metadata(String tableName, String schemaJson) {
        try {
            TableMetadata value = SeedJson.read(schemaJson, new TypeReference<>() {
            });
            if (value != null
                    && value.primaryKeyColumns() != null
                    && value.uniqueColumns() != null
                    && value.foreignKeys() != null) {
                return value;
            }
        } catch (RuntimeException ignored) {
        }
        return new TableMetadata(tableName, List.of(), Set.of(), Map.of());
    }
}
