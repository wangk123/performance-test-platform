package com.yr.perftest.platform.report;

import java.util.List;
import java.util.Map;

/**
 * Word/PDF 导出请求：既有图表截图与富文本，另加测试方法章节趋势图（前端离屏渲染的 PNG dataUrl）。
 */
public record ReportExportRequest(
        Map<String, String> chartImages,
        String editorContent,
        List<MethodChartImage> methodChartImages
) {
    public ReportExportRequest {
        chartImages = chartImages == null ? Map.of() : Map.copyOf(chartImages);
        editorContent = editorContent == null ? "" : editorContent;
        methodChartImages = methodChartImages == null ? List.of() : List.copyOf(methodChartImages);
    }

    /** 方法章节单张趋势图：executionId + 图类型（TPS/RT/CPU/MEM）+ PNG dataUrl（缺失/非法由导出侧降级占位）。 */
    public record MethodChartImage(long executionId, String kind, String dataUrl) {
        public String normalizedKind() {
            return kind == null ? "" : kind.trim().toUpperCase();
        }
    }
}
