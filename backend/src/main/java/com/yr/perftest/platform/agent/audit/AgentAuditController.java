package com.yr.perftest.platform.agent.audit;

import com.yr.perftest.platform.agent.AgentExceptionHandler;
import com.yr.perftest.platform.agent.contract.ApiResponse;
import com.yr.perftest.platform.facade.AuditFacade;
import com.yr.perftest.platform.governance.PersistentExecutionAuditRecord;
import com.yr.perftest.platform.governance.PersistentRequestAuditRecord;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 审计轨迹查询 agent 面入口（T13）：工具调用重建平台侧操作轨迹的校验口。
 */
@RestController
@RequestMapping("/api/agent/audit")
public class AgentAuditController {
    private final AuditFacade auditFacade;

    public AgentAuditController(AuditFacade auditFacade) {
        this.auditFacade = auditFacade;
    }

    @GetMapping("/requests")
    public ApiResponse<RequestAuditData> recentRequests(@RequestParam(defaultValue = "50") int limit) {
        List<PersistentRequestAuditRecord> items = auditFacade.recentRequests(limit);
        return ApiResponse.success(
                UUID.randomUUID().toString(),
                AgentExceptionHandler.SCHEMA_VERSION,
                new RequestAuditData(items)
        );
    }

    @GetMapping("/executions")
    public ApiResponse<ExecutionAuditData> executions(@RequestParam long executionId) {
        List<PersistentExecutionAuditRecord> items = auditFacade.executions(executionId);
        return ApiResponse.success(
                UUID.randomUUID().toString(),
                AgentExceptionHandler.SCHEMA_VERSION,
                new ExecutionAuditData(items)
        );
    }

    public record RequestAuditData(List<PersistentRequestAuditRecord> items) {
    }

    public record ExecutionAuditData(List<PersistentExecutionAuditRecord> items) {
    }
}
