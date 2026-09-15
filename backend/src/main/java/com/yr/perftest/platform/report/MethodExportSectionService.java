package com.yr.perftest.platform.report;

import com.yr.perftest.platform.task.TaskPlan;
import com.yr.perftest.platform.task.method.MethodSectionResponse;
import com.yr.perftest.platform.task.method.MethodSectionService;
import com.yr.perftest.platform.task.method.PersistentPlanEvidenceImageRecord;
import com.yr.perftest.platform.task.method.PlanEvidenceImageRepository;
import com.yr.perftest.platform.task.plandoc.PlanDocumentService;
import com.yr.perftest.platform.task.plandoc.PlanMarkdownSupport;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 测试方法章节导出装配：Task 4 聚合（hidden 行排除）+ 前端离屏趋势图 PNG + 补充截图按 storedPath 读盘。
 * 产出 Word（静态 OOXML 片段 + 图片资源注入模板流）与 PDF（HTML + data URI）两种视图；
 * 仅当计划文档含「测试方法」章节（spec §8 章节作用域）才追加导出节；
 * 任一图片缺失均降级为文字占位，不阻断导出。
 */
@Service
public class MethodExportSectionService {

    private static final List<String> TREND_KINDS = List.of("TPS", "RT", "CPU", "MEM");
    private static final Map<String, String> KIND_TITLES = Map.of(
            "TPS", "TPS 趋势", "RT", "响应时间趋势", "CPU", "CPU 使用率趋势", "MEM", "内存使用率趋势");
    private static final String SECTION_TITLE = "测试方法执行结果";
    private static final float TREND_IMAGE_WIDTH = 420f;
    private static final float SHOT_IMAGE_WIDTH = 420f;
    private static final Map<String, String> CONTENT_TYPE_EXTENSIONS = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/webp", "webp");

    private final MethodSectionService methodSectionService;
    private final PlanEvidenceImageRepository imageRepository;
    private final PlanDocumentService planDocumentService;

    public MethodExportSectionService(
            MethodSectionService methodSectionService,
            PlanEvidenceImageRepository imageRepository,
            PlanDocumentService planDocumentService
    ) {
        this.methodSectionService = methodSectionService;
        this.imageRepository = imageRepository;
        this.planDocumentService = planDocumentService;
    }

    /** 图片资产：字节 + 上传侧声明的 contentType（决定 Word 扩展名/Content_Types 声明与 PDF data URI mime）。 */
    public record ImageAsset(byte[] content, String contentType) {
    }

    /** 章节视图：场景 → 执行（结果行 + 趋势图）→ 补充截图；imageAssets 供 Word 图片注入/PDF data URI 共用。 */
    public record MethodSectionView(List<ScenarioView> scenarios, Map<String, ImageAsset> imageAssets) {
        public boolean isEmpty() {
            return scenarios.isEmpty();
        }
    }

    public record ScenarioView(String name, List<ExecutionView> executions, List<ImageView> evidence) {
    }

    public record ExecutionView(String title, Map<String, String> row, List<ImageView> images) {
    }

    /** field=null 表示图片缺失，导出侧输出文字占位。 */
    public record ImageView(String field, String caption) {
    }

    public MethodSectionView buildSection(long planId, List<ReportExportRequest.MethodChartImage> charts) {
        // spec §8：仅文档含「测试方法」章节的计划追加导出节（内置模板存量计划不含该章节）
        if (!hasTestMethodSection(planId)) {
            return new MethodSectionView(List.of(), Map.of());
        }
        MethodSectionResponse method = methodSectionService.getPlanMethod(planId);
        if (method.scenarios().isEmpty()) {
            return new MethodSectionView(List.of(), Map.of());
        }
        Map<String, String> dataUrlByKey = new LinkedHashMap<>();
        for (ReportExportRequest.MethodChartImage chart : charts) {
            if (chart.dataUrl() != null && !chart.dataUrl().isBlank()) {
                dataUrlByKey.put(chart.executionId() + "|" + chart.normalizedKind(), chart.dataUrl());
            }
        }
        List<Long> scenarioIds = method.scenarios().stream()
                .map(MethodSectionResponse.ScenarioMethodData::scenarioId).toList();
        Map<Long, List<PersistentPlanEvidenceImageRecord>> shotsByScenario = imageRepository
                .findByScenarioIdInOrderBySortOrderAscIdAsc(scenarioIds).stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        PersistentPlanEvidenceImageRecord::getScenarioId, LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));

        List<ScenarioView> scenarios = new ArrayList<>();
        Map<String, ImageAsset> imageAssets = new LinkedHashMap<>();
        for (MethodSectionResponse.ScenarioMethodData scenario : method.scenarios()) {
            List<MethodSectionResponse.ExecutionRow> visible = scenario.executions().stream()
                    .filter(row -> !row.hidden()).toList();

            List<ExecutionView> executions = new ArrayList<>();
            for (MethodSectionResponse.ExecutionRow row : visible) {
                executions.add(new ExecutionView(
                        "#" + row.executionId() + " · " + nvl(row.executionName()),
                        toRowMap(row),
                        trendImages(row.executionId(), dataUrlByKey, imageAssets)));
            }

            List<ImageView> evidence = new ArrayList<>();
            for (PersistentPlanEvidenceImageRecord shot : shotsByScenario
                    .getOrDefault(scenario.scenarioId(), List.of())) {
                evidence.add(evidenceImage(shot, imageAssets));
            }
            scenarios.add(new ScenarioView(nvl(scenario.name()), executions, evidence));
        }
        return new MethodSectionView(scenarios, imageAssets);
    }

    private boolean hasTestMethodSection(long planId) {
        TaskPlan plan = planDocumentService.getDocument(planId);
        String body = plan == null ? null : plan.body();
        return body != null && PlanMarkdownSupport.testMethodSectionBounds(body) != null;
    }

    private List<ImageView> trendImages(long executionId, Map<String, String> dataUrlByKey,
                                        Map<String, ImageAsset> imageAssets) {
        List<ImageView> images = new ArrayList<>();
        for (String kind : TREND_KINDS) {
            String field = "methodImg_" + executionId + "_" + kind;
            String dataUrl = dataUrlByKey.get(executionId + "|" + kind);
            byte[] png = dataUrl == null ? null : decodeDataUri(dataUrl);
            if (png == null) {
                images.add(new ImageView(null, "（" + KIND_TITLES.get(kind) + "缺失）"));
            } else {
                imageAssets.put(field, new ImageAsset(png, "image/png"));
                images.add(new ImageView(field, KIND_TITLES.get(kind)));
            }
        }
        return images;
    }

    private ImageView evidenceImage(PersistentPlanEvidenceImageRecord shot, Map<String, ImageAsset> imageAssets) {
        String field = "methodShot_" + shot.getId();
        String caption = nvl(shot.getCaption());
        String contentType = nvl(shot.getContentType());
        try {
            byte[] content = Files.readAllBytes(Path.of(shot.getStoredPath()));
            if (content.length > 0 && CONTENT_TYPE_EXTENSIONS.containsKey(contentType)) {
                imageAssets.put(field, new ImageAsset(content, contentType));
                return new ImageView(field, caption.isEmpty() ? "补充截图" : caption);
            }
        } catch (IOException ignored) {
            // 磁盘文件缺失：降级占位
        }
        return new ImageView(null, caption.isEmpty() ? "（补充截图文件缺失）" : "（补充截图缺失：" + caption + "）");
    }

    private Map<String, String> toRowMap(MethodSectionResponse.ExecutionRow row) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("executionName", nvl(row.executionName()));
        map.put("threads", String.valueOf(row.threads()));
        map.put("rampUpSec", String.valueOf(row.rampUpSec()));
        map.put("durationSec", String.valueOf(row.durationSec()));
        map.put("status", nvl(row.status()));
        map.put("samples", row.samples() == null ? "—" : String.valueOf(row.samples()));
        map.put("successRate", row.successRate() == null ? "—" : row.successRate() + "%");
        map.put("avgRtMs", row.avgRtMs() == null ? "—" : row.avgRtMs() + "ms");
        map.put("p95Ms", row.p95Ms() == null ? "—" : row.p95Ms() + "ms");
        map.put("tps", row.tps() == null ? "—" : String.format("%.2f", row.tps()));
        map.put("startedAtText", nvl(row.startedAtText()));
        return map;
    }

    /** Word 章节片段：全静态展开（场景/执行/行数据 Java 侧内联转义），图片占位在段落层。
     * 整体包 [#noparse]：用户数据中的 ${...}/#{...}/[#...] 形态按字面输出，防 Freemarker 注入。 */
    public String buildWordSectionXml(MethodSectionView view) {
        StringBuilder xml = new StringBuilder();
        xml.append("[#noparse]");
        xml.append(paragraph(SECTION_TITLE, true, 30, true));
        for (ScenarioView scenario : view.scenarios()) {
            xml.append("<w:p><w:r><w:rPr><w:b/></w:rPr><w:t xml:space=\"preserve\">")
                    .append(escapeXml(scenario.name())).append("</w:t></w:r></w:p>");
            xml.append(wordExecutionTable(scenario));
            for (ExecutionView execution : scenario.executions()) {
                xml.append("<w:p><w:r><w:rPr><w:b/></w:rPr><w:t xml:space=\"preserve\">")
                        .append(escapeXml(execution.title())).append("</w:t></w:r></w:p>");
                for (ImageView image : execution.images()) {
                    xml.append(wordImage(image));
                }
            }
            if (!scenario.evidence().isEmpty()) {
                xml.append("<w:p><w:r><w:rPr><w:b/></w:rPr><w:t xml:space=\"preserve\">补充截图</w:t></w:r></w:p>");
                for (ImageView shot : scenario.evidence()) {
                    xml.append(wordImage(shot));
                }
            }
        }
        xml.append("[/#noparse]");
        return xml.toString();
    }

    private String wordExecutionTable(ScenarioView scenario) {
        String[] headers = {"执行", "并发", "Ramp-Up(s)", "时长(s)", "状态", "采样数", "成功率", "AvgRT", "P95", "TPS", "开始时间"};
        String[] keys = {"executionName", "threads", "rampUpSec", "durationSec", "status", "samples",
                "successRate", "avgRtMs", "p95Ms", "tps", "startedAtText"};
        StringBuilder xml = new StringBuilder("<w:tbl><w:tblPr><w:tblBorders>");
        for (String edge : List.of("top", "left", "bottom", "right", "insideH", "insideV")) {
            xml.append("<w:").append(edge).append(" w:val=\"single\" w:sz=\"4\" w:color=\"999999\"/>");
        }
        xml.append("</w:tblBorders></w:tblPr><w:tblGrid>");
        for (int i = 0; i < headers.length; i++) {
            xml.append("<w:gridCol w:w=\"1440\"/>");
        }
        xml.append("</w:tblGrid><w:tr>");
        for (String header : headers) {
            xml.append("<w:tc><w:p><w:r><w:rPr><w:b/></w:rPr><w:t xml:space=\"preserve\">")
                    .append(header).append("</w:t></w:r></w:p></w:tc>");
        }
        xml.append("</w:tr>");
        for (ExecutionView execution : scenario.executions()) {
            xml.append("<w:tr>");
            for (String key : keys) {
                xml.append("<w:tc><w:p><w:r><w:t xml:space=\"preserve\">")
                        .append(escapeXml(execution.row().get(key))).append("</w:t></w:r></w:p></w:tc>");
            }
            xml.append("</w:tr>");
        }
        xml.append("</w:tbl>");
        return xml.toString();
    }

    private String wordImage(ImageView image) {
        StringBuilder xml = new StringBuilder();
        if (image.field() == null) {
            xml.append("<w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr><w:r><w:t xml:space=\"preserve\">")
                    .append(escapeXml(image.caption())).append("</w:t></w:r></w:p>");
            return xml.toString();
        }
        // 占位符置于段落层（run 之外），patchImagePlaceholders 阶段重建为完整 <w:r><w:drawing/></w:r>，
        // 避免 <w:drawing> 进入 <w:t> 内部（ECMA-376 中 CT_Text 仅允许文本）。
        xml.append("<w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr>[[DRAWING:").append(image.field())
                .append("]]</w:p>");
        xml.append("<w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr><w:r><w:rPr><w:color w:val=\"808080\"/>")
                .append("<w:sz w:val=\"18\"/></w:rPr><w:t xml:space=\"preserve\">")
                .append(escapeXml(image.caption())).append("</w:t></w:r></w:p>");
        return xml.toString();
    }

    /**
     * 模板流装饰（模板资源文件本身不动）：
     * 1) word/document.xml —— </w:body> 前插入章节 XML，占位符 [[DRAWING:field]] 重建为 <w:r><w:drawing/></w:r>；
     * 2) word/media/ —— 按 contentType 写入图片；3) word/_rels/document.xml.rels —— 追加 image 关系；
     * 4) [Content_Types].xml —— 按实际用到的图片类型条件声明 Default。图片为静态资源，XDocReport 只需原样保留。
     */
    public InputStream decorateWordTemplate(InputStream templateStream, String sectionXml,
                                            MethodSectionView view) throws IOException {
        Map<String, ImageAsset> imageAssets = view.imageAssets();
        String patchedSection = patchImagePlaceholders(sectionXml, imageAssets);

        ByteArrayOutputStream decorated = new ByteArrayOutputStream();
        try (ZipInputStream zipIn = new ZipInputStream(templateStream);
             ZipOutputStream zipOut = new ZipOutputStream(decorated)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zipIn.getNextEntry()) != null) {
                String name = entry.getName();
                zipOut.putNextEntry(new ZipEntry(name));
                if ("word/document.xml".equals(name)) {
                    String document = new String(zipIn.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    int index = document.lastIndexOf("</w:body>");
                    if (index < 0) {
                        throw new IOException("word template document.xml is malformed");
                    }
                    String patched = document.substring(0, index) + patchedSection + document.substring(index);
                    zipOut.write(patched.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                } else if ("word/_rels/document.xml.rels".equals(name)) {
                    String rels = new String(zipIn.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    zipOut.write(appendImageRelationships(rels, imageAssets).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                } else if ("[Content_Types].xml".equals(name)) {
                    String types = new String(zipIn.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    zipOut.write(appendContentTypes(types, imageAssets).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                } else {
                    int read;
                    while ((read = zipIn.read(buffer)) > 0) {
                        zipOut.write(buffer, 0, read);
                    }
                }
                zipOut.closeEntry();
            }
            for (Map.Entry<String, ImageAsset> image : imageAssets.entrySet()) {
                zipOut.putNextEntry(new ZipEntry("word/media/" + mediaFileName(image.getKey(), image.getValue())));
                zipOut.write(image.getValue().content());
                zipOut.closeEntry();
            }
        }
        return new java.io.ByteArrayInputStream(decorated.toByteArray());
    }

    private String extensionOf(String contentType) {
        return CONTENT_TYPE_EXTENSIONS.getOrDefault(contentType, "png");
    }

    private String mediaFileName(String field, ImageAsset asset) {
        return field + "." + extensionOf(asset.contentType());
    }

    /** [[DRAWING:field]] 占位（段落层）→ 完整 <w:r><w:drawing/></w:r>；引用 media/{field}.{ext} 与 rIdMethodX 关系。 */
    private String patchImagePlaceholders(String sectionXml, Map<String, ImageAsset> imageAssets) {
        String patched = sectionXml;
        int index = 0;
        for (Map.Entry<String, ImageAsset> entry : imageAssets.entrySet()) {
            String field = entry.getKey();
            String placeholder = "[[DRAWING:" + field + "]]";
            String fileName = mediaFileName(field, entry.getValue());
            float width = field.startsWith("methodShot_") ? SHOT_IMAGE_WIDTH : TREND_IMAGE_WIDTH;
            int heightPx = pngHeightPx(entry.getValue(), width);
            long cx = (long) (width * 9525);
            long cy = (long) (heightPx * 9525);
            index++;
            // w:drawing 必须是 w:r 的子元素（ECMA-376），替换产物重建整个 run
            String drawing = "<w:r><w:drawing><wp:inline xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">"
                    + "<wp:extent cx=\"" + cx + "\" cy=\"" + cy + "\"/>"
                    + "<wp:docPr id=\"" + (100 + index) + "\" name=\"" + fileName + "\"/>"
                    + "<a:graphic xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\">"
                    + "<a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">"
                    + "<pic:pic xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">"
                    + "<pic:nvPicPr><pic:cNvPr id=\"" + (100 + index) + "\" name=\"" + fileName
                    + "\"/><pic:cNvPicPr/></pic:nvPicPr>"
                    + "<pic:blipFill><a:blip r:embed=\"rIdMethod" + index + "\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>"
                    + "<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"" + cx + "\" cy=\"" + cy + "\"/></a:xfrm>"
                    + "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr>"
                    + "</pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r>";
            patched = patched.replace(placeholder, drawing);
        }
        return patched;
    }

    /** 嵌入高度：PNG 按 IHDR 真实比例；jpeg/webp 无轻量尺寸解析，用 4:3 回退（与 png 解析失败同口径）。 */
    private int pngHeightPx(ImageAsset asset, float widthPx) {
        int[] size = "image/png".equals(asset.contentType()) ? pngSize(asset.content()) : null;
        return size == null ? Math.round(widthPx * 3 / 4) : Math.round(widthPx * size[1] / (float) size[0]);
    }

    private String appendImageRelationships(String rels, Map<String, ImageAsset> imageAssets) {
        StringBuilder extra = new StringBuilder();
        int index = 0;
        for (Map.Entry<String, ImageAsset> image : imageAssets.entrySet()) {
            index++;
            extra.append("<Relationship Id=\"rIdMethod").append(index)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/")
                    .append(mediaFileName(image.getKey(), image.getValue())).append("\"/>");
        }
        return rels.replace("</Relationships>", extra + "</Relationships>");
    }

    /** 按本次导出实际用到的图片类型条件追加 [Content_Types] Default 声明（png/jpeg/webp）。 */
    private String appendContentTypes(String types, Map<String, ImageAsset> imageAssets) {
        StringBuilder extra = new StringBuilder();
        imageAssets.values().stream()
                .map(ImageAsset::contentType)
                .distinct()
                .forEach(contentType -> {
                    String ext = extensionOf(contentType);
                    if (!types.contains("Extension=\"" + ext + "\"")) {
                        extra.append("<Default Extension=\"").append(ext)
                                .append("\" ContentType=\"").append(contentType).append("\"/>");
                    }
                });
        return extra.isEmpty() ? types : types.replaceFirst("</Types>", extra + "</Types>");
    }

    /** PDF 章节片段：表格 + base64 data URI 图片，缺图占位。 */
    public String buildPdfSectionHtml(MethodSectionView view) {
        if (view.isEmpty()) {
            return "<h2>" + SECTION_TITLE + "</h2><p>暂无测试方法执行数据</p>";
        }
        StringBuilder html = new StringBuilder("<h2>").append(SECTION_TITLE).append("</h2>");
        for (ScenarioView scenario : view.scenarios()) {
            html.append("<h3>").append(escapeHtml(scenario.name())).append("</h3>");
            html.append("<table><tr><th>执行</th><th>并发</th><th>Ramp-Up(s)</th><th>时长(s)</th><th>状态</th>")
                    .append("<th>采样数</th><th>成功率</th><th>AvgRT</th><th>P95</th><th>TPS</th><th>开始时间</th></tr>");
            for (ExecutionView execution : scenario.executions()) {
                Map<String, String> row = execution.row();
                html.append("<tr><td>").append(escapeHtml(row.get("executionName")))
                        .append("</td><td>").append(row.get("threads"))
                        .append("</td><td>").append(row.get("rampUpSec"))
                        .append("</td><td>").append(row.get("durationSec"))
                        .append("</td><td>").append(escapeHtml(row.get("status")))
                        .append("</td><td>").append(row.get("samples"))
                        .append("</td><td>").append(row.get("successRate"))
                        .append("</td><td>").append(row.get("avgRtMs"))
                        .append("</td><td>").append(row.get("p95Ms"))
                        .append("</td><td>").append(row.get("tps"))
                        .append("</td><td>").append(escapeHtml(row.get("startedAtText")))
                        .append("</td></tr>");
            }
            html.append("</table>");
            for (ExecutionView execution : scenario.executions()) {
                html.append("<h4>").append(escapeHtml(execution.title())).append("</h4>");
                appendHtmlImages(html, execution.images(), view.imageAssets());
            }
            html.append("<h4>补充截图</h4>");
            appendHtmlImages(html, scenario.evidence(), view.imageAssets());
        }
        return html.toString();
    }

    private void appendHtmlImages(StringBuilder html, List<ImageView> images, Map<String, ImageAsset> imageAssets) {
        for (ImageView image : images) {
            ImageAsset asset = image.field() == null ? null : imageAssets.get(image.field());
            if (asset == null) {
                html.append("<p>").append(escapeHtml(image.caption())).append("</p>");
            } else {
                html.append("<p><img src=\"data:").append(asset.contentType()).append(";base64,")
                        .append(Base64.getEncoder().encodeToString(asset.content()))
                        .append("\" style=\"width:420px\"/></p><p style=\"font-size:10px;color:#666;\">")
                        .append(escapeHtml(image.caption())).append("</p>");
            }
        }
    }

    private String paragraph(String text, boolean bold, int halfPointSize, boolean centered) {
        return "<w:p>" + (centered ? "<w:pPr><w:jc w:val=\"center\"/></w:pPr>" : "")
                + "<w:r><w:rPr>" + (bold ? "<w:b/>" : "") + "<w:sz w:val=\"" + halfPointSize + "\"/></w:rPr>"
                + "<w:t xml:space=\"preserve\">" + escapeXml(text) + "</w:t></w:r></w:p>";
    }

    /** PNG IHDR 宽高（big-endian，宽偏移 16 高偏移 20），非 PNG 或损坏返回 null。 */
    private int[] pngSize(byte[] bytes) {
        if (bytes == null || bytes.length < 24) {
            return null;
        }
        int width = ((bytes[16] & 0xFF) << 24) | ((bytes[17] & 0xFF) << 16) | ((bytes[18] & 0xFF) << 8) | (bytes[19] & 0xFF);
        int height = ((bytes[20] & 0xFF) << 24) | ((bytes[21] & 0xFF) << 16) | ((bytes[22] & 0xFF) << 8) | (bytes[23] & 0xFF);
        if (width <= 0 || height <= 0) {
            return null;
        }
        return new int[]{width, height};
    }

    private byte[] decodeDataUri(String dataUri) {
        try {
            int idx = dataUri.indexOf(',');
            if (idx < 0) {
                return null;
            }
            return Base64.getDecoder().decode(dataUri.substring(idx + 1));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /**
     * XML 转义（&amp;、&lt;、&gt;、&quot;）。Freemarker 防注入不在此层：章节 XML 整体由
     * buildWordSectionXml 包裹的 [#noparse] 保护（实测数字实体 `&#36;` 会被 XDocReport 的 SAX
     * 预处理解码回 `$`、`${'$'}` 自逃逸会被预处理把 `'` 转义为 `&apos;` 而失效，均不可行）。
     * 边缘限制：用户数据精确包含 `[/#noparse]` 字面序列时仍会提前闭合保护（概率与危害极低，报告已注明）。
     */
    private String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /** PDF HTML 路径仅做 XML/HTML 转义，不做 Freemarker 防注入（HTML 不经 Freemarker 求值）。 */
    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }
}
