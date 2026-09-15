package com.yr.perftest.platform.task.method;

import java.util.List;

/** 测试方法章节聚合视图：场景（含绑定脚本）+ 执行行（参数快照 + 聚合指标 + hidden）+ 截图元数据。 */
public record MethodSectionResponse(long planId, List<ScenarioMethodData> scenarios) {

    public record ScenarioMethodData(long scenarioId, String name, String testType, int sortOrder,
            Long scriptVersionId, String scriptName,
            List<ExecutionRow> executions, long hiddenCount, List<EvidenceImage> images) {
    }

    public record ExecutionRow(long executionId, String executionName,
            int threads, int rampUpSec, int durationSec, String status,
            Long samples, Double successRate, Double avgRtMs, Double p95Ms, Double tps,
            String startedAtText, boolean hidden) {
    }

    public record EvidenceImage(long id, Long executionId, String caption, int sortOrder,
            String contentType, long sizeBytes) {
    }
}
