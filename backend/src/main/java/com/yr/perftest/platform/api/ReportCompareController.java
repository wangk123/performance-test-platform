package com.yr.perftest.platform.api;

import com.yr.perftest.platform.report.ReportCompareService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 报告对比接口（模块 06 增强）。
 */
@RestController
public class ReportCompareController {
    private final ReportCompareService reportCompareService;

    public ReportCompareController(ReportCompareService reportCompareService) {
        this.reportCompareService = reportCompareService;
    }

    @PostMapping("/api/reports/compare")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportCompareService.ComparisonView compare(
            @RequestBody CompareRequest request,
            @RequestHeader(name = "X-User", defaultValue = "admin") String operatorUsername
    ) {
        return reportCompareService.compare(request.basePlanId(), request.targetPlanId(), operatorUsername);
    }

    @GetMapping("/api/reports/compare/{compareId}")
    public ReportCompareService.ComparisonView get(@PathVariable long compareId) {
        return reportCompareService.get(compareId);
    }

    public record CompareRequest(long basePlanId, long targetPlanId) {
    }
}
