package com.yr.perftest.platform.script;

import com.yr.perftest.platform.project.PersistentProjectRepository;
import com.yr.perftest.platform.project.ProjectValidationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
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
import java.nio.file.StandardCopyOption;

@Service
public class ScriptService {
    private final PersistentProjectRepository projectRepository;
    private final PersistentScriptRepository scriptRepository;
    private final PersistentScriptVersionRepository scriptVersionRepository;
    private final ScriptPublicationService publicationService;
    private final JmeterScriptParser jmeterScriptParser;
    private final JmeterScriptPatcher jmeterScriptPatcher;
    private final JmeterScriptRenderer jmeterScriptRenderer;
    private final Path storageRoot;

    public ScriptService(
            PersistentProjectRepository projectRepository,
            PersistentScriptRepository scriptRepository,
            PersistentScriptVersionRepository scriptVersionRepository,
            ScriptPublicationService publicationService,
            JmeterScriptParser jmeterScriptParser,
            JmeterScriptPatcher jmeterScriptPatcher,
            JmeterScriptRenderer jmeterScriptRenderer,
            @Value("${platform.storage.root:./storage}") String storageRoot
    ) {
        this.projectRepository = projectRepository;
        this.scriptRepository = scriptRepository;
        this.scriptVersionRepository = scriptVersionRepository;
        this.publicationService = publicationService;
        this.jmeterScriptParser = jmeterScriptParser;
        this.jmeterScriptPatcher = jmeterScriptPatcher;
        this.jmeterScriptRenderer = jmeterScriptRenderer;
        this.storageRoot = Path.of(storageRoot);
    }

    @Transactional
    public ScriptVersion uploadScript(long projectId, MultipartFile file, String uploadedBy, String remark) {
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
        String content = new String(readFile(file), StandardCharsets.UTF_8);
        validateJmx(content);
        String name = nameOf(originalFilename);
        PersistentScriptRecord script = scriptRepository.findByProjectIdAndName(projectId, name)
                .orElseGet(() -> scriptRepository.save(PersistentScriptRecord.persistentOf(
                        projectId, name, 0, uploadedBy, Instant.now())));
        return storePublishedVersion(script, originalFilename, content, uploadedBy,
                remark == null ? "" : remark.trim());
    }

    private ScriptVersion storePublishedVersion(
            PersistentScriptRecord script, String originalFilename, String content, String uploadedBy, String remark) {
        int versionNo = script.getLatestVersionNo() + 1;
        Path target = storageRoot
                .resolve("scripts")
                .resolve(String.valueOf(script.getProjectId()))
                .resolve("s" + script.getPersistentId())
                .resolve("v" + versionNo + "-" + sanitizeFilename(originalFilename));
        ScriptFiles.writeAtomically(target, content);
        PersistentScriptVersionRecord record = scriptVersionRepository.save(new PersistentScriptVersionRecord(
                script.getPersistentId(),
                script.getProjectId(),
                versionNo,
                originalFilename,
                target.toString(),
                uploadedBy,
                Instant.now(),
                ScriptVersionStatus.PUBLISHED,
                remark
        ));
        script.bumpLatestVersionNo(versionNo);
        scriptRepository.save(script);
        return record.toScriptVersion();
    }

    @Transactional
    public ScriptDefinition createScript(long projectId, String name, String uploadedBy) {
        if (!projectRepository.existsById(projectId)) {
            throw new ProjectValidationException("project does not exist");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new ScriptValidationException("script name is required");
        }
        String trimmed = name.trim();
        if (scriptRepository.existsByProjectIdAndName(projectId, trimmed)) {
            throw new ScriptValidationException("script name already exists");
        }
        PersistentScriptRecord script = scriptRepository.save(
                PersistentScriptRecord.persistentOf(projectId, trimmed, 0, uploadedBy, Instant.now()));
        PersistentScriptVersionRecord draft = publicationService.createScaffoldDraft(script, uploadedBy);
        return getScriptDefinition(projectId, draft.getId());
    }

    @Transactional(readOnly = true)
    public ScriptDefinition getScriptDefinition(long projectId, long versionId) {
        PersistentScriptVersionRecord record = requireScriptVersion(projectId, versionId);
        List<ScriptVersion> versions = scriptVersionRepository.findAllByProjectIdOrderByVersionNoDesc(projectId).stream()
                .map(PersistentScriptVersionRecord::toScriptVersion)
                .toList();
        return toScriptDefinition(record, versions);
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
    public ScriptVersion saveScriptContent(long projectId, long versionId, String content, String filename, String uploadedBy) {
        PersistentScriptVersionRecord baseVersion = requireScriptVersion(projectId, versionId);
        requireDraftMutable(baseVersion);
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

        ScriptFiles.writeAtomically(Path.of(baseVersion.getStoredPath()), content);

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
        PersistentScriptVersionRecord baseVersion = requireScriptVersion(projectId, versionId);
        requireDraftMutable(baseVersion);
        String baseContent = readStoredContent(baseVersion);
        String content = jmeterScriptPatcher.patch(baseContent, steps == null ? List.of() : steps);
        if (steps != null && !steps.isEmpty() && jmeterScriptParser.parseSteps(content).isEmpty()) {
            throw new ScriptValidationException("failed to persist script steps");
        }
        ScriptVersion version = saveScriptContent(projectId, versionId, content, filename, uploadedBy);
        return getScriptDefinition(projectId, version.id());
    }

    public PersistentScriptVersionRecord requireScriptVersion(long projectId, long versionId) {
        return scriptVersionRepository.findByIdAndProjectId(versionId, projectId)
                .orElseThrow(() -> new ScriptValidationException("script version does not exist"));
    }

    private void requireDraftMutable(PersistentScriptVersionRecord version) {
        if (version.getStatus() != ScriptVersionStatus.DRAFT) {
            throw new ScriptValidationException("published version is immutable, edit the draft instead");
        }
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

    private ScriptDefinition toScriptDefinition(PersistentScriptVersionRecord record, List<ScriptVersion> versions) {
        String content = readStoredContent(record);
        List<ScriptStepDefinition> steps = jmeterScriptParser.parseSteps(content);
        return new ScriptDefinition(
                record.getId(),
                record.getProjectId(),
                nameOf(record.getOriginalFilename()),
                record.getOriginalFilename(),
                record.getVersionNo(),
                record.getScriptId() == null ? 0 : record.getScriptId(),
                record.getStatus().name(),
                record.getRemark() == null ? "" : record.getRemark(),
                record.toScriptVersion().uploadedAt(),
                steppingThreadGroupSupported(),
                steps,
                versions
        );
    }

    private String readStoredContent(PersistentScriptVersionRecord record) {
        return ScriptFiles.read(Path.of(record.getStoredPath()));
    }

    private String nameOf(String filename) {
        return filename.replaceFirst("(?i)\\.jmx$", "");
    }

    private String toJmxFilename(String name) {
        return name.toLowerCase(Locale.ROOT).endsWith(".jmx") ? name : name + ".jmx";
    }

    private String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private boolean steppingThreadGroupSupported() {
        // 实际执行环境是远端 Docker 容器，能力由注入容器的 jmeter-runtime/*.jar 决定，
        // 而不是平台本机的 JMeter 安装（本机 JMeter 不参与执行）。
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath:jmeter-runtime/*.jar");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename != null && filename.contains("casutg")) {
                    return true;
                }
            }
            return false;
        } catch (IOException exception) {
            return false;
        }
    }
}
