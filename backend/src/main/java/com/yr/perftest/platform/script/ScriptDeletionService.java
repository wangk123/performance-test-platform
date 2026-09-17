package com.yr.perftest.platform.script;

import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.PersistentTaskScenarioRecord;
import com.yr.perftest.platform.task.PersistentTaskScenarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 删除守卫：DRAFT 随时可删；PUBLISHED 被场景引用则拒绝（报错列场景名）；
 * 脚本删除为级联（全部版本 + 文件），任一 PUBLISHED 被引用整体拒绝。
 * 守卫范围 = 场景当前绑定；历史执行靠 storage/executions 目录复制件自包含。
 */
@Service
public class ScriptDeletionService {
    private final PersistentScriptRepository scriptRepository;
    private final PersistentScriptVersionRepository versionRepository;
    private final PersistentTaskScenarioRepository scenarioRepository;
    private final PersistentTaskPlanRepository planRepository;

    public ScriptDeletionService(
            PersistentScriptRepository scriptRepository,
            PersistentScriptVersionRepository versionRepository,
            PersistentTaskScenarioRepository scenarioRepository,
            PersistentTaskPlanRepository planRepository
    ) {
        this.scriptRepository = scriptRepository;
        this.versionRepository = versionRepository;
        this.scenarioRepository = scenarioRepository;
        this.planRepository = planRepository;
    }

    @Transactional
    public void deleteVersion(long projectId, long versionId) {
        PersistentScriptVersionRecord version = versionRepository.findByIdAndProjectId(versionId, projectId)
                .orElseThrow(() -> new ScriptValidationException("script version does not exist"));
        List<PersistentTaskScenarioRecord> refs = scenariosReferencing(projectId, List.of(versionId));
        if (version.getStatus() == ScriptVersionStatus.PUBLISHED && !refs.isEmpty()) {
            throw new ScriptValidationException("该版本被场景" + scenarioNames(refs) + "引用，无法删除");
        }
        versionRepository.delete(version);
        ScriptFiles.deleteQuietly(Path.of(version.getStoredPath()));
    }

    @Transactional
    public void deleteScript(long projectId, long scriptId) {
        PersistentScriptRecord script = scriptRepository.findByIdAndProjectId(scriptId, projectId)
                .orElseThrow(() -> new ScriptValidationException("script does not exist"));
        List<PersistentScriptVersionRecord> versions =
                versionRepository.findAllByScriptIdOrderByVersionNoDesc(scriptId);
        List<PersistentTaskScenarioRecord> refs = scenariosReferencing(projectId,
                versions.stream().map(PersistentScriptVersionRecord::getId).toList());
        if (!refs.isEmpty()) {
            throw new ScriptValidationException("脚本下存在被场景引用的版本（" + scenarioNames(refs) + "），无法删除");
        }
        versions.forEach(version -> ScriptFiles.deleteQuietly(Path.of(version.getStoredPath())));
        versionRepository.deleteAllInBatch(versions);
        scriptRepository.delete(script);
    }

    List<PersistentTaskScenarioRecord> scenariosReferencing(long projectId, List<Long> versionIds) {
        if (versionIds.isEmpty()) {
            return List.of();
        }
        Set<Long> planIdsOfProject = planRepository.findAllByProjectIdOrderByIdDesc(projectId).stream()
                .map(plan -> plan.getId())
                .collect(Collectors.toSet());
        return scenarioRepository.findByScriptVersionIdIn(versionIds).stream()
                .filter(scenario -> planIdsOfProject.contains(scenario.getPlanId()))
                .toList();
    }

    Map<Long, List<PersistentTaskScenarioRecord>> refsByVersion(long projectId, List<Long> versionIds) {
        return scenariosReferencing(projectId, versionIds).stream()
                .collect(Collectors.groupingBy(PersistentTaskScenarioRecord::getScriptVersionId));
    }

    private String scenarioNames(List<PersistentTaskScenarioRecord> refs) {
        return refs.stream()
                .map(PersistentTaskScenarioRecord::getName)
                .map(name -> "「" + name + "」")
                .collect(Collectors.joining("、"));
    }
}
