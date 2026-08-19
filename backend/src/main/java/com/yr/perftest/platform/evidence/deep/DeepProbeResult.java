package com.yr.perftest.platform.evidence.deep;

import com.yr.perftest.platform.facade.query.Availability;

import java.util.Map;

/**
 * 深度证据探针结果：不可用时 availability.present=false 且 missingReason 显式声明，
 * 不允许伪造空数据成功。
 */
public record DeepProbeResult(
        Availability availability,
        Map<String, Object> summary,
        String sourceRef
) {
    public DeepProbeResult {
        summary = summary == null ? Map.of() : Map.copyOf(summary);
    }
}
