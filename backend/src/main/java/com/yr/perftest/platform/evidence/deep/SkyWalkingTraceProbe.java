package com.yr.perftest.platform.evidence.deep;

import com.yr.perftest.platform.evidence.CorrelationKey;
import com.yr.perftest.platform.facade.query.Availability;
import com.yr.perftest.platform.facade.query.PageBudget;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * trace 深度证据探针（T3）：基于 SkyWalking OAP GraphQL。
 * 有 traceId 时下钻单条 trace 的 span 树，否则按时间窗列出 trace 概要；
 * 摘要有界（span 数受 PageBudget 截断），不可达/无数据显式声明，不伪造空成功。
 */
public class SkyWalkingTraceProbe implements DeepEvidenceProbe {

    private static final String SOURCE_REF = "skywalking:trace";
    private static final int LIST_PAGE_SIZE_CAP = 100;

    private final SkyWalkingGraphqlClient client;

    public SkyWalkingTraceProbe(SkyWalkingGraphqlClient client) {
        this.client = client;
    }

    @Override
    public DeepEvidenceKind kind() {
        return DeepEvidenceKind.TRACE;
    }

    @Override
    public DeepProbeResult probe(CorrelationKey key, PageBudget budget) {
        try {
            if (key.traceId() != null) {
                return probeTrace(key.traceId(), budget);
            }
            return probeList(key, budget);
        } catch (RuntimeException exception) {
            String message = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            return new DeepProbeResult(
                    new Availability(false, null, null, null, false,
                            SOURCE_REF, Availability.MissingReason.SOURCE_UNAVAILABLE),
                    Map.of("error", message),
                    SOURCE_REF
            );
        }
    }

    private DeepProbeResult probeTrace(String traceId, PageBudget budget) {
        SkyWalkingTraceModels.TraceDetail detail = client.queryTrace(traceId);
        List<SkyWalkingTraceModels.TraceSpanView> allSpans = detail.spans();
        if (allSpans.isEmpty()) {
            return unavailable(Availability.MissingReason.NO_DATA, Map.of("spans", 0));
        }
        boolean truncated = allSpans.size() > budget.maxItems();
        List<SkyWalkingTraceModels.TraceSpanView> spans = truncated
                ? allSpans.subList(0, budget.maxItems())
                : allSpans;
        long minStart = spans.stream().mapToLong(SkyWalkingTraceModels.TraceSpanView::startTimeMillis).min().orElse(0);
        long maxEnd = spans.stream().mapToLong(SkyWalkingTraceModels.TraceSpanView::endTimeMillis).max().orElse(0);
        long errorSpans = spans.stream().filter(SkyWalkingTraceModels.TraceSpanView::isError).count();
        Set<String> services = new LinkedHashSet<>();
        for (SkyWalkingTraceModels.TraceSpanView span : spans) {
            if (span.service() != null && !span.service().isEmpty()) {
                services.add(span.service());
            }
        }
        String sourceRef = SOURCE_REF + "?traceId=" + traceId + "#" + minStart + "-" + maxEnd;
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("spans", spans.size());
        summary.put("services", services.size());
        summary.put("errorSpans", errorSpans);
        summary.put("truncated", truncated);
        return new DeepProbeResult(
                new Availability(
                        true,
                        Instant.ofEpochMilli(minStart),
                        Instant.ofEpochMilli(maxEnd),
                        "span",
                        truncated,
                        sourceRef,
                        null
                ),
                summary,
                sourceRef
        );
    }

    private DeepProbeResult probeList(CorrelationKey key, PageBudget budget) {
        int pageSize = Math.min(budget.maxItems(), LIST_PAGE_SIZE_CAP);
        SkyWalkingTraceModels.TraceBriefsPage page = client.queryBasicTraces(
                null, null, key.from(), key.to(), null, null, true, 1, pageSize);
        List<SkyWalkingTraceModels.TraceBrief> briefs = page.traces();
        if (briefs.isEmpty()) {
            return unavailable(Availability.MissingReason.NO_DATA, Map.of("traceCount", 0));
        }
        long fromMs = Long.MAX_VALUE;
        long toMs = Long.MIN_VALUE;
        long errorCount = 0;
        long slowestMs = 0;
        for (SkyWalkingTraceModels.TraceBrief brief : briefs) {
            fromMs = Math.min(fromMs, brief.startEpochMs());
            toMs = Math.max(toMs, brief.startEpochMs() + brief.durationMs());
            if (brief.isError()) {
                errorCount++;
            }
            slowestMs = Math.max(slowestMs, brief.durationMs());
        }
        String sourceRef = SOURCE_REF + "?from=" + fromMs + "&to=" + toMs
                + "#" + briefs.get(0).traceId() + "-" + briefs.get(briefs.size() - 1).traceId();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("traceCount", briefs.size());
        summary.put("total", page.total());
        summary.put("errorCount", errorCount);
        summary.put("slowestMs", slowestMs);
        return new DeepProbeResult(
                new Availability(
                        true,
                        Instant.ofEpochMilli(fromMs),
                        Instant.ofEpochMilli(toMs),
                        "trace",
                        briefs.size() >= pageSize,
                        sourceRef,
                        null
                ),
                summary,
                sourceRef
        );
    }

    private DeepProbeResult unavailable(Availability.MissingReason reason, Map<String, Object> summary) {
        return new DeepProbeResult(
                new Availability(false, null, null, null, false, SOURCE_REF, reason),
                summary,
                SOURCE_REF
        );
    }
}
