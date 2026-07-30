package com.yr.perftest.platform.task;

import com.yr.perftest.platform.execution.ExecutionConfig;
import com.yr.perftest.platform.execution.ExecutionStatus;
import com.yr.perftest.platform.execution.ExecutionValidationException;
import com.yr.perftest.platform.execution.distributed.ExecutionNodeStatus;
import com.yr.perftest.platform.execution.distributed.PersistentExecutionNodeRecord;
import com.yr.perftest.platform.execution.distributed.PersistentExecutionNodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ExecutionPrecheckService {
    private final PersistentTaskScenarioRepository scenarioRepository;
    private final PersistentTaskPlanRepository planRepository;
    private final PersistentScenarioExecutionRepository executionRepository;
    private final PersistentExecutionNodeRepository nodeRepository;
    private final ExecutionConfigMerger configMerger;

    public ExecutionPrecheckService(
            PersistentTaskScenarioRepository scenarioRepository,
            PersistentTaskPlanRepository planRepository,
            PersistentScenarioExecutionRepository executionRepository,
            PersistentExecutionNodeRepository nodeRepository,
            ExecutionConfigMerger configMerger
    ) {
        this.scenarioRepository = scenarioRepository;
        this.planRepository = planRepository;
        this.executionRepository = executionRepository;
        this.nodeRepository = nodeRepository;
        this.configMerger = configMerger;
    }

    @Transactional(readOnly = true)
    public PrecheckReport precheck(long scenarioId, Long threadGroupConfigId, Integer threadGroupPresetSortOrder) {
        PersistentTaskScenarioRecord scenario = scenarioRepository.findById(scenarioId)
                .orElseThrow(() -> new ExecutionValidationException("scenario does not exist"));
        PersistentTaskPlanRecord plan = planRepository.findById(scenario.getPlanId())
                .orElseThrow(() -> new ExecutionValidationException("task plan does not exist"));

        ExecutionConfig config;
        try {
            config = configMerger.merge(plan, scenario, threadGroupConfigId, threadGroupPresetSortOrder);
        } catch (ExecutionValidationException exception) {
            return new PrecheckReport(
                    false,
                    List.of(exception.getMessage()),
                    List.of(),
                    null, null, null, null, null,
                    List.of()
            );
        }

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (config.threads() <= 0) {
            warnings.add("threads is not configured");
        }
        if (config.duration() <= 0) {
            warnings.add("duration is not configured");
        }

        List<PrecheckNode> nodes = new ArrayList<>();
        if (config.controllerNodeId() == null) {
            errors.add("controller node is required");
        } else {
            addNode(nodes, errors, warnings, config.controllerNodeId(), "CONTROLLER");
        }
        int workerCount;
        if (config.workerNodeIds().isEmpty()) {
            workerCount = config.controllerNodeId() == null ? 0 : 1;
        } else {
            workerCount = config.workerNodeIds().size();
            for (Long workerId : config.workerNodeIds()) {
                addNode(nodes, errors, warnings, workerId, "WORKER");
            }
        }

        long queueAhead = executionRepository.countByStatusIn(List.of(ExecutionStatus.QUEUED, ExecutionStatus.RUNNING));
        if (queueAhead > 0) {
            warnings.add("platform has " + queueAhead + " queued or running executions");
        }
        return new PrecheckReport(
                errors.isEmpty(),
                List.copyOf(errors),
                List.copyOf(warnings),
                config.threads(),
                config.duration(),
                workerCount,
                config.monitorTargetIds().size(),
                queueAhead,
                List.copyOf(nodes)
        );
    }

    private void addNode(List<PrecheckNode> nodes, List<String> errors, List<String> warnings, long nodeId, String role) {
        PersistentExecutionNodeRecord node = nodeRepository.findById(nodeId).orElse(null);
        if (node == null) {
            errors.add(role.toLowerCase() + " node " + nodeId + " does not exist");
            nodes.add(new PrecheckNode(nodeId, null, role, "MISSING", null));
            return;
        }
        if (node.getStatus() == ExecutionNodeStatus.OFFLINE) {
            warnings.add("node " + nodeId + " is OFFLINE");
        }
        nodes.add(new PrecheckNode(nodeId, node.getName(), role, node.getStatus().name(), node.getLastMessage()));
    }

    public record PrecheckNode(long nodeId, String name, String role, String status, String message) {
    }

    public record PrecheckReport(
            boolean valid,
            List<String> errors,
            List<String> warnings,
            Integer threads,
            Integer durationSeconds,
            Integer workerCount,
            Integer monitorTargetCount,
            Long queueAhead,
            List<PrecheckNode> nodes
    ) {
    }
}
