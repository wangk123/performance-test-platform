package com.yr.perftest.platform.evidence.deep;

import com.yr.perftest.platform.evidence.CorrelationKey;
import com.yr.perftest.platform.facade.query.PageBudget;

/**
 * 深度证据探针（T11）：按配置执行某类深度证据采集，返回可用性与摘要。
 */
public interface DeepEvidenceProbe {
    DeepEvidenceKind kind();

    DeepProbeResult probe(CorrelationKey key, PageBudget budget);
}
