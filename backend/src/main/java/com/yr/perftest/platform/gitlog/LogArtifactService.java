package com.yr.perftest.platform.gitlog;

import com.yr.perftest.platform.execution.ExecutionValidationException;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 日志制品服务（模块 10）：上传存盘 + 线性检索（MVP 本地扫描；数据量增长后换 Lucene/ES）。
 */
@Service
public class LogArtifactService {
    private static final int MAX_HITS = 50;

    private final PersistentLogArtifactRepository artifactRepository;
    private final PersistentScenarioExecutionRepository executionRepository;
    private final String storageRoot;

    public LogArtifactService(
            PersistentLogArtifactRepository artifactRepository,
            PersistentScenarioExecutionRepository executionRepository,
            @Value("${platform.storage.root:./storage}") String storageRoot
    ) {
        this.artifactRepository = artifactRepository;
        this.executionRepository = executionRepository;
        this.storageRoot = storageRoot;
    }

    @Transactional
    public ArtifactView upload(long executionId, MultipartFile file, String uploadedBy) {
        requireExecution(executionId);
        String safeName = safeFileName(file.getOriginalFilename());
        Path target = Path.of(storageRoot, "log-artifacts", Long.toString(executionId),
                UUID.randomUUID() + "-" + safeName);
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot store log artifact: " + exception.getMessage());
        }
        PersistentLogArtifactRecord record = artifactRepository.save(new PersistentLogArtifactRecord(
                executionId,
                safeName,
                "log-artifacts/" + executionId + "/" + target.getFileName(),
                uploadedBy
        ));
        record.markIndexed();
        return view(artifactRepository.save(record));
    }

    @Transactional(readOnly = true)
    public List<ArtifactView> list(long executionId) {
        requireExecution(executionId);
        return artifactRepository.findAllByExecutionIdOrderByIdDesc(executionId).stream()
                .map(this::view)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SearchHit> search(long executionId, String query, int maxHits) {
        requireExecution(executionId);
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("search query is required");
        }
        List<SearchHit> hits = new ArrayList<>();
        for (PersistentLogArtifactRecord record : artifactRepository.findAllByExecutionIdOrderByIdDesc(executionId)) {
            if (hits.size() >= Math.min(maxHits, MAX_HITS)) {
                break;
            }
            try (BufferedReader reader = Files.newBufferedReader(
                    Path.of(storageRoot).resolve(record.getFilePath()), StandardCharsets.UTF_8)) {
                String line;
                int lineNo = 0;
                while ((line = reader.readLine()) != null && hits.size() < Math.min(maxHits, MAX_HITS)) {
                    lineNo++;
                    if (line.contains(query)) {
                        hits.add(new SearchHit(record.getId(), record.getFileName(), lineNo, line));
                    }
                }
            } catch (IOException exception) {
                // 单个文件不可读不影响其他文件检索
            }
        }
        return List.copyOf(hits);
    }

    private ArtifactView view(PersistentLogArtifactRecord record) {
        return new ArtifactView(
                record.getId(),
                record.getExecutionId(),
                record.getFileName(),
                record.getFilePath(),
                record.getIndexStatus(),
                record.getUploadedBy(),
                record.getCreatedAt()
        );
    }

    private void requireExecution(long executionId) {
        executionRepository.findById(executionId)
                .orElseThrow(() -> new ExecutionValidationException(
                        "execution " + executionId + " does not exist"));
    }

    private String safeFileName(String original) {
        if (original == null || original.isBlank()) {
            return "artifact.log";
        }
        return original.replaceAll("[^A-Za-z0-9._\\-]", "_");
    }

    public record ArtifactView(
            long artifactId,
            long executionId,
            String fileName,
            String filePath,
            String indexStatus,
            String uploadedBy,
            Instant createdAt
    ) {
    }

    public record SearchHit(long artifactId, String fileName, int lineNo, String line) {
    }
}
