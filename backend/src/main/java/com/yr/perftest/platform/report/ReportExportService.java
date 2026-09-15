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
    private final MethodExportSectionService methodExportSectionService;

    public ReportExportService(
            ReportDataService reportDataService,
            ReportTemplateService templateService,
            MethodExportSectionService methodExportSectionService
    ) {
        this.reportDataService = reportDataService;
        this.templateService = templateService;
        this.methodExportSectionService = methodExportSectionService;
    }

    public byte[] generateWord(long planId, ReportExportRequest request) {
        PlanReportResponse data = reportDataService.aggregateByPlan(planId);
        if (data == null || data.plan() == null) {
            throw new ExecutionValidationException("task plan does not exist");
        }

        Map<String, Object> context = buildContext(data, request);
        MethodExportSectionService.MethodSectionView methodView =
                methodExportSectionService.buildSection(planId, request.methodChartImages());
        String sectionXml = methodExportSectionService.buildWordSectionXml(methodView);
        boolean hasMethodSection = !methodView.isEmpty();

        try (InputStream templateStream = templateService.loadDefaultTemplate()) {
            InputStream template = hasMethodSection
                    ? methodExportSectionService.decorateWordTemplate(templateStream, sectionXml, methodView)
                    : templateStream;

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            var registry = fr.opensagres.xdocreport.document.registry.XDocReportRegistry.getRegistry();
            // 唯一 id + 用后注销：registry 同 id 重复注册会抛 XDocReportException（同 JVM 内连续导出场景）
            String templateId = "report-" + java.util.UUID.randomUUID();
            var report = registry.loadReport(
                    template, templateId,
                    fr.opensagres.xdocreport.template.TemplateEngineKind.Freemarker
            );
            try {
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
            } finally {
                registry.unregisterReport(templateId);
            }
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
            scMap.put("presetCount", sc.presets().size());

            List<Map<String, Object>> presetList = new java.util.ArrayList<>();
            for (var preset : sc.presets()) {
                Map<String, Object> presetMap = new HashMap<>();
                presetMap.put("label", nvl(preset.label()));
                presetMap.put("threadGroupCount", preset.threadGroupCount());

                List<Map<String, Object>> rowList = new java.util.ArrayList<>();
                for (var row : preset.rows()) {
                    Map<String, Object> rowMap = new HashMap<>();
                    rowMap.put("stepName", nvl(row.stepName()));
                    rowMap.put("threads", row.threads());
                    rowMap.put("rampUp", row.rampUp());
                    rowMap.put("duration", row.duration());
                    if (row.summary() != null) {
                        rowMap.put("totalSamples", row.summary().samples());
                        rowMap.put("throughput", fm2(row.summary().throughput()));
                        rowMap.put("avgRt", row.summary().avgRt());
                        rowMap.put("errorRate", fm2(row.summary().errorRate()));
                    }
                    rowList.add(rowMap);
                }
                presetMap.put("rows", rowList);

                if (preset.summary() != null) {
                    presetMap.put("totalSamples", preset.summary().samples());
                    presetMap.put("throughput", fm2(preset.summary().throughput()));
                    presetMap.put("avgRt", preset.summary().avgRt());
                    presetMap.put("errorRate", fm2(preset.summary().errorRate()));
                }

                if (preset.aggregateRows() != null) {
                    presetMap.put("aggregateRows", preset.aggregateRows().stream().map(ar -> {
                        Map<String, Object> aggregateMap = new HashMap<>();
                        aggregateMap.put("label", ar.label());
                        aggregateMap.put("samples", ar.samples());
                        aggregateMap.put("average", ar.average());
                        aggregateMap.put("p95", ar.p95());
                        aggregateMap.put("errorRate", fm2(ar.errorRate()));
                        aggregateMap.put("throughput", fm2(ar.throughput()));
                        return aggregateMap;
                    }).collect(Collectors.toList()));
                }
                presetList.add(presetMap);
            }
            scMap.put("presets", presetList);
            scenarioList.add(scMap);
        }
        ctx.put("scenarios", scenarioList);

        ctx.put("editorContent", stripHtml(nvl(request.editorContent())));
        ctx.put("generatedAt", DATE_FORMAT.format(java.time.Instant.now()));

        // 旧模板（单场景视图）遗留占位键兜底：Freemarker 对缺失变量抛 InvalidReferenceException，
        // 这些键不再由聚合数据填充（新版数据走 methodScenarios 循环）。
        ctx.put("scenarioName", "");
        ctx.put("startedAt", "");
        ctx.put("status", "");
        ctx.put("totalSamples", "");
        ctx.put("throughput", "");
        ctx.put("avgRt", "");
        ctx.put("p95", "");
        ctx.put("errorRate", "");
        ctx.put("aggregateRows", List.of());

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
