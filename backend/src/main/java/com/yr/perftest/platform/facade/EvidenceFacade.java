package com.yr.perftest.platform.facade;

import com.yr.perftest.platform.evidence.CorrelationKey;
import com.yr.perftest.platform.evidence.EvidenceService;
import com.yr.perftest.platform.evidence.EvidenceSummary;
import com.yr.perftest.platform.execution.ExecutionValidationException;
import com.yr.perftest.platform.facade.data.ExecutionSummary;
import com.yr.perftest.platform.facade.query.PageBudget;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 证据链 agent 面入口（T11）：按 executionId + 时间窗 + 目标实例 + 请求标签 + 可选 traceId
 * 下钻收集基础与深度证据摘要，全部经 Facade 主体校验。
 */
@Service
public class EvidenceFacade {
    private final FacadeGuard guard;
    private final DataFacade dataFacade;
    private final EvidenceService evidenceService;

    public EvidenceFacade(FacadeGuard guard, DataFacade dataFacade, EvidenceService evidenceService) {
        this.guard = guard;
        this.dataFacade = dataFacade;
        this.evidenceService = evidenceService;
    }

    public EvidenceCollectView collect(
            long executionId,
            Instant from,
            Instant to,
            List<String> targetInstances,
            String requestLabel,
            String traceId,
            List<String> kinds
    ) {
        return guard.requirePrincipal(() -> {
            ExecutionSummary summary;
            try {
                summary = dataFacade.getExecutionSummary(executionId);
            } catch (ExecutionValidationException exception) {
                if (exception.getMessage() != null && exception.getMessage().contains("execution does not exist")) {
                    // 执行已删除：证据失效语义（每源 present=false + DELETED），不得伪装存在
                    Instant now = Instant.now();
                    CorrelationKey deletedKey = new CorrelationKey(
                            executionId,
                            now.minus(Duration.ofHours(1)),
                            now,
                            targetInstances,
                            requestLabel,
                            traceId
                    );
                    List<EvidenceSummary> deletedSummaries = evidenceService.collect(
                            deletedKey,
                            PageBudget.defaults()
                    );
                    return new EvidenceCollectView(
                            EvidenceCollectView.SCHEMA_VERSION,
                            executionId,
                            deletedKey.from(),
                            deletedKey.to(),
                            deletedSummaries
                    );
                }
                throw exception;
            }
            Instant effectiveFrom = from != null
                    ? from
                    : summary.startedAt() != null ? summary.startedAt() : summary.createdAt();
            Instant effectiveTo = to != null
                    ? to
                    : summary.endedAt() != null ? summary.endedAt() : Instant.now();
            CorrelationKey key = new CorrelationKey(
                    executionId,
                    effectiveFrom,
                    effectiveTo,
                    targetInstances,
                    requestLabel,
                    traceId
            );
            List<EvidenceSummary> summaries = evidenceService.collect(key, PageBudget.defaults());
            if (kinds != null && !kinds.isEmpty()) {
                summaries = summaries.stream()
                        .filter(item -> kinds.contains(item.sourceType())
                                || (item.sourceType().startsWith("deep:")
                                && kinds.contains(item.sourceType().substring("deep:".length()))))
                        .toList();
            }
            return new EvidenceCollectView(
                    EvidenceCollectView.SCHEMA_VERSION,
                    executionId,
                    effectiveFrom,
                    effectiveTo,
                    summaries
            );
        });
    }

    public record EvidenceCollectView(
            String schemaVersion,
            long executionId,
            Instant from,
            Instant to,
            List<EvidenceSummary> summaries
    ) {
        public static final String SCHEMA_VERSION = "1";
    }
}
