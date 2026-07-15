package com.yr.perftest.platform.seed;

import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class SeedCaptureSampleService {
    private static final int MAX_ROW_PAGE_SIZE = 200;
    private static final Set<String> ACTIVE_ANALYSIS_STATUSES = Set.of(
            "QUEUED",
            "VALIDATING",
            "RUNNING",
            "DIFFING",
            "INFERRING",
            "PERSISTING",
            "CANCEL_REQUESTED"
    );

    private final PersistentSeedCaptureSampleRepository sampleRepository;
    private final PersistentSeedCaptureSampleTableRepository tableRepository;
    private final PersistentSeedCaptureChunkRepository chunkRepository;
    private final PersistentSeedCaptureAnalysisRepository analysisRepository;
    private final PersistentSeedCaptureAnalysisInputLockRepository inputLockRepository;
    private final CaptureChunkStore chunkStore;

    public SeedCaptureSampleService(
            PersistentSeedCaptureSampleRepository sampleRepository,
            PersistentSeedCaptureSampleTableRepository tableRepository,
            PersistentSeedCaptureChunkRepository chunkRepository,
            PersistentSeedCaptureAnalysisRepository analysisRepository,
            PersistentSeedCaptureAnalysisInputLockRepository inputLockRepository,
            CaptureChunkStore chunkStore
    ) {
        this.sampleRepository = sampleRepository;
        this.tableRepository = tableRepository;
        this.chunkRepository = chunkRepository;
        this.analysisRepository = analysisRepository;
        this.inputLockRepository = inputLockRepository;
        this.chunkStore = chunkStore;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listTables(long projectId, long sampleId) {
        requireSample(projectId, sampleId);
        List<Map<String, Object>> tables = tableRepository
                .findBySampleIdOrderByTableNameAsc(sampleId)
                .stream()
                .map(this::tableView)
                .toList();
        boolean incomplete = tables.stream()
                .anyMatch(table -> Boolean.TRUE.equals(table.get("incomplete")));
        return Map.of(
                "sampleId", sampleId,
                "tableCount", tables.size(),
                "incomplete", incomplete,
                "tables", tables
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> readRows(
            long projectId,
            long sampleId,
            String tableName,
            String cursor,
            int limit
    ) {
        requireSample(projectId, sampleId);
        PersistentSeedCaptureSampleTableRecord table = tableRepository
                .findBySampleIdAndTableName(sampleId, tableName)
                .orElseThrow(() -> new SeedValidationException(
                        "capture sample table not found: " + tableName
                ));
        List<PersistentSeedCaptureChunkRecord> chunks =
                chunkRepository.findBySampleIdAndTableNameOrderByChunkSeqAsc(sampleId, tableName);
        RowCursor start = decodeCursor(cursor);
        int pageSize = Math.min(Math.max(limit, 1), MAX_ROW_PAGE_SIZE);
        List<Map<String, Object>> rows = new java.util.ArrayList<>(pageSize);
        List<Map<String, Object>> diagnostics = new java.util.ArrayList<>();
        boolean incomplete = Boolean.TRUE.equals(table.getIncomplete());
        boolean checksumValid = true;
        String nextCursor = null;

        for (int index = 0; index < chunks.size(); index++) {
            PersistentSeedCaptureChunkRecord chunk = chunks.get(index);
            if (chunk.getChunkSeq() < start.chunkSeq()) {
                continue;
            }
            int offset = chunk.getChunkSeq().equals(start.chunkSeq()) ? start.rowOffset() : 0;
            Map<String, Object> diagnostic = chunkView(chunk);
            if (!"READY".equals(chunk.getStatus()) || chunk.getRelativePath() == null) {
                diagnostic.put("checksumValid", false);
                diagnostic.put("incomplete", true);
                diagnostics.add(diagnostic);
                incomplete = true;
                checksumValid = false;
                continue;
            }
            CaptureChunkStore.ChunkManifest manifest = new CaptureChunkStore.ChunkManifest(
                    chunk.getRelativePath(),
                    chunk.getRowCount(),
                    chunk.getContentHash(),
                    chunk.getFileChecksum(),
                    chunk.getByteSize()
            );
            try {
                Stream<Map<String, Object>> rowStream = chunkStore.readRows(manifest);
                diagnostic.put("checksumValid", true);
                diagnostic.put("incomplete", false);
                diagnostics.add(diagnostic);
                int consumed = 0;
                try (rowStream) {
                    Iterator<Map<String, Object>> iterator = rowStream.iterator();
                    while (iterator.hasNext()) {
                        Map<String, Object> row = iterator.next();
                        if (consumed++ < offset) {
                            continue;
                        }
                        rows.add(row);
                        if (rows.size() == pageSize) {
                            boolean hasMoreInChunk = iterator.hasNext();
                            nextCursor = hasMoreInChunk
                                    ? encodeCursor(chunk.getChunkSeq(), consumed)
                                    : nextChunkCursor(chunks, index);
                            break;
                        }
                    }
                }
                if (rows.size() == pageSize) {
                    break;
                }
            } catch (RuntimeException ex) {
                diagnostic.put("checksumValid", false);
                diagnostic.put("incomplete", true);
                incomplete = true;
                checksumValid = false;
                if (!diagnostics.contains(diagnostic)) {
                    diagnostics.add(diagnostic);
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sampleId", sampleId);
        result.put("tableName", tableName);
        result.put("rows", rows);
        result.put("nextCursor", nextCursor);
        result.put("schema", schema(table.getSchemaJson()));
        result.put("incomplete", incomplete);
        result.put("checksumValid", checksumValid);
        result.put("chunks", diagnostics);
        return result;
    }

    @Transactional
    public Map<String, Object> delete(long projectId, long sampleId) {
        PersistentSeedCaptureSampleRecord sample = requireSample(projectId, sampleId);
        ensureNotLocked(sampleId);
        if (CaptureSampleStateMachine.isActive(sample.getStatus())) {
            throw new SeedValidationException("active capture sample cannot be deleted: " + sampleId);
        }
        if (!"DELETING".equals(sample.getStatus())) {
            sample.markStatus("DELETING");
            sampleRepository.saveAndFlush(sample);
        }

        try {
            List<PersistentSeedCaptureChunkRecord> chunks =
                    chunkRepository.findBySampleIdOrderByTableNameAscChunkSeqAsc(sampleId);
            for (PersistentSeedCaptureChunkRecord chunk : chunks) {
                if (chunk.getRelativePath() != null && !chunk.getRelativePath().isBlank()) {
                    Files.deleteIfExists(chunkStore.resolveRelativePath(chunk.getRelativePath()));
                }
            }
            chunkRepository.deleteAll(chunks);
            chunkRepository.flush();
            List<PersistentSeedCaptureSampleTableRecord> tables =
                    tableRepository.findBySampleIdOrderByTableNameAsc(sampleId);
            tableRepository.deleteAll(tables);
            tableRepository.flush();
            sampleRepository.delete(sample);
            sampleRepository.flush();
            return Map.of("id", sampleId, "status", "DELETED");
        } catch (Exception ex) {
            PersistentSeedCaptureSampleRecord current = sampleRepository.findById(sampleId).orElse(sample);
            current.recordDeletionFailure(message(ex));
            sampleRepository.saveAndFlush(current);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", sampleId);
            result.put("status", current.getStatus());
            result.put("deletionError", current.getErrorMessage());
            return result;
        }
    }

    private Map<String, Object> tableView(PersistentSeedCaptureSampleTableRecord table) {
        List<Map<String, Object>> chunks = chunkRepository
                .findBySampleIdAndTableNameOrderByChunkSeqAsc(table.getSampleId(), table.getTableName())
                .stream()
                .map(this::chunkView)
                .toList();
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", table.getId());
        view.put("sampleId", table.getSampleId());
        view.put("tableName", table.getTableName());
        view.put("schema", schema(table.getSchemaJson()));
        view.put("schemaHash", table.getSchemaHash());
        view.put("rowCount", table.getRowCount());
        view.put("contentHash", table.getContentHash());
        view.put("riskyNoPk", table.getRiskyNoPk());
        view.put("status", table.getStatus());
        view.put("incomplete", Boolean.TRUE.equals(table.getIncomplete())
                || chunks.stream().anyMatch(chunk -> !"READY".equals(chunk.get("status"))));
        view.put("errorMessage", table.getErrorMessage());
        view.put("chunkCount", chunks.size());
        view.put("chunks", chunks);
        return view;
    }

    private Map<String, Object> chunkView(PersistentSeedCaptureChunkRecord chunk) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", chunk.getId());
        view.put("chunkSeq", chunk.getChunkSeq());
        view.put("rowCount", chunk.getRowCount());
        view.put("contentHash", chunk.getContentHash());
        view.put("relativePath", chunk.getRelativePath());
        view.put("fileChecksum", chunk.getFileChecksum());
        view.put("status", chunk.getStatus());
        view.put("byteSize", chunk.getByteSize());
        return view;
    }

    private Map<String, Object> schema(String schemaJson) {
        return SeedJson.read(
                schemaJson == null || schemaJson.isBlank() ? "{}" : schemaJson,
                new TypeReference<Map<String, Object>>() {
                }
        );
    }

    private String nextChunkCursor(
            List<PersistentSeedCaptureChunkRecord> chunks,
            int currentIndex
    ) {
        if (currentIndex + 1 >= chunks.size()) {
            return null;
        }
        return encodeCursor(chunks.get(currentIndex + 1).getChunkSeq(), 0);
    }

    private RowCursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return new RowCursor(0, 0);
        }
        try {
            String decoded;
            try {
                decoded = new String(
                        Base64.getUrlDecoder().decode(cursor),
                        StandardCharsets.UTF_8
                );
            } catch (IllegalArgumentException ignored) {
                decoded = cursor;
            }
            if (decoded.startsWith("v1:")) {
                decoded = decoded.substring(3);
            }
            String[] parts = decoded.split(":", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException("cursor parts");
            }
            int chunkSeq = Integer.parseInt(parts[0]);
            int rowOffset = Integer.parseInt(parts[1]);
            if (chunkSeq < 0 || rowOffset < 0) {
                throw new IllegalArgumentException("cursor range");
            }
            return new RowCursor(chunkSeq, rowOffset);
        } catch (RuntimeException ex) {
            throw new SeedValidationException("invalid capture row cursor");
        }
    }

    private String encodeCursor(int chunkSeq, int rowOffset) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("v1:" + chunkSeq + ":" + rowOffset).getBytes(StandardCharsets.UTF_8)
        );
    }

    private PersistentSeedCaptureSampleRecord requireSample(long projectId, long sampleId) {
        return sampleRepository.findByIdAndProjectId(sampleId, projectId)
                .orElseThrow(() -> new SeedValidationException(
                        "capture sample not found: " + sampleId
                ));
    }

    private void ensureNotLocked(long sampleId) {
        Optional<PersistentSeedCaptureAnalysisInputLockRecord> lock =
                inputLockRepository.findBySampleId(sampleId);
        if (lock.isEmpty()) {
            return;
        }
        Optional<PersistentSeedCaptureAnalysisRecord> analysis =
                analysisRepository.findById(lock.get().getAnalysisId());
        if (analysis.isEmpty() || ACTIVE_ANALYSIS_STATUSES.contains(analysis.get().getStatus())) {
            throw new SeedValidationException(
                    "capture sample " + sampleId
                            + " is locked by active analysis " + lock.get().getAnalysisId()
            );
        }
        inputLockRepository.delete(lock.get());
        inputLockRepository.flush();
    }

    private static String message(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }

    private record RowCursor(int chunkSeq, int rowOffset) {
    }
}
