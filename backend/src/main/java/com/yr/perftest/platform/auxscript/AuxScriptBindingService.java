package com.yr.perftest.platform.auxscript;

import com.yr.perftest.platform.execution.ExecutionValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 场景辅助脚本绑定（模块 09）：任务绑定的是脚本版本号，替换式保存。
 */
@Service
public class AuxScriptBindingService {
    private final PersistentAuxScriptBindingRepository bindingRepository;
    private final PersistentAuxScriptVersionRepository versionRepository;

    public AuxScriptBindingService(
            PersistentAuxScriptBindingRepository bindingRepository,
            PersistentAuxScriptVersionRepository versionRepository
    ) {
        this.bindingRepository = bindingRepository;
        this.versionRepository = versionRepository;
    }

    @Transactional
    public List<BindingView> replaceBindings(long scenarioId, List<BindingInput> bindings, String createdBy) {
        bindingRepository.deleteAllByScenarioId(scenarioId);
        List<BindingView> views = new ArrayList<>();
        int index = 0;
        for (BindingInput input : bindings) {
            requireVersion(input.scriptVersionId());
            PersistentAuxScriptBindingRecord record = bindingRepository.save(
                    new PersistentAuxScriptBindingRecord(
                            scenarioId,
                            input.phase(),
                            input.scriptVersionId(),
                            input.failurePolicy() == null
                                    ? AuxScriptFailurePolicy.STOP_TASK
                                    : input.failurePolicy(),
                            input.sortOrder() == null ? index : input.sortOrder(),
                            createdBy
                    )
            );
            views.add(bindingView(record));
            index++;
        }
        return List.copyOf(views);
    }

    @Transactional(readOnly = true)
    public List<BindingView> listBindings(long scenarioId) {
        return bindingRepository.findAllByScenarioIdOrderByPhaseAscSortOrderAsc(scenarioId).stream()
                .map(this::bindingView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PersistentAuxScriptBindingRecord> bindingsFor(long scenarioId, AuxScriptPhase phase) {
        return bindingRepository.findAllByScenarioIdOrderByPhaseAscSortOrderAsc(scenarioId).stream()
                .filter(binding -> binding.getPhase() == phase)
                .toList();
    }

    private BindingView bindingView(PersistentAuxScriptBindingRecord record) {
        return new BindingView(
                record.getId(),
                record.getScenarioId(),
                record.getPhase().name(),
                record.getScriptVersionId(),
                record.getFailurePolicy().name(),
                record.getSortOrder(),
                record.getCreatedBy(),
                record.getCreatedAt()
        );
    }

    private void requireVersion(long versionId) {
        versionRepository.findById(versionId)
                .orElseThrow(() -> new ExecutionValidationException(
                        "aux script version " + versionId + " does not exist"));
    }

    public record BindingInput(
            AuxScriptPhase phase,
            long scriptVersionId,
            AuxScriptFailurePolicy failurePolicy,
            Integer sortOrder
    ) {
    }

    public record BindingView(
            long bindingId,
            long scenarioId,
            String phase,
            long scriptVersionId,
            String failurePolicy,
            int sortOrder,
            String createdBy,
            java.time.Instant createdAt
    ) {
    }
}
