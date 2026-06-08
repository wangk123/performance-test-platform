package com.yr.perftest.platform.script;

import com.yr.perftest.platform.project.PersistentProjectRepository;
import com.yr.perftest.platform.project.ProjectValidationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class ScriptService {
    private final PersistentProjectRepository projectRepository;
    private final PersistentScriptVersionRepository scriptVersionRepository;
    private final JmeterScriptParser jmeterScriptParser;
    private final JmeterScriptRenderer jmeterScriptRenderer;
    private final Path storageRoot;

    public ScriptService(
            PersistentProjectRepository projectRepository,
            PersistentScriptVersionRepository scriptVersionRepository,
            JmeterScriptParser jmeterScriptParser,
            JmeterScriptRenderer jmeterScriptRenderer,
            @Value("${platform.storage.root:./storage}") String storageRoot
    ) {
        this.projectRepository = projectRepository;
        this.scriptVersionRepository = scriptVersionRepository;
        this.jmeterScriptParser = jmeterScriptParser;
        this.jmeterScriptRenderer = jmeterScriptRenderer;
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
        byte[] content = readFile(file);
        validateJmx(new String(content, StandardCharsets.UTF_8));

        int versionNo = scriptVersionRepository.countByProjectId(projectId) + 1;
        Path target = storageRoot
                .resolve("scripts")
                .resolve(String.valueOf(projectId))
                .resolve("v" + versionNo + "-" + sanitizeFilename(originalFilename));
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
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

    @Transactional(readOnly = true)
    public List<ScriptDefinition> listScriptDefinitions(long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ProjectValidationException("project does not exist");
        }
        return scriptVersionRepository.findAllByProjectIdOrderByVersionNoDesc(projectId).stream()
                .map(this::toScriptDefinition)
                .toList();
    }

    @Transactional(readOnly = true)
    public ScriptDefinition getScriptDefinition(long projectId, long versionId) {
        return toScriptDefinition(requireScriptVersion(projectId, versionId));
    }

    @Transactional(readOnly = true)
    public ScriptContent getScriptContent(long projectId, long versionId) {
        PersistentScriptVersionRecord record = requireScriptVersion(projectId, versionId);
        try {
            return new ScriptContent(record.toScriptVersion(), Files.readString(Path.of(record.getStoredPath())));
        } catch (IOException exception) {
            throw new ScriptValidationException("failed to read script file");
        }
    }

    @Transactional
    public void deleteScript(long projectId, long versionId) {
        PersistentScriptVersionRecord record = requireScriptVersion(projectId, versionId);
        scriptVersionRepository.delete(record);
        try {
            Files.deleteIfExists(Path.of(record.getStoredPath()));
        } catch (IOException exception) {
            throw new ScriptValidationException("failed to delete script file");
        }
    }

    @Transactional
    public ScriptVersion saveScriptContent(long projectId, long versionId, String content, String filename, String uploadedBy) {
        PersistentScriptVersionRecord baseVersion = requireScriptVersion(projectId, versionId);
        String targetFilename = filename == null || filename.trim().isEmpty()
                ? baseVersion.getOriginalFilename()
                : filename.trim();
        if (!targetFilename.toLowerCase(Locale.ROOT).endsWith(".jmx")) {
            throw new ScriptValidationException("only .jmx files are supported");
        }
        if (content == null || content.trim().isEmpty()) {
            throw new ScriptValidationException("script content is empty");
        }
        validateJmx(content);

        Path target = Path.of(baseVersion.getStoredPath());
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new ScriptValidationException("failed to store script file");
        }

        baseVersion.updateMetadata(targetFilename, uploadedBy, Instant.now());
        return baseVersion.toScriptVersion();
    }

    @Transactional
    public ScriptDefinition saveScriptDefinition(
            long projectId,
            long versionId,
            String filename,
            List<ScriptStepDefinition> steps,
            String uploadedBy
    ) {
        String content = jmeterScriptRenderer.render(steps == null ? List.of() : steps);
        ScriptVersion version = saveScriptContent(projectId, versionId, content, filename, uploadedBy);
        return getScriptDefinition(projectId, version.id());
    }

    public PersistentScriptVersionRecord requireScriptVersion(long projectId, long versionId) {
        return scriptVersionRepository.findByIdAndProjectId(versionId, projectId)
                .orElseThrow(() -> new ScriptValidationException("script version does not exist"));
    }

    private byte[] readFile(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new ScriptValidationException("failed to read script file");
        }
    }

    private void validateJmx(String content) {
        if (!content.contains("<jmeterTestPlan")) {
            throw new ScriptValidationException("script content is not a JMeter test plan");
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(content)));
            if (!"jmeterTestPlan".equals(document.getDocumentElement().getNodeName())) {
                throw new ScriptValidationException("script content is not a JMeter test plan");
            }
        } catch (ScriptValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ScriptValidationException("script content is not valid xml");
        }
    }

    private ScriptDefinition toScriptDefinition(PersistentScriptVersionRecord record) {
        String content = readStoredContent(record);
        List<ScriptStepDefinition> steps = jmeterScriptParser.parseSteps(content);
        return new ScriptDefinition(
                record.getId(),
                record.getProjectId(),
                nameOf(record.getOriginalFilename()),
                record.getOriginalFilename(),
                record.getVersionNo(),
                "PARSED",
                "",
                record.toScriptVersion().uploadedAt(),
                steps,
                scriptVersionRepository.findAllByProjectIdOrderByVersionNoDesc(record.getProjectId()).stream()
                        .map(PersistentScriptVersionRecord::toScriptVersion)
                        .toList()
        );
    }

    private String readStoredContent(PersistentScriptVersionRecord record) {
        try {
            return Files.readString(Path.of(record.getStoredPath()));
        } catch (IOException exception) {
            throw new ScriptValidationException("failed to read script file");
        }
    }

    private String nameOf(String filename) {
        return filename.replaceFirst("(?i)\\.jmx$", "");
    }

    private String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
