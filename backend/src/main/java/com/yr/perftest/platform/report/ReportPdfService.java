package com.yr.perftest.platform.report;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 报告 PDF 导出（模块 06 增强）：由计划报告数据渲染 HTML 后经 openhtmltopdf 生成 PDF。
 */
@Service
public class ReportPdfService {
    private final ReportDataService reportDataService;

    public ReportPdfService(ReportDataService reportDataService) {
        this.reportDataService = reportDataService;
    }

    public byte[] generatePdf(long planId) {
        PlanReportResponse report = reportDataService.aggregateByPlan(planId);
        String html = renderHtml(report);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("pdf generation failed: " + exception.getMessage());
        }
    }

    String renderHtml(PlanReportResponse report) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"/>")
                .append("<style>body{font-family:sans-serif;margin:24px;}")
                .append("h1{font-size:20px;}h2{font-size:16px;margin-top:18px;}")
                .append("table{border-collapse:collapse;width:100%;font-size:12px;}")
                .append("th,td{border:1px solid #999;padding:4px 8px;text-align:left;}</style>")
                .append("</head><body>");
        html.append("<h1>性能测试报告 — ").append(escape(report.plan().planName())).append("</h1>");
        html.append("<p>planId=").append(report.plan().planId()).append("</p>");
        if (report.scenarios() == null) {
            html.append("<p>无场景数据</p></body></html>");
            return html.toString();
        }
        for (PlanReportResponse.ScenarioReport scenario : report.scenarios()) {
            html.append("<h2>场景：").append(escape(scenario.scenarioName())).append("</h2>");
            if (scenario.presets() == null) {
                continue;
            }
            for (PlanReportResponse.PresetReport preset : scenario.presets()) {
                html.append("<h3>配置组 ").append(preset.sortOrder()).append(" — ")
                        .append(escape(preset.label())).append("</h3>");
                if (preset.summary() != null) {
                    PlanReportResponse.AggregateSummary summary = preset.summary();
                    html.append("<p>采样 ").append(summary.samples())
                            .append("，TPS ").append(summary.throughput())
                            .append("，平均 ").append(summary.avgRt()).append("ms")
                            .append("，P95 ").append(summary.p95()).append("ms")
                            .append("，错误率 ").append(summary.errorRate()).append("</p>");
                }
                if (preset.aggregateRows() != null && !preset.aggregateRows().isEmpty()) {
                    html.append("<table><tr><th>接口</th><th>采样</th><th>平均(ms)</th><th>P95(ms)</th>")
                            .append("<th>P99(ms)</th><th>TPS</th><th>错误率</th></tr>");
                    for (PlanReportResponse.AggregateRow row : preset.aggregateRows()) {
                        html.append("<tr><td>").append(escape(row.label())).append("</td>")
                                .append("<td>").append(row.samples()).append("</td>")
                                .append("<td>").append(row.average()).append("</td>")
                                .append("<td>").append(row.p95()).append("</td>")
                                .append("<td>").append(row.p99()).append("</td>")
                                .append("<td>").append(row.throughput()).append("</td>")
                                .append("<td>").append(row.errorRate()).append("</td></tr>");
                    }
                    html.append("</table>");
                }
            }
        }
        html.append("</body></html>");
        return html.toString();
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
