package com.yr.perftest.platform.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.execution.aggregate.PersistentAggregateReportRecord;
import com.yr.perftest.platform.execution.aggregate.PersistentAggregateReportRepository;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRecord;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRepository;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.PersistentTaskScenarioRecord;
import com.yr.perftest.platform.task.PersistentTaskScenarioRepository;
import com.yr.perftest.platform.task.method.PersistentPlanEvidenceImageRecord;
import com.yr.perftest.platform.task.method.PlanEvidenceImageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:report-method-export-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.h2.console.enabled=false",
        "platform.storage.root=./build/test-storage/report-method-export"
})
@AutoConfigureMockMvc
@Transactional
class ReportMethodExportTest {
    private static final String WORD_EXPORT_URL = "/api/reports/plans/%d/export/word";
    private static final String PDF_EXPORT_URL = "/api/reports/plans/%d/export/pdf";
    private static final String CONFIG_JSON =
            "{\"threads\":300,\"rampUp\":10,\"duration\":600,\"loops\":1,\"jmeterProperties\":{},"
                    + "\"mode\":\"DISTRIBUTED\",\"controllerNodeId\":1,\"workerNodeIds\":[1],\"monitorTargetIds\":[]}";
    /** 最小合法 RIFF/WEBP 文件头（导出侧不解码，仅校验嵌入分支）。 */
    private static final byte[] WEBP_HEADER_BYTES = java.util.HexFormat.of().parseHex(
            "5249464624000000574542505650382010000000300100002E011300");
    /** 1x1 白色 PNG。 */
    private static final String TINY_PNG_DATA_URL = "data:image/png;base64,"
            + "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PersistentTaskPlanRepository planRepository;

    @Autowired
    private PersistentTaskScenarioRepository scenarioRepository;

    @Autowired
    private PersistentScenarioExecutionRepository executionRepository;

    @Autowired
    private PersistentAggregateReportRepository aggregateRepository;

    @Autowired
    private PlanEvidenceImageRepository imageRepository;

    @Autowired
    private com.yr.perftest.platform.project.PersistentProjectRepository projectRepository;

    @Autowired
    private com.yr.perftest.platform.script.ScriptService scriptService;

    private String adminToken;
    private long planId;
    private long scriptVersionId;

    @BeforeEach
    void setUp() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(login.getResponse().getContentAsString());
        adminToken = body.get("token").asText();

        long projectId = projectRepository.save(
                new com.yr.perftest.platform.project.PersistentProjectRecord(
                        "P-EXPORT", "导出测试项目", "", "admin")).getId();
        scriptVersionId = scriptService.createScript(projectId, "loan-export", "admin").id();
        PersistentTaskPlanRecord plan = planRepository.save(
                new PersistentTaskPlanRecord(projectId, "方法导出计划", null, "admin"));
        planId = plan.getId();
    }

    /** 给计划写入含「测试方法」章节的文档 body（spec §8：导出追加节仅对含该章节的计划生效）。 */
    private void enableTestMethodChapter() {
        planRepository.findById(planId).ifPresent(plan -> {
            plan.updateBody("# 方法导出计划\n\n## 一、测试方法\n\n**方法说明**：（自由编辑）\n");
            planRepository.save(plan);
        });
    }

    private Map<String, String> documentXmlOf(byte[] docx) throws Exception {
        Map<String, String> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        }
        return entries;
    }

    private String entryContentOf(byte[] docx, String entryName) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entryName.equals(entry.getName())) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        return null;
    }

    private java.util.List<String> entryNamesOf(byte[] docx) throws Exception {
        java.util.List<String> names = new java.util.ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                names.add(entry.getName());
            }
        }
        return names;
    }

    private org.w3c.dom.Document parseDocumentXml(String xml) throws Exception {
        var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder()
                .parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));
    }

    @Test
    void wordExportDrawingSitsInsideRunNotInsideText() throws Exception {
        enableTestMethodChapter();
        long scenarioId = scenarioRepository.save(
                new PersistentTaskScenarioRecord(planId, scriptVersionId, "结构场景", 0)).getId();
        PersistentScenarioExecutionRecord execution = executionRepository.save(
                new PersistentScenarioExecutionRecord(scenarioId, CONFIG_JSON));
        execution.markRunning("r1.jtl", "j1.log");
        execution.markSuccess(0);
        long executionId = executionRepository.save(execution).getId();

        // webp 截图（最小合法 RIFF/WEBP 头），验证导出按 contentType 分支嵌入
        Path shotPath = Path.of("build/test-storage/report-method-export", "structure-shot-" + planId + ".webp");
        Files.createDirectories(shotPath.getParent());
        Files.write(shotPath, WEBP_HEADER_BYTES);
        imageRepository.save(new PersistentPlanEvidenceImageRecord(
                planId, scenarioId, "结构截图", 0, shotPath.toString(), "image/webp", 100, "admin"));

        String requestBody = objectMapper.writeValueAsString(Map.of(
                "chartImages", Map.of(),
                "editorContent", "",
                "methodChartImages", new Object[]{
                        Map.of("executionId", executionId, "kind", "TPS", "dataUrl", TINY_PNG_DATA_URL)
                }));

        MvcResult result = mockMvc.perform(post(String.format(WORD_EXPORT_URL, planId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn();

        byte[] docx = result.getResponse().getContentAsByteArray();

        // webp 截图：media 扩展名、Content_Types 声明、rels Target 均按 contentType
        assertThat(entryNamesOf(docx).stream().anyMatch(
                name -> name.matches("word/media/methodShot_\\d+\\.webp"))).isTrue();
        assertThat(entryContentOf(docx, "[Content_Types].xml"))
                .contains("Extension=\"webp\" ContentType=\"image/webp\"");
        assertThat(entryContentOf(docx, "word/_rels/document.xml.rels")).contains("media/methodShot_");

        // 文档 well-formed（DOM parse 成功即证）+ 结构合法性断言
        org.w3c.dom.Document document = parseDocumentXml(
                documentXmlOf(docx).get("word/document.xml"));
        String wordNs = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

        // 每个 w:drawing 的父元素必须是 w:r
        var drawings = document.getElementsByTagNameNS(wordNs, "drawing");
        assertThat(drawings.getLength()).isGreaterThanOrEqualTo(2);
        for (int i = 0; i < drawings.getLength(); i++) {
            org.w3c.dom.Node parent = drawings.item(i).getParentNode();
            assertThat(parent.getNamespaceURI()).isEqualTo(wordNs);
            assertThat(parent.getLocalName()).isEqualTo("r");
        }

        // 任何 w:t 不得包含 w:drawing 子元素（CT_Text 仅允许文本）
        var texts = document.getElementsByTagNameNS(wordNs, "t");
        assertThat(texts.getLength()).isGreaterThan(0);
        for (int i = 0; i < texts.getLength(); i++) {
            org.w3c.dom.NodeList children = texts.item(i).getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                org.w3c.dom.Node child = children.item(j);
                assertThat(child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE
                        && "drawing".equals(child.getLocalName()))
                        .as("w:t must not contain w:drawing")
                        .isFalse();
            }
        }
    }

    @Test
    void wordExportKeepsFreemarkerLikeSequencesInUserDataLiteral() throws Exception {
        enableTestMethodChapter();
        String scenarioName = "场景${name}$端 #{h}";
        String executionName = "执行$1 ${x}";
        long scenarioId = scenarioRepository.save(
                new PersistentTaskScenarioRecord(planId, scriptVersionId, scenarioName, 0)).getId();
        PersistentScenarioExecutionRecord execution = executionRepository.save(
                new PersistentScenarioExecutionRecord(scenarioId, CONFIG_JSON));
        execution.setExecutionName(executionName);
        execution.markRunning("r1.jtl", "j1.log");
        execution.markSuccess(0);
        executionRepository.save(execution);
        imageRepository.save(new PersistentPlanEvidenceImageRecord(
                planId, scenarioId, "截图$ ${y}", 0,
                "build/test-storage/report-method-export/no-such-file.png", "image/png", 10, "admin"));

        String requestBody = objectMapper.writeValueAsString(Map.of(
                "chartImages", Map.of(),
                "editorContent", "",
                "methodChartImages", java.util.List.of()));

        // Freemarker 序列未转义时会在模板求值阶段抛 InvalidReferenceException → 500
        MvcResult result = mockMvc.perform(post(String.format(WORD_EXPORT_URL, planId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn();

        org.w3c.dom.Document document = parseDocumentXml(
                documentXmlOf(result.getResponse().getContentAsByteArray()).get("word/document.xml"));
        String body = document.getDocumentElement().getTextContent();
        assertThat(body).contains("场景${name}$端 #{h}");
        assertThat(body).contains("执行$1 ${x}");
        assertThat(body).contains("（补充截图缺失：截图$ ${y}）");
    }

    @Test
    void wordExportWithoutMethodDataProducesValidDocx() throws Exception {
        MvcResult result = mockMvc.perform(post(String.format(WORD_EXPORT_URL, planId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chartImages\":{},\"editorContent\":\"\"}"))
                .andExpect(status().isOk())
                .andReturn();

        byte[] docx = result.getResponse().getContentAsByteArray();
        assertThat(docx.length).isGreaterThan(100);
        String documentXml = documentXmlOf(docx).get("word/document.xml");
        assertThat(documentXml).isNotBlank();
        assertThat(documentXml).doesNotContain("测试方法执行结果");
    }

    @Test
    void wordExportSkipsSectionWhenPlanHasNoTestMethodChapter() throws Exception {
        // 计划文档不含「测试方法」章节（内置模板存量形态）：有场景与执行也不追加导出节（spec §8 章节作用域）
        long scenarioId = scenarioRepository.save(
                new PersistentTaskScenarioRecord(planId, scriptVersionId, "无章节场景", 0)).getId();
        PersistentScenarioExecutionRecord execution = executionRepository.save(
                new PersistentScenarioExecutionRecord(scenarioId, CONFIG_JSON));
        execution.markRunning("r1.jtl", "j1.log");
        execution.markSuccess(0);
        executionRepository.save(execution);

        MvcResult result = mockMvc.perform(post(String.format(WORD_EXPORT_URL, planId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chartImages\":{},\"editorContent\":\"\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String documentXml = documentXmlOf(result.getResponse().getContentAsByteArray()).get("word/document.xml");
        assertThat(documentXml).doesNotContain("测试方法执行结果");
    }

    @Test
    void exportDeniedForNonProjectMember() throws Exception {
        // tester 为种子用户（非项目 owner、非成员、无 ADMIN 角色）
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"tester\",\"password\":\"tester123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String testerToken = objectMapper.readTree(login.getResponse().getContentAsString())
                .get("token").asText();

        mockMvc.perform(post(String.format(WORD_EXPORT_URL, planId))
                        .header("Authorization", "Bearer " + testerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chartImages\":{},\"editorContent\":\"\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(String.format(PDF_EXPORT_URL, planId))
                        .header("Authorization", "Bearer " + testerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void wordExportEmbedsMethodSectionTableChartsAndShots() throws Exception {
        enableTestMethodChapter();
        long scenarioId = scenarioRepository.save(
                new PersistentTaskScenarioRecord(planId, scriptVersionId, "导出场景A", 0)).getId();
        PersistentScenarioExecutionRecord execution = executionRepository.save(
                new PersistentScenarioExecutionRecord(scenarioId, CONFIG_JSON));
        execution.setExecutionName("300并发 首轮");
        execution.markRunning("r1.jtl", "j1.log");
        execution.markSuccess(0);
        long executionId = executionRepository.save(execution).getId();
        aggregateRepository.save(new PersistentAggregateReportRecord(
                executionId, "final", System.currentTimeMillis() - 60_000, System.currentTimeMillis(), 60.0,
                "{\"samples\":1000,\"throughput\":682.4,\"avgRt\":120,\"p95\":300,\"errorRate\":0.2,\"accuracy\":\"final\"}",
                "[]", null, java.time.Instant.now(), "test"));

        // 截图：一张磁盘存在、一张文件缺失（降级占位）
        Path shotPath = Path.of("build/test-storage/report-method-export", "shot-" + planId + ".png");
        Files.createDirectories(shotPath.getParent());
        Files.write(shotPath, Base64.getDecoder().decode(TINY_PNG_DATA_URL.substring("data:image/png;base64,".length())));
        imageRepository.save(new PersistentPlanEvidenceImageRecord(
                planId, scenarioId, "TPS 截图", 0, shotPath.toString(), "image/png", 100, "admin"));
        imageRepository.save(new PersistentPlanEvidenceImageRecord(
                planId, scenarioId, "丢失截图", 1, "build/test-storage/report-method-export/missing.png",
                "image/png", 100, "admin"));

        // 隐藏行不进导出
        PersistentScenarioExecutionRecord hidden = executionRepository.save(
                new PersistentScenarioExecutionRecord(scenarioId, CONFIG_JSON));
        hidden.setExecutionName("已隐藏执行");
        hidden.markRunning("r2.jtl", "j2.log");
        hidden.markSuccess(0);
        hidden.setMethodHidden(true);
        executionRepository.save(hidden);

        String requestBody = objectMapper.writeValueAsString(Map.of(
                "chartImages", Map.of(),
                "editorContent", "",
                "methodChartImages", new Object[]{
                        Map.of("executionId", executionId, "kind", "TPS", "dataUrl", TINY_PNG_DATA_URL),
                        Map.of("executionId", executionId, "kind", "RT", "dataUrl", TINY_PNG_DATA_URL),
                }));

        MvcResult result = mockMvc.perform(post(String.format(WORD_EXPORT_URL, planId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn();

        byte[] docx = result.getResponse().getContentAsByteArray();
        String documentXml = documentXmlOf(docx).get("word/document.xml");
        assertThat(documentXml).contains("测试方法执行结果");
        assertThat(documentXml).contains("导出场景A");
        assertThat(documentXml).contains("300并发 首轮");
        assertThat(documentXml).doesNotContain("已隐藏执行");
        assertThat(documentXml).contains("CPU 使用率趋势缺失");
        assertThat(documentXml).contains("补充截图缺失：丢失截图");
        // 趋势图 / 截图以 inline drawing 嵌入，media 与关系同步注入
        assertThat(documentXml).contains("r:embed=\"rIdMethod1\"");
        assertThat(documentXml).doesNotContain("[[IMG:");
        assertThat(entryNamesOf(docx)).contains("word/media/methodImg_" + executionId + "_TPS.png");
        assertThat(entryNamesOf(docx).stream().anyMatch(
                name -> name.matches("word/media/methodShot_\\d+\\.png"))).isTrue();
    }

    @Test
    void pdfExportEmbedsMethodSectionAndKeepsLegacyBodylessCall() throws Exception {
        enableTestMethodChapter();
        long scenarioId = scenarioRepository.save(
                new PersistentTaskScenarioRecord(planId, scriptVersionId, "导出场景B", 0)).getId();
        PersistentScenarioExecutionRecord execution = executionRepository.save(
                new PersistentScenarioExecutionRecord(scenarioId, CONFIG_JSON));
        execution.markRunning("r1.jtl", "j1.log");
        execution.markSuccess(0);
        long executionId = executionRepository.save(execution).getId();

        String requestBody = objectMapper.writeValueAsString(Map.of(
                "chartImages", Map.of(),
                "editorContent", "",
                "methodChartImages", new Object[]{
                        Map.of("executionId", executionId, "kind", "TPS", "dataUrl", TINY_PNG_DATA_URL)
                }));

        MvcResult withBody = mockMvc.perform(post(String.format(PDF_EXPORT_URL, planId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn();
        byte[] pdf = withBody.getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        Files.write(Path.of("build/test-storage/report-method-export/last-export.pdf"), pdf);

        // 既有无 body 调用保持兼容
        mockMvc.perform(post(String.format(PDF_EXPORT_URL, planId))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }
}
