package com.yr.perftest.platform.seed;

import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class SeedCaptureAnalysisService {
    private static final int MAX_RESULT_PAGE_SIZE = 200;
    private static final Set<String> TERMINAL_SAMPLE_STATUSES = Set.of(
            "SUCCEEDED", "FAILED", "CANCELED", "INTERRUPTED"
    );
    private static final Set<String> ACTIVE_ANALYSIS_STATUSES = Set.of(
            "QUEUED", "VALIDATING", "DIFFING", "INFERRING", "PERSISTING", "CANCEL_REQUESTED"
    );

    private final PersistentSeedCaptureStrategyRepository strategyRepository;
    private final PersistentSeedCaptureSampleRepository sampleRepository;
    private final PersistentSeedCaptureAnalysisRepository analysisRepository;
    private final PersistentSeedCaptureAnalysisInputLockRepository inputLockRepository;
    private final PersistentSeedCaptureAnalysisResultRepository resultRepository;
    private final PersistentSeedTemplateRepository templateRepository;
    private final CaptureChunkStore chunkStore;
    private final SeedCaptureAnalysisExecutor executor;
    private final SeedCaptureAnalysisManifestBuilder manifestBuilder;

    @Autowired
    public SeedCaptureAnalysisService(
            PersistentSeedCaptureStrategyRepository strategyRepository,
            PersistentSeedCaptureSampleRepository sampleRepository,
            PersistentSeedCaptureSampleTableRepository tableRepository,
            PersistentSeedCaptureChunkRepository chunkRepository,
            PersistentSeedCaptureAnalysisRepository analysisRepository,
            PersistentSeedCaptureAnalysisInputLockRepository inputLockRepository,
            PersistentSeedCaptureAnalysisResultRepository resultRepository,
            PersistentSeedTemplateRepository templateRepository,
            CaptureChunkStore chunkStore,
            SeedCaptureAnalysisExecutor executor
    ) {
        this.strategyRepository = strategyRepository;
        this.sampleRepository = sampleRepository;
        this.analysisRepository = analysisRepository;
        this.inputLockRepository = inputLockRepository;
        this.resultRepository = resultRepository;
        this.templateRepository = templateRepository;
        this.chunkStore = chunkStore;
        this.executor = executor;
        this.manifestBuilder = new SeedCaptureAnalysisManifestBuilder(
                tableRepository,
                chunkRepository
        );
    }

    public SeedCaptureAnalysisService(
            PersistentSeedCaptureStrategyRepository strategyRepository,
            PersistentSeedCaptureSampleRepository sampleRepository,
            PersistentSeedCaptureSampleTableRepository tableRepository,
            PersistentSeedCaptureChunkRepository chunkRepository,
            PersistentSeedCaptureAnalysisRepository analysisRepository,
            PersistentSeedCaptureAnalysisInputLockRepository inputLockRepository,
            PersistentSeedCaptureAnalysisResultRepository resultRepository,
            PersistentSeedTemplateRepository templateRepository,
            CaptureChunkStore chunkStore
    ) {
        this(
                strategyRepository,
                sampleRepository,
                tableRepository,
                chunkRepository,
                analysisRepository,
                inputLockRepository,
                resultRepository,
                templateRepository,
                chunkStore,
                null
        );
    }

    @Transactional
    public Map<String, Object> create(
            long projectId,
            CreateSeedCaptureAnalysisRequest request
    ) {
        if (request == null) {
            throw new SeedValidationException("capture analysis request is required");
        }
        if (request.strategyId() == null) {
            throw new SeedValidationException("strategyId is required");
        }
        List<Long> sampleIds = request.sampleIds() == null
                ? List.of()
                : request.sampleIds().stream().filter(java.util.Objects::nonNull).toList();
        if (sampleIds.size() < 3) {
            throw new SeedValidationException(
                    "at least three terminal samples are required for two adjacent Diffs"
            );
        }
        if (new HashSet<>(sampleIds).size() != sampleIds.size()) {
            throw new SeedValidationException("sampleIds must not contain duplicates");
        }

        PersistentSeedCaptureStrategyRecord strategy = strategyRepository
                .findByIdAndProjectId(request.strategyId(), projectId)
                .orElseThrow(() -> new SeedValidationException(
                        "capture strategy not found: " + request.strategyId()
                ));
        List<PersistentSeedCaptureSampleRecord> samples = sampleIds.stream()
                .map(id -> sampleRepository.findByIdAndProjectId(id, projectId)
                        .orElseThrow(() -> new SeedValidationException(
                                "capture sample not found: " + id
                        )))
                .toList();
        validateSamples(strategy, samples, Boolean.TRUE.equals(request.confirmIncomplete()));
        List<PersistentSeedCaptureSampleRecord> ordered = samples.stream()
                .sorted(Comparator.comparing(PersistentSeedCaptureSampleRecord::getCaptureStartedAt)
                        .thenComparing(PersistentSeedCaptureSampleRecord::getSampleSeq)
                        .thenComparing(PersistentSeedCaptureSampleRecord::getId))
                .toList();
        ordered.forEach(sample -> ensureInputAvailable(sample.getId()));

        PersistentSeedCaptureAnalysisRecord analysis = analysisRepository.saveAndFlush(
                new PersistentSeedCaptureAnalysisRecord(
                        projectId,
                        strategy.getId(),
                        SeedJson.write(ordered.stream()
                                .map(PersistentSeedCaptureSampleRecord::getId)
                                .toList())
                )
        );
        analysis.setInputManifest(
                SeedJson.write(manifestBuilder.build(analysis, strategy, ordered))
        );
        analysisRepository.saveAndFlush(analysis);
        for (PersistentSeedCaptureSampleRecord sample : ordered) {
            inputLockRepository.saveAndFlush(
                    new PersistentSeedCaptureAnalysisInputLockRecord(analysis.getId(), sample.getId())
            );
        }
        submitAfterCommit(analysis.getId());
        return view(analysis);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(long projectId) {
        return analysisRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream()
                .map(this::view)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(long projectId, long analysisId) {
        return view(requireAnalysis(projectId, analysisId));
    }

    @Transactional
    public Map<String, Object> cancel(long projectId, long analysisId) {
        PersistentSeedCaptureAnalysisRecord analysis = requireAnalysis(projectId, analysisId);
        if (AnalysisStateMachine.isActive(analysis.getStatus())
                && !"CANCEL_REQUESTED".equals(analysis.getStatus())) {
            analysis.requestCancel();
            analysisRepository.saveAndFlush(analysis);
        }
        return view(analysisRepository.findById(analysisId).orElseThrow());
    }

    @Transactional
    public Map<String, Object> delete(long projectId, long analysisId) {
        PersistentSeedCaptureAnalysisRecord analysis = requireAnalysis(projectId, analysisId);
        if (ACTIVE_ANALYSIS_STATUSES.contains(analysis.getStatus())) {
            throw new SeedValidationException(
                    "active analysis cannot be deleted: " + analysisId
            );
        }
        if (!"DELETING".equals(analysis.getStatus())) {
            analysis.markStatus("DELETING");
            analysisRepository.saveAndFlush(analysis);
        }
        try {
            List<PersistentSeedCaptureAnalysisResultRecord> results =
                    resultRepository.findByAnalysisIdOrderByIdAsc(analysisId);
            for (PersistentSeedCaptureAnalysisResultRecord result : results) {
                if (result.getRelativePath() != null && !result.getRelativePath().isBlank()) {
                    Files.deleteIfExists(chunkStore.resolveRelativePath(result.getRelativePath()));
                }
            }
            resultRepository.deleteAll(results);
            resultRepository.flush();
            inputLockRepository.deleteByAnalysisId(analysisId);
            inputLockRepository.flush();
            analysisRepository.delete(analysis);
            analysisRepository.flush();
            return Map.of("id", analysisId, "status", "DELETED");
        } catch (Exception ex) {
            PersistentSeedCaptureAnalysisRecord current =
                    analysisRepository.findById(analysisId).orElse(analysis);
            current.recordDeletionFailure(message(ex));
            analysisRepository.saveAndFlush(current);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", analysisId);
            result.put("status", current.getStatus());
            result.put("deletionError", current.getErrorMessage());
            return result;
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> readDiffs(
            long projectId,
            long analysisId,
            String tableName,
            String cursor,
            int limit
    ) {
        PersistentSeedCaptureAnalysisRecord analysis = requireAnalysis(projectId, analysisId);
        if (tableName == null || tableName.isBlank()) {
            throw new SeedValidationException("table is required");
        }
        List<PersistentSeedCaptureAnalysisResultRecord> results =
                resultRepository.findByAnalysisIdOrderByIdAsc(analysisId).stream()
                        .filter(result -> tableName.equals(resultTable(result, analysisId)))
                        .sorted(Comparator.comparing(
                                result -> result.getChunkSeq() == null ? 0 : result.getChunkSeq()
                        ))
                        .toList();
        Cursor start = decodeCursor(cursor);
        int pageSize = Math.min(Math.max(limit, 1), MAX_RESULT_PAGE_SIZE);
        List<Map<String, Object>> rows = new ArrayList<>(pageSize);
        List<Map<String, Object>> diagnostics = new ArrayList<>();
        boolean incomplete = false;
        boolean checksumValid = true;
        String nextCursor = null;

        for (int index = start.resultIndex(); index < results.size(); index++) {
            PersistentSeedCaptureAnalysisResultRecord result = results.get(index);
            Map<String, Object> diagnostic = resultView(result);
            if (result.getRelativePath() == null || result.getRelativePath().isBlank()) {
                diagnostics.add(diagnostic);
                continue;
            }
            CaptureChunkStore.ChunkManifest manifest = new CaptureChunkStore.ChunkManifest(
                    result.getRelativePath(),
                    result.getRowCount(),
                    null,
                    result.getFileChecksum(),
                    0
            );
            if (!chunkStore.verifyChecksum(manifest)) {
                incomplete = true;
                checksumValid = false;
                diagnostic.put("checksumValid", false);
                diagnostic.put("incomplete", true);
                diagnostics.add(diagnostic);
                continue;
            }
            diagnostic.put("checksumValid", true);
            diagnostic.put("incomplete", false);
            diagnostics.add(diagnostic);
            int offset = index == start.resultIndex() ? start.rowOffset() : 0;
            try (Stream<Map<String, Object>> stream = chunkStore.readRows(manifest)) {
                java.util.Iterator<Map<String, Object>> iterator = stream.iterator();
                int consumed = 0;
                while (iterator.hasNext()) {
                    Map<String, Object> row = iterator.next();
                    if (consumed++ < offset) {
                        continue;
                    }
                    rows.add(row);
                    if (rows.size() == pageSize) {
                        nextCursor = iterator.hasNext()
                                ? encodeCursor(index, consumed)
                                : nextResultCursor(index, results.size());
                        break;
                    }
                }
            } catch (RuntimeException ex) {
                incomplete = true;
                checksumValid = false;
                diagnostic.put("checksumValid", false);
                diagnostic.put("incomplete", true);
            }
            if (rows.size() == pageSize) {
                break;
            }
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("analysisId", analysisId);
        response.put("tableName", tableName);
        response.put("rows", rows);
        response.put("nextCursor", nextCursor);
        response.put("results", diagnostics);
        response.put("incomplete", incomplete);
        response.put("checksumValid", checksumValid);
        return response;
    }

    private void validateSamples(
            PersistentSeedCaptureStrategyRecord strategy,
            List<PersistentSeedCaptureSampleRecord> samples,
            boolean confirmIncomplete
    ) {
        for (PersistentSeedCaptureSampleRecord sample : samples) {
            if (sample.getStrategyId() == null || !sample.getStrategyId().equals(strategy.getId())) {
                throw new SeedValidationException("all samples must belong to one strategy");
            }
            if (!TERMINAL_SAMPLE_STATUSES.contains(sample.getStatus())) {
                throw new SeedValidationException(
                        "sample " + sample.getId() + " is not a terminal capture sample"
                );
            }
            boolean incomplete = !"SUCCEEDED".equals(sample.getStatus())
                    || Boolean.TRUE.equals(sample.getIncomplete());
            if (incomplete && !confirmIncomplete) {
                throw new SeedValidationException(
                        "incomplete samples require confirmIncomplete=true"
                );
            }
        }
    }

    private void ensureInputAvailable(long sampleId) {
        Optional<PersistentSeedCaptureAnalysisInputLockRecord> existing =
                inputLockRepository.findBySampleId(sampleId);
        if (existing.isEmpty()) {
            return;
        }
        Optional<PersistentSeedCaptureAnalysisRecord> analysis =
                analysisRepository.findById(existing.get().getAnalysisId());
        if (analysis.isEmpty() || AnalysisStateMachine.isActive(analysis.get().getStatus())) {
            throw new SeedValidationException(
                    "sample " + sampleId + " is locked by active analysis "
                            + existing.get().getAnalysisId()
            );
        }
        inputLockRepository.delete(existing.get());
        inputLockRepository.flush();
    }

    private void submitAfterCommit(long analysisId) {
        if (executor == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            executor.submit(analysisId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                executor.submit(analysisId);
            }
        });
    }

    private Map<String, Object> view(PersistentSeedCaptureAnalysisRecord analysis) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", analysis.getId());
        view.put("analysisId", analysis.getId());
        view.put("projectId", analysis.getProjectId());
        view.put("strategyId", analysis.getStrategyId());
        view.put("status", analysis.getStatus());
        view.put("phase", analysis.getPhase());
        view.put("completedTables", analysis.getCompletedTables());
        view.put("totalTables", analysis.getTotalTables());
        view.put("currentTables", readList(analysis.getCurrentTablesJson()));
        view.put("comparedRows", analysis.getComparedRows());
        view.put("skippedTables", analysis.getSkippedTables());
        view.put("fineScreenedChunks", analysis.getFineScreenedChunks());
        view.put("candidateOperationCount", analysis.getCandidateOperationCount());
        view.put("inputSampleIds", readList(analysis.getInputSampleIdsJson()));
        view.put("inputManifest", readMap(analysis.getInputManifestJson()));
        view.put("summary", readMap(analysis.getSummaryJson()));
        view.put("templateId", analysis.getTemplateId());
        view.put("heartbeatAt", analysis.getHeartbeatAt());
        view.put("errorMessage", analysis.getErrorMessage());
        view.put("createdAt", analysis.getCreatedAt());
        view.put("updatedAt", analysis.getUpdatedAt());
        view.put("finishedAt", analysis.getFinishedAt());
        return view;
    }

    private Map<String, Object> resultView(PersistentSeedCaptureAnalysisResultRecord result) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", result.getId());
        view.put("resultType", result.getResultType());
        view.put("chunkSeq", result.getChunkSeq());
        view.put("rowCount", result.getRowCount());
        view.put("relativePath", result.getRelativePath());
        view.put("summary", readMap(result.getSummaryJson()));
        return view;
    }

    private String resultTable(
            PersistentSeedCaptureAnalysisResultRecord result,
            long analysisId
    ) {
        if (result.getTableName() != null) {
            return result.getTableName();
        }
        String path = result.getRelativePath();
        if (path == null) {
            return null;
        }
        String[] parts = path.split("[/\\\\]");
        String marker = "analysis-" + analysisId;
        for (int i = 0; i + 1 < parts.length; i++) {
            if (marker.equals(parts[i])) {
                return parts[i + 1];
            }
        }
        return null;
    }

    private PersistentSeedCaptureAnalysisRecord requireAnalysis(long projectId, long analysisId) {
        return analysisRepository.findByIdAndProjectId(analysisId, projectId)
                .orElseThrow(() -> new SeedValidationException(
                        "capture analysis not found: " + analysisId
                ));
    }

    private Cursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return new Cursor(0, 0);
        }
        try {
            String decoded = new String(
                    Base64.getUrlDecoder().decode(cursor),
                    StandardCharsets.UTF_8
            );
            String[] parts = decoded.replaceFirst("^v1:", "").split(":", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException("cursor parts");
            }
            int resultIndex = Integer.parseInt(parts[0]);
            int rowOffset = Integer.parseInt(parts[1]);
            if (resultIndex < 0 || rowOffset < 0) {
                throw new IllegalArgumentException("cursor range");
            }
            return new Cursor(resultIndex, rowOffset);
        } catch (RuntimeException ex) {
            throw new SeedValidationException("invalid analysis diff cursor");
        }
    }

    private String encodeCursor(int resultIndex, int rowOffset) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("v1:" + resultIndex + ":" + rowOffset).getBytes(StandardCharsets.UTF_8)
        );
    }

    private String nextResultCursor(int index, int size) {
        return index + 1 < size ? encodeCursor(index + 1, 0) : null;
    }

    private List<Object> readList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return SeedJson.read(json, new TypeReference<List<Object>>() {
        });
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return SeedJson.read(json, new TypeReference<Map<String, Object>>() {
        });
    }

    private static String message(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }

    private record Cursor(int resultIndex, int rowOffset) {
    }
}
