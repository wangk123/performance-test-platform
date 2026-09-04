package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.execution.distributed.ExecutionNodeRole;
import com.yr.perftest.platform.execution.distributed.ExecutionNodeStatus;
import com.yr.perftest.platform.execution.distributed.PersistentExecutionNodeRecord;
import com.yr.perftest.platform.execution.distributed.PersistentExecutionNodeRepository;
import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.script.PersistentScriptVersionRecord;
import com.yr.perftest.platform.script.PersistentScriptVersionRepository;
import com.yr.perftest.platform.task.ExecutionControlService;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.TaskPlanService;
import com.yr.perftest.platform.task.TaskScenarioService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 快捷执行：单事务内 建计划(EXECUTION/PENDING)→系统批注→建场景(带脚本)→start（设计 §10.4）。 */
@Service
public class PlanQuickExecuteService {

    public record QuickExecuteResult(long planId, long scenarioId, long executionId) {
    }

    private final PersistentScriptVersionRepository scriptVersionRepository;
    private final PersistentExecutionNodeRepository executionNodeRepository;
    private final PersistentTaskPlanRepository planRepository;
    private final TaskPlanService planService;
    private final TaskScenarioService scenarioService;
    private final ExecutionControlService executionControlService;
    private final PlanWorkflowService workflowService;

    public PlanQuickExecuteService(
            PersistentScriptVersionRepository scriptVersionRepository,
            PersistentExecutionNodeRepository executionNodeRepository,
            PersistentTaskPlanRepository planRepository,
            TaskPlanService planService,
            TaskScenarioService scenarioService,
            ExecutionControlService executionControlService,
            PlanWorkflowService workflowService
    ) {
        this.scriptVersionRepository = scriptVersionRepository;
        this.executionNodeRepository = executionNodeRepository;
        this.planRepository = planRepository;
        this.planService = planService;
        this.scenarioService = scenarioService;
        this.executionControlService = executionControlService;
        this.workflowService = workflowService;
    }

    @Transactional
    public QuickExecuteResult quickExecute(long scriptVersionId, HumanPrincipal actor) {
        PersistentScriptVersionRecord script = scriptVersionRepository.findById(scriptVersionId)
                .orElseThrow(() -> new PlanValidationException("PLAN_INVALID：脚本版本不存在"));
        String username = actor == null ? "admin" : actor.username();
        String planName = scriptDisplayName(script) + " / 即时执行";
        com.yr.perftest.platform.task.TaskPlan plan = planService.createPlan(
                script.getProjectId(), planName, "从脚本列表直接执行", firstAvailableControllerNodeId(),
                null, null, username, null);
        PersistentTaskPlanRecord raw = planRepository.findById(plan.id()).orElseThrow();
        raw.forceState(PlanPhase.EXECUTION, PlanStatus.PENDING);
        planRepository.save(raw);
        workflowService.systemComment(plan.id(), "快捷执行自动通过评审（操作人：" + username + "）");
        com.yr.perftest.platform.task.TaskScenario scenario = scenarioService.createScenario(
                plan.id(), scriptVersionId, scriptDisplayName(script), null, null, null, null, null, null, null);
        ExecutionControlService.StartOutcome outcome = executionControlService.start(
                new ExecutionControlService.StartCommand(scenario.id(), null, null, null), null);
        return new QuickExecuteResult(plan.id(), scenario.id(), outcome.executionId());
    }

    /**
     * 快捷执行无 UI 选节点环节，计划默认 Controller 取节点池中 id 最小的 AVAILABLE 控制节点；
     * 无可用节点则保持空，由执行链路以既有「controller node is required」同步报错。
     */
    private Long firstAvailableControllerNodeId() {
        return executionNodeRepository.findAllByOrderByIdDesc().stream()
                .filter(node -> node.getStatus() == ExecutionNodeStatus.AVAILABLE)
                .filter(node -> node.getRole() == ExecutionNodeRole.CONTROLLER
                        || node.getRole() == ExecutionNodeRole.BOTH)
                .min(java.util.Comparator.comparingLong(PersistentExecutionNodeRecord::getId))
                .map(PersistentExecutionNodeRecord::getId)
                .orElse(null);
    }

    private String scriptDisplayName(PersistentScriptVersionRecord script) {
        return script.getOriginalFilename() != null ? script.getOriginalFilename() : "脚本-" + script.getId();
    }
}
