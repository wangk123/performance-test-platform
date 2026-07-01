package com.yr.perftest.platform.report;

import java.util.Map;

/**
 * Word 导出请求，包含图表截图和用户编辑的富文本内容。
 */
public record ReportExportRequest(
        Map<String, String> chartImages,
        String editorContent
) {
    public ReportExportRequest {
        chartImages = chartImages == null ? Map.of() : Map.copyOf(chartImages);
        editorContent = editorContent == null ? "" : editorContent;
    }
}
