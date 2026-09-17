package com.yr.perftest.platform.script;

import com.yr.perftest.platform.project.PersistentProjectRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 脚本草稿与发布：草稿可反复保存（无草稿时惰性创建，基准 = 最新已发布内容或脚手架）；
 * 发布把 DRAFT 转为不可变 PUBLISHED 快照并推进版本号水位（只增不减）。
 */
@Service
public class ScriptPublicationService {
    private final PersistentScriptRepository scriptRepository;
    private final PersistentScriptVersionRepository versionRepository;
    private final PersistentProjectRepository projectRepository;
    private final JmeterScriptParser jmeterScriptParser;
    private final JmeterScriptPatcher jmeterScriptPatcher;
    private final JmeterScriptRenderer jmeterScriptRenderer;
    private final Jsr223SecretScanner jsr223SecretScanner;
    private final Path storageRoot;

    public ScriptPublicationService(
            PersistentScriptRepository scriptRepository,
            PersistentScriptVersionRepository versionRepository,
            PersistentProjectRepository projectRepository,
            JmeterScriptParser jmeterScriptParser,
            JmeterScriptPatcher jmeterScriptPatcher,
            JmeterScriptRenderer jmeterScriptRenderer,
            Jsr223SecretScanner jsr223SecretScanner,
            @Value("${platform.storage.root:./storage}") String storageRoot
    ) {
        this.scriptRepository = scriptRepository;
        this.versionRepository = versionRepository;
        this.projectRepository = projectRepository;
        this.jmeterScriptParser = jmeterScriptParser;
        this.jmeterScriptPatcher = jmeterScriptPatcher;
        this.jmeterScriptRenderer = jmeterScriptRenderer;
        this.jsr223SecretScanner = jsr223SecretScanner;
        this.storageRoot = Path.of(storageRoot);
    }

    @Transactional
    public SaveScriptDefinitionResult saveDraftDefinition(
            long projectId, long scriptId, String filename, List<ScriptStepDefinition> steps, String updatedBy) {
        PersistentScriptVersionRecord draft = ensureDraft(projectId, scriptId);
        String content = ScriptFiles.read(Path.of(draft.getStoredPath()));
        String patched = jmeterScriptPatcher.patch(content, steps == null ? List.of() : steps);
        if (steps != null && !steps.isEmpty() && jmeterScriptParser.parseSteps(patched).isEmpty()) {
            throw new ScriptValidationException("failed to persist script steps");
        }
        ScriptFiles.writeAtomically(Path.of(draft.getStoredPath()), patched);
        draft.updateMetadata(resolveFilename(draft, filename), updatedBy, Instant.now());
        return new SaveScriptDefinitionResult(draft.toScriptVersion(), jsr223SecretScanner.scanScriptContent(patched));
    }

    @Transactional
    public SaveScriptDefinitionResult saveDraftContent(
            long projectId, long scriptId, String filename, String content, String updatedBy) {
        if (content == null || content.trim().isEmpty()) {
            throw new ScriptValidationException("script content is empty");
        }
        validateJmx(content);
        PersistentScriptVersionRecord draft = ensureDraft(projectId, scriptId);
        ScriptFiles.writeAtomically(Path.of(draft.getStoredPath()), content);
        draft.updateMetadata(resolveFilename(draft, filename), updatedBy, Instant.now());
        return new SaveScriptDefinitionResult(draft.toScriptVersion(), jsr223SecretScanner.scanScriptContent(content));
    }

    @Transactional
    public ScriptVersion publish(long projectId, long scriptId, String versionLabel, String remark, String publishedBy) {
        PersistentScriptRecord script = requireScript(projectId, scriptId);
        if (remark == null || remark.isBlank()) {
            throw new ScriptValidationException("change remark is required");
        }
        String label = versionLabel == null ? "" : versionLabel.trim();
        if (!ScriptVersionLabels.isValid(label)) {
            throw new ScriptValidationException("version label must look like 1.0.0");
        }
        String watermark = script.getLatestVersionLabel();
        if (watermark != null && ScriptVersionLabels.compare(label, watermark) <= 0) {
            throw new ScriptValidationException("version label must be greater than " + watermark);
        }
        PersistentScriptVersionRecord draft = versionRepository
                .findFirstByScriptIdAndStatusOrderByVersionNoDesc(scriptId, ScriptVersionStatus.DRAFT)
                .orElseThrow(() -> new ScriptValidationException("no draft to publish"));
        int sequenceNo = script.getLatestVersionNo() + 1;
        draft.markPublished(sequenceNo, label, remark.trim(), publishedBy, Instant.now());
        script.recordPublished(sequenceNo, label);
        scriptRepository.save(script);
        return draft.toScriptVersion();
    }

    @Transactional
    public ScriptVersion forkDraft(long projectId, long scriptId, long sourceVersionId, String updatedBy) {
        PersistentScriptRecord script = requireScript(projectId, scriptId);
        PersistentScriptVersionRecord source = versionRepository.findById(sourceVersionId)
                .filter(v -> Long.valueOf(scriptId).equals(v.getScriptId()))
                .filter(v -> v.getStatus() == ScriptVersionStatus.PUBLISHED)
                .orElseThrow(() -> new ScriptValidationException(
                        "source version is not a published version of this script"));
        versionRepository.findFirstByScriptIdAndStatusOrderByVersionNoDesc(scriptId, ScriptVersionStatus.DRAFT)
                .ifPresent(versionRepository::delete);
        PersistentScriptVersionRecord draft = createDraftFrom(script,
                ScriptFiles.read(Path.of(source.getStoredPath())), updatedBy);
        return draft.toScriptVersion();
    }

    @Transactional
    public PersistentScriptVersionRecord createScaffoldDraft(PersistentScriptRecord script, String uploadedBy) {
        ScriptStepDefinition threadGroup = new ScriptStepDefinition(
                "thread-1",
                ScriptStepType.THREAD_GROUP.code(),
                "线程组 1",
                new ThreadGroupConfig(100, 60, 1, 600, false).toMap(),
                List.of()
        );
        return createDraftFrom(script, jmeterScriptRenderer.render(List.of(threadGroup)), uploadedBy);
    }

    private PersistentScriptVersionRecord ensureDraft(long projectId, long scriptId) {
        PersistentScriptRecord script = requireScript(projectId, scriptId);
        Optional<PersistentScriptVersionRecord> existing = versionRepository
                .findFirstByScriptIdAndStatusOrderByVersionNoDesc(scriptId, ScriptVersionStatus.DRAFT);
        if (existing.isPresent()) {
            return existing.get();
        }
        return versionRepository
                .findFirstByScriptIdAndStatusOrderByVersionNoDesc(scriptId, ScriptVersionStatus.PUBLISHED)
                .<PersistentScriptVersionRecord>map(latest ->
                        createDraftFrom(script, ScriptFiles.read(Path.of(latest.getStoredPath())), latest.getUploadedBy() == null ? script.getName() : latest.getUploadedBy()))
                .orElseGet(() -> createScaffoldDraft(script, script.getName()));
    }

    private PersistentScriptVersionRecord createDraftFrom(
            PersistentScriptRecord script, String content, String updatedBy) {
        validateJmx(content);
        String originalFilename = toJmxFilename(script.getName());
        Path target = storageRoot
                .resolve("scripts")
                .resolve(String.valueOf(script.getProjectId()))
                .resolve("s" + script.getPersistentId())
                .resolve("draft-" + sanitizeFilename(originalFilename));
        ScriptFiles.writeAtomically(target, content);
        return versionRepository.save(new PersistentScriptVersionRecord(
                script.getPersistentId(),
                script.getProjectId(),
                0,
                null,
                originalFilename,
                target.toString(),
                updatedBy,
                Instant.now(),
                ScriptVersionStatus.DRAFT,
                null
        ));
    }

    private PersistentScriptRecord requireScript(long projectId, long scriptId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ScriptValidationException("project does not exist");
        }
        return scriptRepository.findByIdAndProjectId(scriptId, projectId)
                .orElseThrow(() -> new ScriptValidationException("script does not exist"));
    }

    private String resolveFilename(PersistentScriptVersionRecord draft, String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return draft.getOriginalFilename();
        }
        String trimmed = filename.trim();
        return trimmed.toLowerCase(Locale.ROOT).endsWith(".jmx") ? trimmed : trimmed + ".jmx";
    }

    private String toJmxFilename(String name) {
        return name.toLowerCase(Locale.ROOT).endsWith(".jmx") ? name : name + ".jmx";
    }

    private String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private void validateJmx(String content) {
        if (!content.contains("<jmeterTestPlan")) {
            throw new ScriptValidationException("script content is not a JMeter test plan");
        }
    }
}
