package com.yr.perftest.platform.monitoring;

import com.yr.perftest.platform.execution.ExecutionValidationException;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRecord;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRepository;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.PersistentTaskScenarioRecord;
import com.yr.perftest.platform.task.PersistentTaskScenarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

@Service
public class ExecutionMonitorBindingService {
    private final PersistentExecutionMonitorBindingRepository bindingRepository;
    private final PersistentMonitorTargetRepository targetRepository;
    private final PersistentScenarioExecutionRepository executionRepository;
    private final PersistentTaskScenarioRepository scenarioRepository;
    private final PersistentTaskPlanRepository planRepository;
    private final MonitorTargetService targetService;

    public ExecutionMonitorBindingService(
            PersistentExecutionMonitorBindingRepository bindingRepository,
            PersistentMonitorTargetRepository targetRepository,
            PersistentScenarioExecutionRepository executionRepository,
            PersistentTaskScenarioRepository scenarioRepository,
            PersistentTaskPlanRepository planRepository,
            MonitorTargetService targetService
    ) {
        this.bindingRepository = bindingRepository;
        this.targetRepository = targetRepository;
        this.executionRepository = executionRepository;
        this.scenarioRepository = scenarioRepository;
        this.planRepository = planRepository;
        this.targetService = targetService;
    }

    @Transactional
    public void bindTargets(long projectId, long executionId, List<Long> targetIds) {
        bindingRepository.deleteAllByExecutionId(executionId);
        List<Long> ids = targetIds == null ? List.of() : new LinkedHashSet<>(targetIds).stream().toList();
        if (ids.isEmpty()) {
            return;
        }
        List<PersistentMonitorTargetRecord> targets = targetRepository.findAllByIdIn(ids);
        if (targets.size() != ids.size()) {
            throw new MonitoringValidationException("monitor target does not exist");
        }
        targets.forEach(target -> {
            if (!target.getProjectId().equals(projectId)) {
                throw new MonitoringValidationException("monitor target project is invalid");
            }
            if (!target.getEnabled()) {
                throw new MonitoringValidationException("monitor target is disabled");
            }
        });
        ids.forEach(targetId -> bindingRepository.save(new PersistentExecutionMonitorBindingRecord(executionId, targetId)));
    }

    @Transactional
    public void markStart(long executionId, Instant startTime) {
        bindingRepository.findAllByExecutionIdOrderByIdAsc(executionId).forEach(binding -> binding.markStart(startTime));
    }

    @Transactional
    public void markEnd(long executionId, Instant endTime) {
        bindingRepository.findAllByExecutionIdOrderByIdAsc(executionId).forEach(binding -> binding.markEnd(endTime));
    }

    @Transactional
    public void deleteBindings(long executionId) {
        bindingRepository.deleteAllByExecutionId(executionId);
    }

    @Transactional(readOnly = true)
    public TargetMonitoringResult getExecutionMonitoring(long executionId) {
        PersistentScenarioExecutionRecord execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new ExecutionValidationException("execution does not exist"));
        PersistentTaskScenarioRecord scenario = scenarioRepository.findById(execution.getScenarioId())
                .orElseThrow(() -> new ExecutionValidationException("scenario does not exist"));
        PersistentTaskPlanRecord plan = planRepository.findById(scenario.getPlanId())
                .orElseThrow(() -> new ExecutionValidationException("task plan does not exist"));
        List<PersistentExecutionMonitorBindingRecord> bindings = bindingRepository.findAllByExecutionIdOrderByIdAsc(execution.getId());
        List<MonitorTarget> targets = bindings.stream()
                .map(binding -> targetRepository.findById(binding.getTargetId()))
                .flatMap(java.util.Optional::stream)
                .map(targetService::toTarget)
                .toList();
        Instant startTime = bindings.stream()
                .map(PersistentExecutionMonitorBindingRecord::getStartTime)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(execution.getStartTime());
        Instant endTime = bindings.stream()
                .map(PersistentExecutionMonitorBindingRecord::getEndTime)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(execution.getEndTime());
        return new TargetMonitoringResult(
                execution.getId(),
                execution.getId(),
                startTime,
                endTime,
                toServerTargets(targets),
                toJvmInstances(targets),
                targets
        );
    }

    private List<ServerSelectable> toServerTargets(List<MonitorTarget> targets) {
        return targets.stream()
                .map(target -> new ServerSelectable(target.id(), target.name(), target.host()))
                .toList();
    }

    private List<JvmInstanceSelectable> toJvmInstances(List<MonitorTarget> targets) {
        List<JvmInstanceSelectable> instances = new ArrayList<>();
        for (MonitorTarget target : targets) {
            for (MonitorItem item : target.items()) {
                if (item.type() != MonitorItemType.JAVA_JMX_AGENT) {
                    continue;
                }
                instances.add(new JvmInstanceSelectable(
                        target.id(),
                        item.id(),
                        item.serviceName() != null && !item.serviceName().isBlank() ? item.serviceName() : item.name(),
                        target.host(),
                        item.processKeyword()
                ));
            }
        }
        return instances;
    }
}
