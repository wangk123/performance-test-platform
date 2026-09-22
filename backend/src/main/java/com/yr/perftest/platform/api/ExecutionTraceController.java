package com.yr.perftest.platform.api;

import com.yr.perftest.platform.task.ExecutionTraceQueryService;
import com.yr.perftest.platform.task.ExecutionTraceViews;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 执行详情链路追踪面板数据源（Task 4）。鉴权仅登录（对齐既有 executions 端点）；
 * OAP 不可达时 200 + available=false，绝不 5xx。
 */
@RestController
@RequestMapping("/api")
public class ExecutionTraceController {
    private final ExecutionTraceQueryService executionTraceQueryService;

    public ExecutionTraceController(ExecutionTraceQueryService executionTraceQueryService) {
        this.executionTraceQueryService = executionTraceQueryService;
    }

    @GetMapping("/executions/{executionId}/traces")
    public ExecutionTraceViews.ExecutionTracesPageView getTraces(
            @PathVariable long executionId,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String endpoint,
            @RequestParam(required = false) Boolean onlyError,
            @RequestParam(required = false) Long minDurationMs,
            @RequestParam(defaultValue = "duration") String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return executionTraceQueryService.queryTraces(
                executionId, service, endpoint, onlyError, minDurationMs, sort, page, size);
    }

    @GetMapping("/executions/{executionId}/traces/{traceId}")
    public ExecutionTraceViews.ExecutionTraceDetailView getTrace(
            @PathVariable long executionId,
            @PathVariable String traceId
    ) {
        return executionTraceQueryService.queryTraceDetail(executionId, traceId);
    }
}
