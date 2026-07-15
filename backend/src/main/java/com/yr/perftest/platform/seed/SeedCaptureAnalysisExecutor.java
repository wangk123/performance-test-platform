package com.yr.perftest.platform.seed;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SeedCaptureAnalysisExecutor {
    private final PersistentSeedCaptureAnalysisRepository analysisRepository;
    private final PersistentSeedCaptureAnalysisInputLockRepository inputLockRepository;
    private final PersistentSeedTemplateRepository templateRepository;
    private final DiskLowWaterGuard diskGuard;
    private final SeedCaptureAnalysisSnapshotReader snapshotReader;
    private final SeedCaptureAnalysisResultWriter resultWriter;
    private final Set<Long> running = ConcurrentHashMap.newKeySet();

    public SeedCaptureAnalysisExecutor(
            PersistentSeedCaptureSampleRepository sampleRepository,
            PersistentSeedCaptureSampleTableRepository tableRepository,
            PersistentSeedCaptureChunkRepository chunkRepository,
            PersistentSeedCaptureAnalysisRepository analysisRepository,
            PersistentSeedCaptureAnalysisInputLockRepository inputLockRepository,
            PersistentSeedCaptureAnalysisResultRepository resultRepository,
            PersistentSeedTemplateRepository templateRepository,
            CaptureChunkStore chunkStore,
            DiskLowWaterGuard diskGuard
    ) {
        this.analysisRepository = analysisRepository;
        this.inputLockRepository = inputLockRepository;
        this.templateRepository = templateRepository;
        this.diskGuard = diskGuard;
        this.snapshotReader = new SeedCaptureAnalysisSnapshotReader(
                sampleRepository,
                tableRepository,
                chunkRepository,
                chunkStore
        );
        this.resultWriter = new SeedCaptureAnalysisResultWriter(
                resultRepository,
                chunkStore,
                diskGuard
        );
    }

    public void submit(long analysisId) {
        if (!running.add(analysisId)) {
            return;
        }
        Thread worker = new Thread(() -> {
            try {
                run(analysisId);
            } finally {
                running.remove(analysisId);
            }
        }, "seed-capture-analysis-" + analysisId);
        worker.setDaemon(true);
        worker.start();
    }

    public void run(long analysisId) {
        PersistentSeedCaptureAnalysisRecord analysis = requireAnalysis(analysisId);
        try {
            if (AnalysisStateMachine.isTerminal(analysis.getStatus())) {
                return;
            }
            if ("CANCEL_REQUESTED".equals(analysis.getStatus())) {
                cancel(analysisId);
                return;
            }
            transition(analysis, "VALIDATING");
            diskGuard.checkBeforeStart();
            List<PersistentSeedCaptureSampleRecord> samples = snapshotReader.inputSamples(analysis);
            List<SampleSnapshotView> snapshots = samples.stream()
                    .map(snapshotReader::snapshot)
                    .toList();
            List<String> tableNames = snapshotReader.tableNames(snapshots);
            progress(
                    analysis,
                    "VALIDATING",
                    0,
                    tableNames.size(),
                    List.of(),
                    0,
                    0,
                    0,
                    0
            );
            checkCancellation(analysisId);

            transition(analysis, "DIFFING");
            DiffChainResult chain = AdjacentSampleDiffEngine.analyze(snapshots);
            SeedCaptureAnalysisResultWriter.Metrics metrics =
                    resultWriter.metrics(chain, tableNames);
            progress(
                    analysis,
                    "DIFFING",
                    0,
                    tableNames.size(),
                    tableNames,
                    metrics.comparedRows(),
                    metrics.skippedTables(),
                    metrics.fineScreenedChunks(),
                    0
            );
            checkCancellation(analysisId);

            transition(analysis, "INFERRING");
            AdjacentInferenceResult inference = AdjacentDiffInference.infer(chain);
            int candidateOperations = inference.templateDraft().operations().size();
            progress(
                    analysis,
                    "INFERRING",
                    0,
                    tableNames.size(),
                    tableNames,
                    metrics.comparedRows(),
                    metrics.skippedTables(),
                    metrics.fineScreenedChunks(),
                    candidateOperations
            );
            checkCancellation(analysisId);

            transition(analysis, "PERSISTING");
            resultWriter.persist(
                    analysisId,
                    analysis.getProjectId(),
                    analysis.getStrategyId(),
                    chain,
                    tableNames,
                    metrics,
                    candidateOperations,
                    () -> checkCancellation(analysisId),
                    (completed, currentTable) -> reportPersistProgress(
                            analysisId,
                            tableNames,
                            metrics,
                            candidateOperations,
                            completed,
                            currentTable
                    )
            );
            checkCancellation(analysisId);

            PersistentSeedTemplateRecord template = templateRepository.save(
                    PersistentSeedTemplateRecord.forAnalysis(
                            analysis.getProjectId(),
                            analysisId,
                            SeedJson.write(inference.templateDraft()),
                            SeedJson.write(resultWriter.seedRows(chain))
                    )
            );
            String summary = SeedJson.write(
                    resultWriter.summary(chain, inference, metrics, candidateOperations)
            );
            analysis = analysisRepository.findById(analysisId).orElseThrow();
            checkCancellation(analysisId);
            analysis.complete(summary, template.getId());
            analysisRepository.saveAndFlush(analysis);
        } catch (AnalysisCanceled ignored) {
            cancel(analysisId);
        } catch (AnalysisInterrupted ignored) {
            interrupt(analysisId);
        } catch (Exception ex) {
            fail(analysisId, ex);
        } finally {
            releaseLocksIfTerminal(analysisId);
        }
    }

    private void reportPersistProgress(
            long analysisId,
            List<String> tableNames,
            SeedCaptureAnalysisResultWriter.Metrics metrics,
            int candidateOperations,
            int completedTables,
            String currentTable
    ) {
        PersistentSeedCaptureAnalysisRecord analysis =
                analysisRepository.findById(analysisId).orElseThrow();
        progress(
                analysis,
                "PERSISTING",
                completedTables,
                tableNames.size(),
                List.of(currentTable),
                metrics.comparedRows(),
                metrics.skippedTables(),
                metrics.fineScreenedChunks(),
                candidateOperations
        );
    }

    private void transition(PersistentSeedCaptureAnalysisRecord analysis, String status) {
        analysis.markStatus(status);
        analysis.updateHeartbeat(Instant.now());
        analysisRepository.saveAndFlush(analysis);
    }

    private void progress(
            PersistentSeedCaptureAnalysisRecord analysis,
            String phase,
            int completedTables,
            int totalTables,
            List<String> currentTables,
            long comparedRows,
            int skippedTables,
            int fineScreenedChunks,
            int candidateOperations
    ) {
        analysis.updateProgress(
                phase,
                completedTables,
                totalTables,
                SeedJson.write(currentTables),
                comparedRows,
                skippedTables,
                fineScreenedChunks,
                candidateOperations,
                Instant.now()
        );
        analysisRepository.saveAndFlush(analysis);
    }

    private void checkCancellation(long analysisId) {
        if (Thread.currentThread().isInterrupted()) {
            throw new AnalysisInterrupted();
        }
        PersistentSeedCaptureAnalysisRecord analysis = requireAnalysis(analysisId);
        if ("CANCEL_REQUESTED".equals(analysis.getStatus())) {
            throw new AnalysisCanceled();
        }
    }

    private void cancel(long analysisId) {
        PersistentSeedCaptureAnalysisRecord analysis = requireAnalysis(analysisId);
        if ("CANCEL_REQUESTED".equals(analysis.getStatus())) {
            analysis.cancel();
            analysisRepository.saveAndFlush(analysis);
        }
    }

    private void interrupt(long analysisId) {
        PersistentSeedCaptureAnalysisRecord analysis = requireAnalysis(analysisId);
        if (AnalysisStateMachine.isActive(analysis.getStatus())) {
            analysis.markInterrupted("analysis worker interrupted");
            analysisRepository.saveAndFlush(analysis);
        }
    }

    private void fail(long analysisId, Exception failure) {
        PersistentSeedCaptureAnalysisRecord analysis = analysisRepository.findById(analysisId).orElse(null);
        if (analysis == null || AnalysisStateMachine.isTerminal(analysis.getStatus())) {
            return;
        }
        try {
            if ("CANCEL_REQUESTED".equals(analysis.getStatus())) {
                analysis.cancel();
            } else {
                analysis.fail(message(failure));
            }
            analysisRepository.saveAndFlush(analysis);
        } catch (IllegalStateException ignored) {
        }
    }

    private void releaseLocksIfTerminal(long analysisId) {
        analysisRepository.findById(analysisId)
                .filter(analysis -> AnalysisStateMachine.isTerminal(analysis.getStatus()))
                .ifPresent(ignored -> {
                    inputLockRepository.deleteByAnalysisId(analysisId);
                    inputLockRepository.flush();
                });
    }

    private PersistentSeedCaptureAnalysisRecord requireAnalysis(long analysisId) {
        return analysisRepository.findById(analysisId)
                .orElseThrow(() -> new SeedValidationException(
                        "capture analysis not found: " + analysisId
                ));
    }

    private static String message(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }

    private static final class AnalysisCanceled extends RuntimeException {
    }

    private static final class AnalysisInterrupted extends RuntimeException {
    }
}
