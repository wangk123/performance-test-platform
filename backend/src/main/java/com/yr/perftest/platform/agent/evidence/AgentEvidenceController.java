package com.yr.perftest.platform.agent.evidence;

import com.yr.perftest.platform.agent.AgentExceptionHandler;
import com.yr.perftest.platform.agent.contract.ApiResponse;
import com.yr.perftest.platform.facade.EvidenceFacade;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 证据链下钻 agent 面入口（T11）：按 executionId/时间窗/traceId/目标实例 收集
 * 基础与深度证据摘要，每源显式声明可用性。
 */
@RestController
@RequestMapping("/api/agent/executions")
public class AgentEvidenceController {
    private final EvidenceFacade evidenceFacade;

    public AgentEvidenceController(EvidenceFacade evidenceFacade) {
        this.evidenceFacade = evidenceFacade;
    }

    @GetMapping("/{executionId}/evidence")
    public ApiResponse<EvidenceFacade.EvidenceCollectView> collect(
            @PathVariable long executionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) List<String> target,
            @RequestParam(required = false) String label,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) List<String> kinds
    ) {
        EvidenceFacade.EvidenceCollectView view = evidenceFacade.collect(
                executionId,
                from,
                to,
                target,
                label,
                traceId,
                kinds
        );
        return ApiResponse.success(UUID.randomUUID().toString(), AgentExceptionHandler.SCHEMA_VERSION, view);
    }
}
