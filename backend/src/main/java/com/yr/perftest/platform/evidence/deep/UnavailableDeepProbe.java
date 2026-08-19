package com.yr.perftest.platform.evidence.deep;

import com.yr.perftest.platform.evidence.CorrelationKey;
import com.yr.perftest.platform.facade.query.Availability;
import com.yr.perftest.platform.facade.query.PageBudget;

import java.util.Map;

/**
 * 外部系统适配器未选型前的占位探针（T11）：如实声明「源不可用 + 原因」，
 * 不伪造空成功。待 OTel/SkyWalking/日志平台/JFR 等外部系统选型后按
 * {@link DeepEvidenceProbe} 契约接入真实适配器。
 */
public class UnavailableDeepProbe implements DeepEvidenceProbe {
    private final DeepEvidenceKind kind;
    private final String pendingReason;

    public UnavailableDeepProbe(DeepEvidenceKind kind, String pendingReason) {
        this.kind = kind;
        this.pendingReason = pendingReason;
    }

    @Override
    public DeepEvidenceKind kind() {
        return kind;
    }

    @Override
    public DeepProbeResult probe(CorrelationKey key, PageBudget budget) {
        String sourceRef = "deep:" + kind.key() + "(" + pendingReason + ")";
        return new DeepProbeResult(
                new Availability(
                        false,
                        null,
                        null,
                        null,
                        false,
                        sourceRef,
                        Availability.MissingReason.SOURCE_UNAVAILABLE
                ),
                Map.of("pendingReason", pendingReason),
                sourceRef
        );
    }
}
