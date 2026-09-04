package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.task.ExecutionLifecycleEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 终态事件 → 计划状态与文档回填。回填失败只告警，不影响执行主流程。 */
@Component
public class PlanExecutionLifecycleListener {
    private static final Logger log = LoggerFactory.getLogger(PlanExecutionLifecycleListener.class);

    private final PlanWorkflowService workflowService;

    public PlanExecutionLifecycleListener(PlanWorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @EventListener
    public void onExecutionTerminal(ExecutionLifecycleEvent event) {
        try {
            workflowService.onExecutionTerminal(event.executionId());
        } catch (Exception exception) {
            log.warn("plan backfill failed for execution {}: {}", event.executionId(), exception.getMessage());
        }
    }
}
