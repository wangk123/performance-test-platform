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
    void wordExportEmbedsMethodSectionTableChartsAndShots() throws Exception {
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
        assertThat(entryNamesOf(docx)).contains("word/media/methodImg_" + executionId + "_TPS.png",
                "word/media/methodShot_1.png");
    }

    @Test
    void pdfExportEmbedsMethodSectionAndKeepsLegacyBodylessCall() throws Exception {
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
