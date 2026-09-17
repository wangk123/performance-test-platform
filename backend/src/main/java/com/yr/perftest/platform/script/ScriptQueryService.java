package com.yr.perftest.platform.script;

import com.yr.perftest.platform.project.PersistentProjectRepository;
import com.yr.perftest.platform.project.ProjectValidationException;
import com.yr.perftest.platform.task.PersistentTaskScenarioRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 脚本维度聚合：资产列表（最新发布 + 草稿标记 + 使用统计）与版本历史（含引用场景名）。 */
@Service
public class ScriptQueryService {
    private final PersistentProjectRepository projectRepository;
    private final PersistentScriptRepository scriptRepository;
    private final PersistentScriptVersionRepository versionRepository;
    private final ScriptDeletionService deletionService;

    public ScriptQueryService(
            PersistentProjectRepository projectRepository,
            PersistentScriptRepository scriptRepository,
            PersistentScriptVersionRepository versionRepository,
            ScriptDeletionService deletionService
    ) {
        this.projectRepository = projectRepository;
        this.scriptRepository = scriptRepository;
        this.versionRepository = versionRepository;
        this.deletionService = deletionService;
    }

    @Transactional(readOnly = true)
    public List<ScriptAssetSummary> listAssets(long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ProjectValidationException("project does not exist");
        }
        List<PersistentScriptVersionRecord> allVersions =
                versionRepository.findAllByProjectIdOrderByVersionNoDesc(projectId);
        Map<Long, List<PersistentScriptVersionRecord>> byScript = allVersions.stream()
                .filter(v -> v.getScriptId() != null)
                .collect(Collectors.groupingBy(PersistentScriptVersionRecord::getScriptId));
        Map<Long, List<PersistentTaskScenarioRecord>> refsByVersion = deletionService.refsByVersion(projectId,
                allVersions.stream().map(PersistentScriptVersionRecord::getId).toList());
        return scriptRepository.findAllByProjectIdOrderByIdAsc(projectId).stream()
                .map(script -> toSummary(script,
                        byScript.getOrDefault(script.getPersistentId(), List.of()), refsByVersion))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ScriptVersionWithRefs> listVersions(long projectId, long scriptId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ProjectValidationException("project does not exist");
        }
        scriptRepository.findByIdAndProjectId(scriptId, projectId)
                .orElseThrow(() -> new ScriptValidationException("script does not exist"));
        List<PersistentScriptVersionRecord> versions =
                versionRepository.findAllByScriptIdOrderByVersionNoDesc(scriptId);
        Map<Long, List<PersistentTaskScenarioRecord>> refsByVersion = deletionService.refsByVersion(projectId,
                versions.stream().map(PersistentScriptVersionRecord::getId).toList());
        List<PersistentScriptVersionRecord> ordered = new ArrayList<>();
        versions.stream().filter(v -> v.getStatus() == ScriptVersionStatus.DRAFT).forEach(ordered::add);
        versions.stream()
                .filter(v -> v.getStatus() == ScriptVersionStatus.PUBLISHED)
                .sorted(Comparator.comparingInt(PersistentScriptVersionRecord::getVersionNo).reversed())
                .forEach(ordered::add);
        return ordered.stream()
                .map(v -> new ScriptVersionWithRefs(
                        v.toScriptVersion(),
                        refsByVersion.getOrDefault(v.getId(), List.of()).stream()
                                .map(PersistentTaskScenarioRecord::getName).toList()))
                .toList();
    }

    private ScriptAssetSummary toSummary(
            PersistentScriptRecord script,
            List<PersistentScriptVersionRecord> versions,
            Map<Long, List<PersistentTaskScenarioRecord>> refsByVersion) {
        PersistentScriptVersionRecord latestPublished = versions.stream()
                .filter(v -> v.getStatus() == ScriptVersionStatus.PUBLISHED)
                .max(Comparator.comparingInt(PersistentScriptVersionRecord::getVersionNo))
                .orElse(null);
        PersistentScriptVersionRecord draft = versions.stream()
                .filter(v -> v.getStatus() == ScriptVersionStatus.DRAFT)
                .findFirst().orElse(null);
        int currentCount = latestPublished == null ? 0 : refsByVersion
                .getOrDefault(latestPublished.getId(), List.of()).size();
        // 仅统计本脚本非最新发布版本上的场景绑定，不能拿全项目引用总数求差
        int outdatedCount = versions.stream()
                .filter(v -> latestPublished == null || v.getId() != latestPublished.getId())
                .mapToInt(v -> refsByVersion.getOrDefault(v.getId(), List.of()).size())
                .sum();
        return new ScriptAssetSummary(
                script.getPersistentId(),
                script.getProjectId(),
                script.getName(),
                latestPublished == null ? 0 : latestPublished.getVersionNo(),
                latestPublished == null ? null : latestPublished.getVersionLabel(),
                latestPublished == null ? null : latestPublished.toScriptVersion(),
                draft != null,
                draft == null ? null : draft.getUpdatedAt(),
                currentCount,
                outdatedCount
        );
    }

    public record ScriptVersionWithRefs(ScriptVersion version, List<String> referencedScenarioNames) {
    }
}
