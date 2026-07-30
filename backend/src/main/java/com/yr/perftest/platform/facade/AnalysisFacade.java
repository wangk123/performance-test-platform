package com.yr.perftest.platform.facade;

import com.yr.perftest.platform.analysis.AnalysisFact;
import com.yr.perftest.platform.analysis.AnalysisReport;
import com.yr.perftest.platform.analysis.AnalysisService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class AnalysisFacade {
    private final FacadeGuard guard;
    private final AnalysisService analysisService;

    public AnalysisFacade(FacadeGuard guard, AnalysisService analysisService) {
        this.guard = guard;
        this.analysisService = analysisService;
    }

    public AnalysisReport getExecutionAnalysis(long executionId, Instant from, Instant to, List<String> kinds, String metricSelector) {
        return guard.requirePrincipal(() -> analysisService.analyze(executionId, from, to, kinds, metricSelector));
    }

    public AnalysisFact compareExecutions(long baselineExecutionId, long candidateExecutionId) {
        return guard.requirePrincipal(() -> analysisService.compare(baselineExecutionId, candidateExecutionId));
    }
}
