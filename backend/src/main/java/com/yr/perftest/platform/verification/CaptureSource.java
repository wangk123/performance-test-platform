package com.yr.perftest.platform.verification;

/**
 * 取证快照的单源摘要：只存可用性 + 条数 + 来源定位，不内联原始数据（沿用 T6 证据摘要原则）。
 */
public record CaptureSource(
        String sourceType,
        boolean present,
        boolean truncated,
        int count,
        String sourceRef,
        String missingReason
) {
}
