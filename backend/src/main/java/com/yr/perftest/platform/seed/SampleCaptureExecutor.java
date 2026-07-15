package com.yr.perftest.platform.seed;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class SampleCaptureExecutor {
    private final PersistentSeedCaptureSampleRepository sampleRepository;
    private final PersistentSeedDatasourceRepository datasourceRepository;
    private final PersistentSeedCaptureSampleTableRepository tableRepository;
    private final PersistentSeedCaptureChunkRepository chunkRepository;
    private final DatasourceCaptureLeaseService leaseService;
    private final CaptureChunkStore chunkStore;
    private final DiskLowWaterGuard diskGuard;
    private final CaptureRowSourceFactory sourceFactory;
    private final CapturePartitionPlanner partitionPlanner;

    public SampleCaptureExecutor(
            PersistentSeedCaptureSampleRepository sampleRepository,
            PersistentSeedDatasourceRepository datasourceRepository,
            PersistentSeedCaptureSampleTableRepository tableRepository,
            PersistentSeedCaptureChunkRepository chunkRepository,
            DatasourceCaptureLeaseService leaseService,
            CaptureChunkStore chunkStore,
            DiskLowWaterGuard diskGuard,
            CaptureRowSourceFactory sourceFactory,
            CapturePartitionPlanner partitionPlanner
    ) {
        this.sampleRepository = sampleRepository;
        this.datasourceRepository = datasourceRepository;
        this.tableRepository = tableRepository;
        this.chunkRepository = chunkRepository;
        this.leaseService = leaseService;
        this.chunkStore = chunkStore;
        this.diskGuard = diskGuard;
        this.sourceFactory = sourceFactory;
        this.partitionPlanner = partitionPlanner;
    }

    public void submit(long sampleId) {
        PersistentSeedCaptureSampleRecord sample = requireSample(sampleId);
        int threadCount = CaptureConfiguration.from(sample).threadCount();
        ExecutorService workers = Executors.newFixedThreadPool(threadCount);
        workers.submit(() -> {
            try {
                capture(sampleId);
            } finally {
                workers.shutdown();
            }
        });
    }

    public void capture(long sampleId) {
        PersistentSeedCaptureSampleRecord sample = requireSample(sampleId);
        long datasourceId = sample.getDatasourceId();
        boolean leaseOwned = false;
        try {
            Optional<Long> activeSample = leaseService.findActiveSampleId(datasourceId);
            if (activeSample.isPresent() && activeSample.get() != sampleId) {
                throw new DatasourceCaptureLeaseService.ActiveCaptureException(
                        "datasource " + datasourceId
                                + " already has an active capture sample " + activeSample.get(),
                        activeSample.get()
                );
            }
            if (activeSample.isEmpty()) {
                leaseService.acquire(datasourceId, sampleId);
            }
            leaseOwned = true;

            if ("CANCEL_REQUESTED".equals(sample.getStatus())) {
                sample.cancel();
                sampleRepository.saveAndFlush(sample);
                return;
            }

            sample.markPreparing();
            sampleRepository.saveAndFlush(sample);
            diskGuard.checkBeforeStart();

            CaptureConfiguration configuration = CaptureConfiguration.from(sample);
            PersistentSeedDatasourceRecord datasource = datasourceRepository
                    .findByIdAndProjectId(datasourceId, sample.getProjectId())
                    .orElseThrow(() -> new SeedValidationException(
                            "datasource not found: " + datasourceId
                    ));
            List<CaptureTableWork> work = prepare(sample, datasource, configuration);
            SampleCaptureProgress progress = new SampleCaptureProgress(
                    sample,
                    work,
                    sampleRepository,
                    tableRepository,
                    leaseService
            );
            progress.persist("PREPARING");

            sample = sampleRepository.findById(sampleId)
                    .orElseThrow(() -> new IllegalStateException(
                            "capture sample disappeared after preparation: " + sampleId
                    ));
            if ("CANCEL_REQUESTED".equals(sample.getStatus())) {
                sample.cancel();
                sampleRepository.saveAndFlush(sample);
                return;
            }
            sample.markCapturing();
            sampleRepository.saveAndFlush(sample);
            progress.persist("CAPTURING");

            AtomicReference<Throwable> failure = new AtomicReference<>();
            runWorkers(sampleId, datasource, configuration, work, progress, failure);

            sample = sampleRepository.findById(sampleId)
                    .orElseThrow(() -> new IllegalStateException(
                            "capture sample disappeared after workers: " + sampleId
                    ));
            if ("CANCEL_REQUESTED".equals(sample.getStatus())) {
                sample.cancel();
            } else if (failure.get() != null) {
                sample.fail(message(failure.get()), true);
            } else {
                sample.succeed();
            }
            sampleRepository.saveAndFlush(sample);
        } catch (Exception ex) {
            markFailure(sampleId, ex);
        } finally {
            if (leaseOwned) {
                leaseService.release(datasourceId, sampleId);
            }
        }
    }

    @Transactional
    public void requestCancel(long sampleId) {
        PersistentSeedCaptureSampleRecord sample = requireSample(sampleId);
        if (CaptureSampleStateMachine.isActive(sample.getStatus())
                && !"CANCEL_REQUESTED".equals(sample.getStatus())) {
            sample.requestCancel();
            sampleRepository.saveAndFlush(sample);
        }
    }

    private List<CaptureTableWork> prepare(
            PersistentSeedCaptureSampleRecord sample,
            PersistentSeedDatasourceRecord datasource,
            CaptureConfiguration configuration
    ) throws Exception {
        List<CaptureTableWork> work = new ArrayList<>();
        try (CaptureRowSource source = sourceFactory.open(datasource)) {
            Set<String> tables = CaptureFilterEvaluator.evaluate(
                    source.listTables(datasource.getDatabaseName()),
                    configuration.includes(),
                    configuration.excludes()
            );
            for (String table : tables) {
                TableMetadata metadata = source.readMetadata(table);
                String schemaHash = LogicalFingerprint.schemaFingerprint(
                        List.of(metadata.table(), metadata.primaryKeyColumns(), metadata.uniqueColumns())
                );
                PersistentSeedCaptureSampleTableRecord tableRecord =
                        tableRepository.saveAndFlush(new PersistentSeedCaptureSampleTableRecord(
                                sample.getId(),
                                table,
                                SeedJson.write(metadata),
                                schemaHash,
                                0L,
                                null,
                                metadata.primaryKeyColumns().isEmpty(),
                                "CAPTURING"
                        ));
                List<CapturePartitionPlanner.Partition> partitions = partitions(
                        source,
                        metadata,
                        configuration.batchRows()
                );
                if (partitions.isEmpty()) {
                    partitions = partitionPlanner.plan(metadata, configuration.batchRows());
                }
                for (CapturePartitionPlanner.Partition partition : partitions) {
                    work.add(new CaptureTableWork(
                            sample.getProjectId(),
                            sample.getStrategyId(),
                            table,
                            metadata,
                            tableRecord,
                            partition,
                            schemaHash
                    ));
                }
            }
        }
        return work;
    }

    private List<CapturePartitionPlanner.Partition> partitions(
            CaptureRowSource source,
            TableMetadata metadata,
            int batchRows
    ) throws Exception {
        Optional<CapturePartitionPlanner.NumericPrimaryKeyRange> range =
                source.numericPrimaryKeyRange(metadata);
        if (range.isPresent()) {
            return partitionPlanner.plan(metadata, range.get(), batchRows);
        }
        if (metadata.primaryKeyColumns().size() > 1) {
            List<CapturePartitionPlanner.PrimaryKeyColumn> columns = metadata.primaryKeyColumns().stream()
                    .map(column -> new CapturePartitionPlanner.PrimaryKeyColumn(
                            column,
                            CapturePartitionPlanner.KeyType.OTHER
                    ))
                    .toList();
            return partitionPlanner.plan(metadata, columns, batchRows);
        }
        return partitionPlanner.plan(metadata, batchRows);
    }

    private void runWorkers(
            long sampleId,
            PersistentSeedDatasourceRecord datasource,
            CaptureConfiguration configuration,
            List<CaptureTableWork> work,
            SampleCaptureProgress progress,
            AtomicReference<Throwable> failure
    ) {
        if (work.isEmpty()) {
            return;
        }
        ExecutorService workers = Executors.newFixedThreadPool(configuration.threadCount());
        List<Future<?>> futures = new ArrayList<>();
        for (CaptureTableWork unit : work) {
            progress.register(unit);
        }
        for (CaptureTableWork unit : work) {
            futures.add(workers.submit(() -> processWork(
                    sampleId,
                    datasource,
                    configuration,
                    unit,
                    progress,
                    failure
            )));
        }
        workers.shutdown();
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                failure.compareAndSet(null, ex);
            } catch (ExecutionException ex) {
                failure.compareAndSet(null, ex.getCause());
            }
        }
    }

    private void processWork(
            long sampleId,
            PersistentSeedDatasourceRecord datasource,
            CaptureConfiguration configuration,
            CaptureTableWork unit,
            SampleCaptureProgress progress,
            AtomicReference<Throwable> failure
    ) {
        progress.workerStarted(unit);
        boolean completed = false;
        try (CaptureRowSource source = sourceFactory.open(datasource)) {
            source.readBatches(unit.partition(), configuration.batchRows(), rows -> {
                if (failure.get() != null || progress.isCancelRequested()) {
                    return false;
                }
                for (int start = 0; start < rows.size(); start += configuration.batchRows()) {
                    if (failure.get() != null || progress.isCancelRequested()) {
                        return false;
                    }
                    int end = Math.min(start + configuration.batchRows(), rows.size());
                    writeBatch(sampleId, unit, rows.subList(start, end), progress);
                }
                return failure.get() == null && !progress.isCancelRequested();
            });
            completed = true;
        } catch (Exception ex) {
            failure.compareAndSet(null, ex);
            progress.failed(unit, message(ex));
        } finally {
            progress.workerFinished(unit, completed);
        }
    }

    private void writeBatch(
            long sampleId,
            CaptureTableWork unit,
            List<Map<String, Object>> rows,
            SampleCaptureProgress progress
    ) {
        if (rows.isEmpty()) {
            return;
        }
        diskGuard.checkDuringRun();
        int chunkSeq = progress.nextChunk(unit.table());
        CaptureChunkStore.ChunkManifest manifest;
        try (CaptureChunkStore.ChunkWriter writer = chunkStore.openChunk(
                unit.projectId(),
                unit.strategyId(),
                sampleId,
                unit.table(),
                chunkSeq
        )) {
            for (Map<String, Object> row : rows) {
                writer.writeRow(row);
            }
            manifest = writer.commit();
        }
        chunkRepository.saveAndFlush(new PersistentSeedCaptureChunkRecord(
                sampleId,
                unit.table(),
                chunkSeq,
                unit.partition().lowerBound(),
                unit.partition().upperBound(),
                manifest.rowCount(),
                manifest.contentHash(),
                manifest.relativePath(),
                manifest.fileChecksum(),
                "READY",
                manifest.byteSize()
        ));
            progress.batchCommitted(unit, chunkSeq, manifest, rows);
    }

    private void markFailure(long sampleId, Exception failure) {
        PersistentSeedCaptureSampleRecord sample = sampleRepository.findById(sampleId).orElse(null);
        if (sample == null || CaptureSampleStateMachine.isTerminal(sample.getStatus())) {
            return;
        }
        try {
            if ("CANCEL_REQUESTED".equals(sample.getStatus())) {
                sample.cancel();
            } else {
                sample.fail(message(failure), true);
            }
            sampleRepository.saveAndFlush(sample);
        } catch (IllegalStateException ignored) {
            // Another worker may have already written the terminal state.
        }
    }

    private PersistentSeedCaptureSampleRecord requireSample(long sampleId) {
        return sampleRepository.findById(sampleId)
                .orElseThrow(() -> new SeedValidationException("capture sample not found: " + sampleId));
    }

    private static String message(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

}
