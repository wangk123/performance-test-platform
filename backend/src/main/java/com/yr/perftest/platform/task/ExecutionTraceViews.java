package com.yr.perftest.platform.task;

import com.yr.perftest.platform.evidence.deep.SkyWalkingTraceModels;

import java.util.List;

/**
 * 执行详情链路面板 REST 响应（Task 4）。字段名即前端 Task 8 消费契约，逐字对齐
 * brief Produces：列表 traces[].{traceId,time,service,entry,durationMs,error,spanCount}、
 * 分页 {available,missingReason,traces,total,page,size}、详情 trace.{traceId,entry,service,durationMs,error,spans}。
 */
public final class ExecutionTraceViews {
    private ExecutionTraceViews() {
    }

    /** 列表行；time 为 epoch millis。 */
    public record TraceListItemView(
            String traceId, long time, String service, String entry,
            long durationMs, boolean error, int spanCount) {
    }

    /** trace 列表页。不可用时 traces 为空数组、missingReason 给出原因；total 为过滤后总数。 */
    public record ExecutionTracesPageView(
            boolean available, String missingReason, List<TraceListItemView> traces,
            int total, int page, int size) {
    }

    /** 单条 trace 详情（span 树由前端按 parentSpanId 组装）。 */
    public record TraceDetailView(
            String traceId, String entry, String service, long durationMs,
            boolean error, List<SkyWalkingTraceModels.TraceSpanView> spans) {
    }

    /** trace 详情响应；不可用时 trace 为 null。 */
    public record ExecutionTraceDetailView(
            boolean available, String missingReason, TraceDetailView trace) {
    }

    static TraceListItemView listItem(SkyWalkingTraceModels.TraceBrief brief) {
        return new TraceListItemView(
                brief.traceId(), brief.startEpochMs(), brief.service(), brief.endpointName(),
                brief.durationMs(), brief.isError(), brief.spanCount());
    }
}
