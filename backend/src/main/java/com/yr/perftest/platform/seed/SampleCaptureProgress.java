package com.yr.perftest.platform.seed;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

final class SampleCaptureProgress {
    private final PersistentSeedCaptureSampleRecord sample;
    private final PersistentSeedCaptureSampleRepository sampleRepository;
    private final PersistentSeedCaptureSampleTableRepository tableRepository;
    private final DatasourceCaptureLeaseService leaseService;
    private final Map<String, TableProgress> tables = new LinkedHashMap<>();
    private final Map<String, AtomicInteger> nextChunks = new LinkedHashMap<>();
    private long capturedRows;
    private long writtenBytes;
    private int activeWorkers;
    private int completedTables;
    private final Set<String> currentTables = new LinkedHashSet<>();

    SampleCaptureProgress(
            PersistentSeedCaptureSampleRecord sample,
            List<CaptureTableWork> work,
            PersistentSeedCaptureSampleRepository sampleRepository,
            PersistentSeedCaptureSampleTableRepository tableRepository,
            DatasourceCaptureLeaseService leaseService
    ) {
        this.sample = sample;
        this.sampleRepository = sampleRepository;
        this.tableRepository = tableRepository;
        this.leaseService = leaseService;
        work.stream().map(CaptureTableWork::table).distinct().forEach(table -> {
            tables.put(table, new TableProgress());
            nextChunks.put(table, new AtomicInteger());
        });
    }

    synchronized void register(CaptureTableWork unit) {
        TableProgress table = tables.get(unit.table());
        table.totalPartitions++;
        table.sample = unit.sample();
    }

    synchronized void workerStarted(CaptureTableWork unit) {
        activeWorkers++;
        currentTables.add(unit.table());
        persist("CAPTURING");
    }

    synchronized int nextChunk(String table) {
        return nextChunks.get(table).getAndIncrement();
    }

    synchronized void batchCommitted(
            CaptureTableWork unit,
            int chunkSeq,
            CaptureChunkStore.ChunkManifest manifest,
            List<Map<String, Object>> rows
    ) {
        TableProgress table = tables.get(unit.table());
        table.rows += manifest.rowCount();
        table.bytes += manifest.byteSize();
        table.chunkHashes.put(chunkSeq, manifest.contentHash());
        rows.forEach(table.multisetFingerprint::addRow);
        capturedRows += manifest.rowCount();
        writtenBytes += manifest.byteSize();
        table.sample.recordBatch(
                table.rows,
                unit.sample().getRiskyNoPk() ? null : tableContentHash(unit, table)
        );
        tableRepository.saveAndFlush(table.sample);
        persist("CAPTURING");
    }

    synchronized void failed(CaptureTableWork unit, String error) {
        tables.get(unit.table()).failed = true;
        unit.sample().markIncomplete(error);
        tableRepository.saveAndFlush(unit.sample());
        persist("CAPTURING");
    }

    synchronized void workerFinished(CaptureTableWork unit, boolean success) {
        TableProgress table = tables.get(unit.table());
        if (!success) {
            table.failed = true;
        }
        table.finishedPartitions++;
        activeWorkers--;
        if (table.finishedPartitions == table.totalPartitions) {
            currentTables.remove(unit.table());
            if (!table.failed && !isCancelRequested()) {
                table.sample.markCompleted(table.rows, tableContentHash(unit, table));
                tableRepository.saveAndFlush(table.sample);
                completedTables++;
            } else if (!table.sample.getIncomplete()) {
                table.sample.markIncomplete("capture canceled before table completion");
                tableRepository.saveAndFlush(table.sample);
            }
        }
        persist("CAPTURING");
    }

    boolean isCancelRequested() {
        return sampleRepository.findById(sample.getId())
                .map(current -> "CANCEL_REQUESTED".equals(current.getStatus()))
                .orElse(true);
    }

    synchronized void persist(String phase) {
        PersistentSeedCaptureSampleRecord current = sampleRepository.findById(sample.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "capture sample disappeared while persisting progress: " + sample.getId()
                ));
        current.updateProgress(
                phase,
                completedTables,
                tables.size(),
                SeedJson.write(currentTables.stream().sorted().toList()),
                capturedRows,
                writtenBytes,
                activeWorkers,
                Instant.now()
        );
        sampleRepository.saveAndFlush(current);
        leaseService.heartbeat(current.getDatasourceId(), sample.getId(), current.getHeartbeatAt());
    }

    private String tableContentHash(CaptureTableWork unit, TableProgress table) {
        if (unit.sample().getRiskyNoPk()) {
            return LogicalFingerprint.tableFingerprint(
                    unit.schemaHash(),
                    table.rows,
                    table.multisetFingerprint.finish()
            );
        }
        String chunks = table.chunkHashes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .reduce("", (left, right) -> left + "|" + right);
        String logicalHash = LogicalFingerprint.sha256(chunks.getBytes(StandardCharsets.UTF_8));
        return LogicalFingerprint.tableFingerprint(unit.schemaHash(), table.rows, logicalHash);
    }

    private static final class TableProgress {
        private int totalPartitions;
        private int finishedPartitions;
        private long rows;
        private long bytes;
        private boolean failed;
        private PersistentSeedCaptureSampleTableRecord sample;
        private final LogicalFingerprint.MultisetAccumulator multisetFingerprint =
                LogicalFingerprint.newMultisetAccumulator();
        private final Map<Integer, String> chunkHashes = new LinkedHashMap<>();
    }
}
