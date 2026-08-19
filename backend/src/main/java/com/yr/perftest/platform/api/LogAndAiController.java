package com.yr.perftest.platform.api;

import com.yr.perftest.platform.gitlog.AiAnalysisService;
import com.yr.perftest.platform.gitlog.LogArtifactService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 日志制品与 AI 分析接口（模块 10）。
 */
@RestController
public class LogAndAiController {
    private final LogArtifactService logArtifactService;
    private final AiAnalysisService aiAnalysisService;

    public LogAndAiController(
            LogArtifactService logArtifactService,
            AiAnalysisService aiAnalysisService
    ) {
        this.logArtifactService = logArtifactService;
        this.aiAnalysisService = aiAnalysisService;
    }

    @PostMapping("/api/executions/{executionId}/logs")
    @ResponseStatus(HttpStatus.CREATED)
    public LogArtifactService.ArtifactView uploadLog(
            @PathVariable long executionId,
            @RequestParam("file") MultipartFile file,
            @RequestHeader(name = "X-User", defaultValue = "admin") String operatorUsername
    ) {
        return logArtifactService.upload(executionId, file, operatorUsername);
    }

    @GetMapping("/api/executions/{executionId}/logs")
    public List<LogArtifactService.ArtifactView> listLogs(@PathVariable long executionId) {
        return logArtifactService.list(executionId);
    }

    @GetMapping("/api/executions/{executionId}/logs/search")
    public List<LogArtifactService.SearchHit> searchLogs(
            @PathVariable long executionId,
            @RequestParam String q,
            @RequestParam(defaultValue = "50") int maxHits
    ) {
        return logArtifactService.search(executionId, q, maxHits);
    }

    @PostMapping("/api/executions/{executionId}/ai-analysis")
    @ResponseStatus(HttpStatus.CREATED)
    public AiAnalysisService.JobView analyze(
            @PathVariable long executionId,
            @RequestBody AnalyzeRequest request,
            @RequestHeader(name = "X-User", defaultValue = "admin") String operatorUsername
    ) {
        return aiAnalysisService.analyze(executionId, request.modelId(), operatorUsername);
    }

    @GetMapping("/api/ai-analysis/{jobId}")
    public AiAnalysisService.JobView getJob(@PathVariable long jobId) {
        return aiAnalysisService.get(jobId);
    }

    public record AnalyzeRequest(long modelId) {
    }
}
