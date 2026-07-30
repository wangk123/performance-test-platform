package com.yr.perftest.platform.agent.analysis;

import com.yr.perftest.platform.agent.AgentExceptionHandler;
import com.yr.perftest.platform.agent.contract.ApiResponse;
import com.yr.perftest.platform.analysis.AnalysisFact;
import com.yr.perftest.platform.analysis.AnalysisReport;
import com.yr.perftest.platform.facade.AnalysisFacade;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/agent/executions")
public class AgentAnalysisController {
    private final AnalysisFacade analysisFacade;

    public AgentAnalysisController(AnalysisFacade analysisFacade) {
        this.analysisFacade = analysisFacade;
    }

    @GetMapping("/{executionId}/analysis")
    public ApiResponse<AnalysisReport> analysis(
            @PathVariable long executionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) List<String> kinds,
            @RequestParam(required = false) String metric
    ) {
        AnalysisReport report = analysisFacade.getExecutionAnalysis(executionId, from, to, kinds, metric);
        return ApiResponse.success(UUID.randomUUID().toString(), AgentExceptionHandler.SCHEMA_VERSION, report);
    }

    @GetMapping("/compare")
    public ApiResponse<AnalysisFact> compare(
            @RequestParam long baselineId,
            @RequestParam long candidateId
    ) {
        AnalysisFact fact = analysisFacade.compareExecutions(baselineId, candidateId);
        return ApiResponse.success(UUID.randomUUID().toString(), AgentExceptionHandler.SCHEMA_VERSION, fact);
    }
}
