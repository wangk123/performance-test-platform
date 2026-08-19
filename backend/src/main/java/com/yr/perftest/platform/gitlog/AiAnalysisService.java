package com.yr.perftest.platform.gitlog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.analysis.AnalysisService;
import com.yr.perftest.platform.execution.ExecutionValidationException;
import com.yr.perftest.platform.llm.LlmCallScene;
import com.yr.perftest.platform.llm.LlmChatMessage;
import com.yr.perftest.platform.llm.LlmGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 报告 AI 分析（模块 10）：单次调用底座（无 Tool Calling），保留输入事实、模型与
 * Prompt 版本；输出只作辅助建议，人工结论由用户确认。
 */
@Service
public class AiAnalysisService {
    public static final String PROMPT_VERSION = "v1";

    private final PersistentAiAnalysisJobRepository jobRepository;
    private final AnalysisService analysisService;
    private final LlmGateway llmGateway;
    private final ObjectMapper objectMapper;

    public AiAnalysisService(
            PersistentAiAnalysisJobRepository jobRepository,
            AnalysisService analysisService,
            LlmGateway llmGateway,
            ObjectMapper objectMapper
    ) {
        this.jobRepository = jobRepository;
        this.analysisService = analysisService;
        this.llmGateway = llmGateway;
        this.objectMapper = objectMapper;
    }

    /**
     * 刻意不加 @Transactional：模型调用失败会经内部事务传播标记 rollback-only，
     * 若与任务落库共用事务会导致 UnexpectedRollbackException；各仓库调用自有事务。
     */
    public JobView analyze(long executionId, long modelId, String requestedBy) {
        String facts = collectFacts(executionId);
        PersistentAiAnalysisJobRecord job = jobRepository.save(new PersistentAiAnalysisJobRecord(
                executionId,
                modelId,
                PROMPT_VERSION,
                facts,
                requestedBy
        ));
        try {
            String prompt = """
                    你是性能测试分析助手。下面是平台的确定性分析事实（带算法版本与证据定位）。
                    请只输出：1) 值得关注的观测点；2) 可执行的优化建议；3) 建议进一步采集的数据。
                    不得修改任何输入事实，不得在无证据时断言根因。输出使用中文。
                    
                    """ + facts;
            LlmGateway.InvokeResult result = llmGateway.invoke(new LlmGateway.InvokeRequest(
                    modelId,
                    LlmCallScene.REPORT_ANALYSIS,
                    List.of(
                            new LlmChatMessage("system", "只输出分析建议，保留证据引用，不做无依据的根因断言。"),
                            new LlmChatMessage("user", prompt)
                    ),
                    false,
                    requestedBy
            ));
            if (!result.success()) {
                job.markFailed("model call failed: " + result.errorMessage());
                return view(jobRepository.save(job));
            }
            job.markSuccess(result.content());
            return view(jobRepository.save(job));
        } catch (RuntimeException exception) {
            job.markFailed(exception.getMessage());
            return view(jobRepository.save(job));
        }
    }

    @Transactional(readOnly = true)
    public JobView get(long jobId) {
        return view(jobRepository.findById(jobId)
                .orElseThrow(() -> new ExecutionValidationException(
                        "ai analysis job " + jobId + " does not exist")));
    }

    private String collectFacts(long executionId) {
        try {
            return objectMapper.writeValueAsString(analysisService.analyze(executionId, null, null, null, null));
        } catch (Exception exception) {
            return "{\"factsUnavailable\":\"" + exception.getMessage() + "\"}";
        }
    }

    private JobView view(PersistentAiAnalysisJobRecord record) {
        return new JobView(
                record.getId(),
                record.getExecutionId(),
                record.getModelId(),
                record.getPromptVersion(),
                record.getStatus(),
                record.getResult(),
                record.getRequestedBy(),
                record.getCreatedAt(),
                record.getCompletedAt()
        );
    }

    public record JobView(
            long jobId,
            long executionId,
            long modelId,
            String promptVersion,
            String status,
            String result,
            String requestedBy,
            Instant createdAt,
            Instant completedAt
    ) {
    }
}
