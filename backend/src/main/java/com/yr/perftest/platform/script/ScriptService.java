package com.yr.perftest.platform.script;

import com.yr.perftest.platform.project.PersistentProjectRepository;
import com.yr.perftest.platform.project.ProjectValidationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class ScriptService {
    private final PersistentProjectRepository projectRepository;
    private final PersistentScriptVersionRepository scriptVersionRepository;
    private final Path storageRoot;

    public ScriptService(
            PersistentProjectRepository projectRepository,
            PersistentScriptVersionRepository scriptVersionRepository,
            @Value("${platform.storage.root:./storage}") String storageRoot
    ) {
        this.projectRepository = projectRepository;
        this.scriptVersionRepository = scriptVersionRepository;
        this.storageRoot = Path.of(storageRoot);
    }

    @Transactional
    public ScriptVersion uploadScript(long projectId, MultipartFile file, String uploadedBy) {
        if (!projectRepository.existsById(projectId)) {
            throw new ProjectValidationException("project does not exist");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            throw new ScriptValidationException("script filename is required");
        }
        if (!originalFilename.toLowerCase(Locale.ROOT).endsWith(".jmx")) {
            throw new ScriptValidationException("only .jmx files are supported");
        }
        if (file.isEmpty()) {
            throw new ScriptValidationException("script file is empty");
        }

        int versionNo = scriptVersionRepository.countByProjectId(projectId) + 1;
        Path target = storageRoot
                .resolve("scripts")
                .resolve(String.valueOf(projectId))
                .resolve("v" + versionNo + "-" + sanitizeFilename(originalFilename));
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException exception) {
            throw new ScriptValidationException("failed to store script file");
        }

        PersistentScriptVersionRecord record = scriptVersionRepository.save(new PersistentScriptVersionRecord(
                projectId,
                versionNo,
                originalFilename,
                target.toString(),
                uploadedBy,
                Instant.now()
        ));
        return record.toScriptVersion();
    }

    @Transactional(readOnly = true)
    public List<ScriptVersion> listScripts(long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ProjectValidationException("project does not exist");
        }
        return scriptVersionRepository.findAllByProjectIdOrderByVersionNoDesc(projectId).stream()
                .map(PersistentScriptVersionRecord::toScriptVersion)
                .toList();
    }

    private String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
