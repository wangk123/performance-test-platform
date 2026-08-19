package com.yr.perftest.platform.auxscript;

import com.yr.perftest.platform.execution.ExecutionValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 辅助脚本管理（模块 09）：项目私有/系统公共脚本与不可变版本。
 */
@Service
public class AuxScriptService {
    private final PersistentAuxScriptRepository scriptRepository;
    private final PersistentAuxScriptVersionRepository versionRepository;

    public AuxScriptService(
            PersistentAuxScriptRepository scriptRepository,
            PersistentAuxScriptVersionRepository versionRepository
    ) {
        this.scriptRepository = scriptRepository;
        this.versionRepository = versionRepository;
    }

    @Transactional
    public AuxScriptView createScript(
            Long projectId,
            String name,
            AuxScriptType type,
            AuxScriptScope scope,
            String description,
            String createdBy
    ) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("aux script name is required");
        }
        if (type == null) {
            throw new IllegalArgumentException("aux script type is required");
        }
        AuxScriptScope effectiveScope = scope == null ? AuxScriptScope.PROJECT : scope;
        Long effectiveProjectId = effectiveScope == AuxScriptScope.SYSTEM ? null : projectId;
        PersistentAuxScriptRecord record = scriptRepository.save(new PersistentAuxScriptRecord(
                effectiveProjectId,
                name.trim(),
                type,
                effectiveScope,
                blankToNull(description),
                createdBy
        ));
        return scriptView(record);
    }

    @Transactional
    public AuxScriptVersionView addVersion(long scriptId, String sourceCode, String remark, String createdBy) {
        requireScript(scriptId);
        if (sourceCode == null || sourceCode.isBlank()) {
            throw new IllegalArgumentException("aux script source code is required");
        }
        int versionNo = (int) versionRepository.countByScriptId(scriptId) + 1;
        PersistentAuxScriptVersionRecord version = versionRepository.save(
                new PersistentAuxScriptVersionRecord(
                        scriptId,
                        versionNo,
                        sourceCode,
                        blankToNull(remark),
                        createdBy
                )
        );
        return new AuxScriptVersionView(
                version.getId(),
                version.getScriptId(),
                version.getVersionNo(),
                version.getRemark(),
                version.getCreatedBy(),
                version.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<AuxScriptView> listScripts(Long projectId) {
        List<PersistentAuxScriptRecord> records = new ArrayList<>(
                scriptRepository.findAllByScopeOrderByIdDesc(AuxScriptScope.SYSTEM));
        if (projectId != null) {
            records.addAll(scriptRepository.findAllByProjectIdOrderByIdDesc(projectId));
        }
        records.sort((first, second) -> Long.compare(second.getId(), first.getId()));
        return records.stream().map(this::scriptView).toList();
    }

    @Transactional(readOnly = true)
    public AuxScriptView getScript(long scriptId) {
        return scriptView(requireScript(scriptId));
    }

    private AuxScriptView scriptView(PersistentAuxScriptRecord record) {
        List<PersistentAuxScriptVersionRecord> versions =
                versionRepository.findAllByScriptIdOrderByVersionNoAsc(record.getId());
        return new AuxScriptView(
                record.getId(),
                record.getProjectId(),
                record.getName(),
                record.getType().name(),
                record.getScope().name(),
                record.getDescription(),
                record.getCreatedBy(),
                record.getCreatedAt(),
                versions.stream()
                        .map(version -> new AuxScriptVersionView(
                                version.getId(),
                                version.getScriptId(),
                                version.getVersionNo(),
                                version.getRemark(),
                                version.getCreatedBy(),
                                version.getCreatedAt()))
                        .toList()
        );
    }

    private PersistentAuxScriptRecord requireScript(long scriptId) {
        return scriptRepository.findById(scriptId)
                .orElseThrow(() -> new ExecutionValidationException("aux script " + scriptId + " does not exist"));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record AuxScriptView(
            long scriptId,
            Long projectId,
            String name,
            String type,
            String scope,
            String description,
            String createdBy,
            Instant createdAt,
            List<AuxScriptVersionView> versions
    ) {
    }

    public record AuxScriptVersionView(
            long versionId,
            long scriptId,
            int versionNo,
            String remark,
            String createdBy,
            Instant createdAt
    ) {
    }
}
