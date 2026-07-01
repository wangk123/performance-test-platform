package com.yr.perftest.platform.report;

import com.yr.perftest.platform.api.ApiError;
import com.yr.perftest.platform.execution.ExecutionValidationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 报告数据 API，以任务计划为维度聚合所有场景及其多轮执行数据。
 */
@RestController
@RequestMapping("/api/reports")
public class ReportDataController {

    private final ReportDataService reportDataService;

    public ReportDataController(ReportDataService reportDataService) {
        this.reportDataService = reportDataService;
    }

    @GetMapping("/plans/{planId}/data")
    public PlanReportResponse getPlanReport(@PathVariable long planId) {
        return reportDataService.aggregateByPlan(planId);
    }

    @ExceptionHandler(ExecutionValidationException.class)
    public ResponseEntity<ApiError> handleValidation(ExecutionValidationException ex) {
        return ResponseEntity.status(404).body(new ApiError("NOT_FOUND", ex.getMessage()));
    }
}
