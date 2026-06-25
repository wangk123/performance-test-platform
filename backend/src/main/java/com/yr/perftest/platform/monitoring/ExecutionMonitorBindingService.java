package com.yr.perftest.platform.monitoring;

import com.yr.perftest.platform.execution.ExecutionValidationException;
import com.yr.perftest.platform.execution.PersistentTaskExecutionRecord;
import com.yr.perftest.platform.execution.PersistentTaskExecutionRepository;
import com.yr.perftest.platform.execution.PersistentTestTaskRecord;
import com.yr.perftest.platform.execution.PersistentTestTaskRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class ExecutionMonitorBindingService {
    private final PersistentExecutionMonitorBindingRepository bindingRepository;
    private final PersistentMonitorTargetRepository targetRepository;
    private final PersistentTaskExecutionRepository executionRepository;
    private final PersistentTestTaskRepository taskRepository;
    private final MonitorTargetService targetService;
    private final String grafanaDashboardUrl;

    public ExecutionMonitorBindingService(
            PersistentExecutionMonitorBindingRepository bindingRepository,
            PersistentMonitorTargetRepository targetRepository,
            PersistentTaskExecutionRepository executionRepository,
            PersistentTestTaskRepository taskRepository,
            MonitorTargetService targetService,
            @Value("${platform.monitoring.grafana-dashboard-url:http://127.0.0.1:3000/d/jmx/jmx-exporter-jvm?orgId=1}") String grafanaDashboardUrl
    ) {
        this.bindingRepository = bindingRepository;
        this.targetRepository = targetRepository;
        this.executionRepository = executionRepository;
        this.taskRepository = taskRepository;
        this.targetService = targetService;
        this.grafanaDashboardUrl = grafanaDashboardUrl;
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
    public TargetMonitoringResult getTaskMonitoring(long taskId) {
        PersistentTestTaskRecord task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ExecutionValidationException("task does not exist"));
        PersistentTaskExecutionRecord execution = executionRepository.findFirstByTaskIdOrderByIdDesc(task.getId())
                .orElseThrow(() -> new ExecutionValidationException("execution does not exist"));
        List<PersistentExecutionMonitorBindingRecord> bindings = bindingRepository.findAllByExecutionIdOrderByIdAsc(execution.getId());
        List<MonitorTarget> targets = bindings.stream()
                .map(binding -> targetRepository.findById(binding.getTargetId()))
                .flatMap(java.util.Optional::stream)
                .map(targetService::toTarget)
                .toList();
        Instant startTime = bindings.stream()
                .map(PersistentExecutionMonitorBindingRecord::getStartTime)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(execution.getStartTime());
        Instant endTime = bindings.stream()
                .map(PersistentExecutionMonitorBindingRecord::getEndTime)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(execution.getEndTime());
        return new TargetMonitoringResult(task.getId(), execution.getId(), startTime, endTime, grafanaUrl(targets, startTime, endTime), targets);
    }

    private String grafanaUrl(List<MonitorTarget> targets, Instant startTime, Instant endTime) {
        if (targets.isEmpty()) {
            return null;
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(grafanaDashboardUrl)
                .queryParam("var-project_id", targets.get(0).projectId());
        targets.forEach(target -> builder.queryParam("var-target_id", target.id()));
        if (startTime != null) {
            builder.queryParam("from", startTime.toEpochMilli());
        }
        if (endTime != null) {
            builder.queryParam("to", endTime.toEpochMilli());
        }
        return builder.build().toUriString();
    }
}
