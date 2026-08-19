package com.yr.perftest.platform.evidence.deep;

import com.yr.perftest.platform.evidence.CorrelationKey;
import com.yr.perftest.platform.evidence.EvidenceSource;
import com.yr.perftest.platform.evidence.EvidenceSourceSupport;
import com.yr.perftest.platform.evidence.EvidenceSummary;
import com.yr.perftest.platform.facade.query.Availability;
import com.yr.perftest.platform.facade.query.PageBudget;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 深度证据源适配器（T11）：按关联键声明接入（executionId + 时间窗 + 可选 traceId），
 * 未配置/不可达/已删除时显式声明缺失，保留策略（retentionDays）随摘要暴露。
 */
public final class DeepEvidenceSource implements EvidenceSource {
    private final DeepEvidenceKind kind;
    private final DeepEvidenceProperties properties;
    private final DeepEvidenceProbe probe;

    public DeepEvidenceSource(
            DeepEvidenceKind kind,
            DeepEvidenceProperties properties,
            DeepEvidenceProbe probe
    ) {
        this.kind = kind;
        this.properties = properties;
        this.probe = probe;
    }

    @Override
    public boolean supports(CorrelationKey key) {
        if (!EvidenceSourceSupport.supports(key)) {
            return false;
        }
        // 请求按 traceId 下钻时，只包含声明支持 traceId 关联的源
        return key.traceId() == null || kind.traceIdCapable();
    }

    @Override
    public EvidenceSummary summarize(CorrelationKey key, PageBudget budget) {
        String sourceType = "deep:" + kind.key();
        DeepEvidenceProperties.KindConfig config = properties.forKind(kind);
        if (!config.isEnabled()) {
            return unavailable(key, sourceType, "disabled (approval required for high-impact kinds)");
        }
        try {
            DeepProbeResult result = probe.probe(key, budget);
            Map<String, Object> summary = new LinkedHashMap<>(result.summary());
            summary.put("kind", kind.key());
            summary.put("requiresApproval", kind.requiresApproval());
            summary.put("retentionDays", config.getRetentionDays());
            return new EvidenceSummary(
                    key,
                    sourceType,
                    result.availability(),
                    summary,
                    result.sourceRef(),
                    kind.sourceClock()
            );
        } catch (RuntimeException exception) {
            if (EvidenceSourceSupport.isDeletedExecution(exception)) {
                return EvidenceSourceSupport.deleted(key, sourceType, kind.sourceClock());
            }
            return unavailable(key, sourceType, "source error: " + safeMessage(exception));
        }
    }

    private EvidenceSummary unavailable(CorrelationKey key, String sourceType, String reason) {
        String sourceRef = "deep:" + kind.key() + "(" + reason + ")";
        Availability availability = new Availability(
                false,
                null,
                null,
                null,
                false,
                sourceRef,
                Availability.MissingReason.SOURCE_UNAVAILABLE
        );
        Map<String, Object> summary = Map.of(
                "kind", kind.key(),
                "requiresApproval", kind.requiresApproval(),
                "retentionDays", properties.forKind(kind).getRetentionDays()
        );
        return new EvidenceSummary(key, sourceType, availability, summary, sourceRef, kind.sourceClock());
    }

    private String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
