package com.yr.perftest.platform.evidence.deep;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * SkyWalking OAP GraphQL 响应解析（纯函数）。
 * 日期格式为 OAP 的 {@code yyyy-MM-dd HHmmss}（本地时区）。
 */
public final class SkyWalkingTraceModels {
    static final DateTimeFormatter SW_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HHmmss");

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private SkyWalkingTraceModels() {
    }

    /** {@code data.queryBasicTraces} → 当页 {@link TraceBrief} 列表 + 命中总数（total，供分页）。 */
    public static TraceBriefsPage parseBasicTraces(String body) {
        JsonNode result = dataField(body, "queryBasicTraces");
        List<TraceBrief> traces = new ArrayList<>();
        for (JsonNode trace : result.path("traces")) {
            traces.add(new TraceBrief(
                    trace.path("traceIds").path(0).asText(""),
                    trace.path("service").asText(""),
                    trace.path("endpointNames").path(0).asText(""),
                    parseEpochMillis(trace.path("start").asText(null)),
                    trace.path("duration").asLong(0),
                    trace.path("isError").asBoolean(false),
                    trace.path("spanCount").asInt(0)));
        }
        return new TraceBriefsPage(traces, result.path("total").asInt(traces.size()));
    }

    /** {@code data.queryTrace.segments[].spans[]} → 扁平 {@link TraceSpanView} 列表 + 聚合 brief。 */
    public static TraceDetail parseTrace(String body) {
        JsonNode segments = dataField(body, "queryTrace").path("segments");
        List<TraceSpanView> spans = new ArrayList<>();
        String service = "";
        String endpointName = "";
        long minStart = Long.MAX_VALUE;
        long maxEnd = Long.MIN_VALUE;
        boolean anyError = false;
        for (JsonNode segment : segments) {
            if (service.isEmpty()) {
                service = segment.path("service").asText("");
            }
            if (endpointName.isEmpty()) {
                endpointName = segment.path("endpointNames").path(0).asText("");
            }
            for (JsonNode span : segment.path("spans")) {
                long startTime = span.path("startTime").asLong(0);
                long endTime = span.path("endTime").asLong(0);
                boolean error = span.path("isError").asBoolean(false);
                spans.add(new TraceSpanView(
                        segment.path("segmentId").asText(""),
                        span.path("spanId").asInt(0),
                        span.path("parentSpanId").asInt(-1),
                        segment.path("service").asText(""),
                        span.path("endpointName").asText(""),
                        startTime,
                        endTime,
                        error,
                        span.path("errorMessage").asText("")));
                minStart = Math.min(minStart, startTime);
                maxEnd = Math.max(maxEnd, endTime);
                anyError = anyError || error;
            }
        }
        TraceBrief brief = new TraceBrief(
                "",
                service,
                endpointName,
                spans.isEmpty() ? 0L : minStart,
                spans.isEmpty() ? 0L : Math.max(0L, maxEnd - minStart),
                anyError,
                spans.size());
        return new TraceDetail(brief, spans);
    }

    private static JsonNode dataField(String body, String field) {
        JsonNode root;
        try {
            root = MAPPER.readTree(body);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new SkyWalkingQueryException("skywalking response is not valid json: " + exception.getMessage(), exception);
        }
        JsonNode node = root == null ? null : root.path("data").get(field);
        if (node == null || node.isNull()) {
            throw new SkyWalkingQueryException("skywalking response missing data." + field);
        }
        return node;
    }

    /** {@code yyyy-MM-dd HHmmss}（本地时区）→ epochMs；解析失败取 0。 */
    static long parseEpochMillis(String start) {
        if (start == null || start.isBlank()) {
            return 0L;
        }
        try {
            return LocalDateTime.parse(start, SW_TIME).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (RuntimeException exception) {
            return 0L;
        }
    }

    /** 查询条件命中的一条 trace 概要（queryBasicTraces 行）。 */
    public record TraceBrief(String traceId, String service, String endpointName, long startEpochMs,
                             long durationMs, boolean isError, int spanCount) {
    }

    /** queryBasicTraces 分页结果：当页 traces + 命中总数 total（缺省回退当页条数）。 */
    public record TraceBriefsPage(List<TraceBrief> traces, int total) {
    }

    /** 扁平化后的 span 视图（跨 segment 拉平，保留 parentSpanId 供组装树）。 */
    public record TraceSpanView(String segmentId, int spanId, int parentSpanId, String service,
                                String endpointName, long startTimeMillis, long endTimeMillis,
                                boolean isError, String errorMessage) {
    }

    /** 单条 trace 的完整详情。 */
    public record TraceDetail(TraceBrief brief, List<TraceSpanView> spans) {
    }
}
