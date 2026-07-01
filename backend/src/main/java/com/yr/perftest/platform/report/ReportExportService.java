package com.yr.perftest.platform.report;

import com.yr.perftest.platform.execution.ExecutionValidationException;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Word 报告导出服务，使用 XDocReport 引擎填充 DOCX 模板。
 */
@Service
public class ReportExportService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final ReportDataService reportDataService;
    private final ReportTemplateService templateService;

    public ReportExportService(
            ReportDataService reportDataService,
            ReportTemplateService templateService
    ) {
        this.reportDataService = reportDataService;
        this.templateService = templateService;
    }

    public byte[] generateWord(long planId, ReportExportRequest request) {
        PlanReportResponse data = reportDataService.aggregateByPlan(planId);
        if (data == null || data.plan() == null) {
            throw new ExecutionValidationException("task plan does not exist");
        }

        Map<String, Object> context = buildContext(data, request);

        try (InputStream templateStream = templateService.loadDefaultTemplate()) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            var registry = fr.opensagres.xdocreport.document.registry.XDocReportRegistry.getRegistry();
            var report = registry.loadReport(
                    templateStream, "report",
                    fr.opensagres.xdocreport.template.TemplateEngineKind.Freemarker
            );

            var reportContext = report.createContext();
            context.forEach(reportContext::put);

            var metadata = report.createFieldsMetadata();
            if (request.chartImages() != null) {
                for (var entry : request.chartImages().entrySet()) {
                    if (entry.getValue() != null && entry.getValue().startsWith("data:image/")) {
                        byte[] bytes = decodeDataUri(entry.getValue());
                        if (bytes != null) {
                            metadata.addFieldAsImage(entry.getKey());
                            reportContext.put(entry.getKey(), bytes);
                        }
                    }
                }
            }

            report.process(reportContext, outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new ReportTemplateService.ReportTemplateException(
                    "Word 报告生成失败: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> buildContext(PlanReportResponse data, ReportExportRequest request) {
        Map<String, Object> ctx = new HashMap<>();
        var plan = data.plan();

        ctx.put("planName", nvl(plan.planName()));
        ctx.put("remark", nvl(plan.remark()));
        ctx.put("scenarioCount", data.scenarios().size());

        // Flatten scenarios for template
        List<Map<String, Object>> scenarioList = new java.util.ArrayList<>();
        for (var sc : data.scenarios()) {
            Map<String, Object> scMap = new HashMap<>();
            scMap.put("scenarioName", nvl(sc.scenarioName()));
            scMap.put("scriptName", nvl(sc.scriptName()));
            scMap.put("roundCount", sc.rounds().size());

            List<Map<String, Object>> roundList = new java.util.ArrayList<>();
            for (var r : sc.rounds()) {
                Map<String, Object> rm = new HashMap<>();
                rm.put("executionName", nvl(r.executionName()));
                rm.put("threads", r.threads());
                rm.put("duration", r.duration());
                rm.put("status", nvl(r.status()));

                if (r.summary() != null) {
                    rm.put("totalSamples", r.summary().samples());
                    rm.put("throughput", fm2(r.summary().throughput()));
                    rm.put("avgRt", r.summary().avgRt());
                    rm.put("p95", r.summary().p95());
                    rm.put("errorRate", fm2(r.summary().errorRate()));
                }

                if (r.aggregateRows() != null) {
                    rm.put("aggregateRows", r.aggregateRows().stream().map(ar -> {
                        Map<String, Object> rm2 = new HashMap<>();
                        rm2.put("label", ar.label());
                        rm2.put("samples", ar.samples());
                        rm2.put("average", ar.average());
                        rm2.put("p95", ar.p95());
                        rm2.put("errorRate", fm2(ar.errorRate()));
                        rm2.put("throughput", fm2(ar.throughput()));
                        return rm2;
                    }).collect(Collectors.toList()));
                }
                roundList.add(rm);
            }
            scMap.put("rounds", roundList);
            scenarioList.add(scMap);
        }
        ctx.put("scenarios", scenarioList);

        ctx.put("editorContent", stripHtml(nvl(request.editorContent())));
        ctx.put("generatedAt", DATE_FORMAT.format(java.time.Instant.now()));

        return ctx;
    }

    private byte[] decodeDataUri(String dataUri) {
        try {
            int idx = dataUri.indexOf(',');
            if (idx < 0) return null;
            return Base64.getDecoder().decode(dataUri.substring(idx + 1));
        } catch (Exception e) {
            return null;
        }
    }

    private String stripHtml(String html) {
        if (html == null || html.isBlank()) return "";
        return html.replaceAll("<[^>]+>", " ")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&amp;", "&")
                .replaceAll("\\s+", " ").trim();
    }

    private String nvl(String s) { return s == null ? "" : s; }

    private String fm2(double v) { return String.format("%.2f", v); }
}
