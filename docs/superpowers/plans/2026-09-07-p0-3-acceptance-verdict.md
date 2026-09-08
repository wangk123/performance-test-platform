# P0-3 验收标准解析 + 自动判等（指标可选）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 计划文档"二、测试目的与指标"章节成为判等唯一输入——保存链解析即校验（不落库、不建判等实体），报告生成时场景级+交易级两层自动判等并幂等重绘达成表，verdict 只读接口即时重算，无指标计划判等全程跳过。

**Architecture:** 新增纯静态解析器 `PlanAcceptanceParser`（`task/plandoc` 包，spec §3）供保存校验与报告判等共用；新增 `PlanVerdictService` 判等引擎（复用每场景最近执行 + `ExecutionQueryService.getResult` 数据面）；`PlanWorkflowService.generateReport` 按有无指标分流（有 → `upsertVerdictTable` 幂等块重绘；无 → P0-1 现状不回归）；`PlanDocumentController` 暴露 `GET /task-plans/{planId}/verdict`；前端报告 Tab 加判定徽章/下钻、发布表单预填。

**Tech Stack:** Spring Boot 3.5 / Java 17（后端，JUnit5 `@SpringBootTest` + 纯单元两种测试风格）；Vue3 + ant-design-vue + TypeScript（前端）。

**Spec:** `docs/superpowers/specs/2026-09-07-p0-3-acceptance-verdict-design.md`（本计划一切取舍以 spec 为准）

## Global Constraints

- **不建任何持久化实体/表**（spec V2/§10）：判等输入 = 文档指标章节，解析即校验、报告期确定性解析。
- **严格校验只挂保存链** `PlanDocumentService.updateMarkdown`（Web PUT / Pretty 章节合并提交 / MCP `plan_update` 三路共用收口，spec §3.1）；系统回填（`backfillExecutionRecord`、`writeBackEntryChecklist`、达成表重绘）**不走**该链、不受校验拦截。
- 校验失败抛 `PlanValidationException`（已映射 400 `PLAN_INVALID`，消息自带前缀风格 `PLAN_INVALID：…`，见 `PlatformExceptionHandler`）。
- 指标类型别名映射（trim + 大小写不敏感，spec §3.4）：TPS/吞吐量/throughput→TPS；平均RT/响应时间→AVG_RT；P95/P95响应时间→P95；P99→P99；错误率→ERROR_RATE；并发峰值→PEAK_CONCURRENCY；容量→CAPACITY；其它→OTHER（放行，判等落无法判定）。
- 表头兼容：第一数据列接受 `对象`（新模板）或 `交易`（存量 P0-1 文档），`指标`、`目标值` 列必须存在；缺 → 400 指明缺列（spec §3.1 + §3.3 向后兼容）。
- 行状态三态达成/未达成/无法判定；总体聚合：任一未达成→未达成，全部可判达成→达成，其余→无法判定；无指标→不产生判定（spec V5/§4.3）。
- verdict 接口即时重算不持久化；仅 `REPORT/DONE` 与 `PUBLISH/PUBLISHED` 可读（available），复测重置后 available=false（spec V8/§6.1）。
- 总体结论不预写文档，仅接口返回 prefillConclusion（spec V7）。
- 不新增任何依赖、不拆 Maven 模块，新类全放 `task/plandoc`（spec §7）。
- Gradle 一律 `JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/`（AGENTS.md）。
- 提交信息风格：`feat：P0-3 …——细节`（全角冒号+破折号，沿用仓库惯例）；小步提交。
- P0-1 遗留的项目级模板创建接缝（`POST /projects/{id}/plan-templates`）**保留不动**（任务书特别约束：spec 未裁决默认保留，待人工验收走查定夺）。

## 现状锚点（实施者必读）

- 保存链：`PlanDocumentService.updateMarkdown(planId, baseRevision, markdown, actor)`（`backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanDocumentService.java:47`）。
- 章节定位：`PlanMarkdownSupport.extractSection(body, "二、测试目的与指标")`（含序号容错）；`CANONICAL_HEADINGS` 见同类。
- 幂等块参照：`PlanWorkflowService.upsertReportOverview`（`<!-- backfill:report -->`，块尾 = 其后第一个其它标题行或 `**总体结论**` 行之前）。
- 数据面：`ExecutionQueryService.getResult(executionId)` → `TaskExecutionResult(Summary(samples,throughput,avgRt,p95,errorRate,accuracy), List<AggregateRow(label,…,average,p95,p99,…,errorRate,throughput)>, samples)`；每场景最近执行 = `executionRepository.findFirstByScenarioIdOrderByIdDesc(scenarioId)`。
- 测试造数：执行记录 `new PersistentScenarioExecutionRecord(scenarioId, configJson)`（status QUEUED，需 setter/forceState 调整终态——用 `forceState` 同名方法？无，执行记录用 JPA set* 不全公开，见 Task 3 测试代码用的现成 setter）；聚合结果 `PersistentAggregateReportRecord(executionId, accuracy, startMs, endMs, durationSeconds, summaryJson, rowsJson, snapshotBlob, generatedAt, builderVersion)` 直接入库（`AggregateReportService.loadPersisted` 读取）。
- MockMvc 认证：`authTokenService.issue("admin")` + `Authorization: Bearer`（参照 `SecurityConfigurationTest`）。
- 前端构建：`cd frontend && npm run build`（= `vue-tsc --noEmit && vite build`；仓库无 lint script）。
- 执行详情路由：`{ name: 'project-execution-detail', params: { projectId, executionId } }`（`frontend/src/router/index.ts:42`）。
- **前端无独立"指标表单"**：Pretty 视图对"二、测试目的与指标"直接 MdPreview 渲染 Markdown，spec §3.5"Pretty 指标表单列名改对象"实际落点仅模板 seed 表头（无前端代码改动点）。

---

### Task 1: PlanAcceptanceParser 指标章节解析器（纯静态）

**Files:**
- Create: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanAcceptanceParser.java`
- Test: `backend/src/test/java/com/yr/perftest/platform/task/plandoc/PlanAcceptanceParserTest.java`

**Interfaces:**
- Consumes: `PlanMarkdownSupport.extractSection(String body, String title)`；`PlanValidationException(String)`。
- Produces（后续任务依赖的精确签名）:
  - `public enum MetricType { TPS, AVG_RT, P95, P99, ERROR_RATE, PEAK_CONCURRENCY, CAPACITY, OTHER }`（嵌套在 parser 内：`PlanAcceptanceParser.MetricType`）
  - `public record AcceptanceMetricRow(int lineNumber, String objectName, String metricRaw, MetricType metricType, String targetRaw, Double targetValue)`
  - `public record AcceptanceSection(boolean present, List<AcceptanceMetricRow> rows)`
  - `public static AcceptanceSection parse(String body)` — 格式非法抛 `PlanValidationException`（消息 `PLAN_INVALID：…`，含行号/缺列原因）；章节缺失/无表格/空表 → `present=false`；未识别类型 → OTHER 放行。

- [ ] **Step 1: 写失败测试**（纯 JUnit，无 Spring）

```java
package com.yr.perftest.platform.task.plandoc;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanAcceptanceParserTest {

    private static String doc(String metricSection) {
        return "# 计划\n\n## 一、背景\n\nb\n\n## 二、测试目的与指标\n\n" + metricSection
                + "\n\n## 三、测试范围\n\nt\n";
    }

    @Test
    void parsesMetricRowsWithAliasAndDirection() {
        PlanAcceptanceParser.AcceptanceSection section = PlanAcceptanceParser.parse(doc("""
                | 对象 | 指标 | 目标值 | 口径 |
                |---|---|---|---|
                | 登录场景 | TPS | ≥ 200 | 5 分钟均值 |
                | 下单交易 | p95响应时间 | ≤ 300 ms | 全量样本 |
                | 下单交易 | 错误率 | ≤ 0.5% | 全量样本 |
                | 系统容量 | 容量 | 1万在线用户 | 自由口径 |
                | GC 观察 | GC 次数 | 少于 10 次 | 人工 |"""));
        assertThat(section.present()).isTrue();
        List<PlanAcceptanceParser.AcceptanceMetricRow> rows = section.rows();
        assertThat(rows).hasSize(5);
        assertThat(rows.get(0).metricType()).isEqualTo(PlanAcceptanceParser.MetricType.TPS);
        assertThat(rows.get(0).targetValue()).isEqualTo(200d);
        assertThat(rows.get(1).metricType()).isEqualTo(PlanAcceptanceParser.MetricType.P95);
        assertThat(rows.get(1).targetValue()).isEqualTo(300d);
        assertThat(rows.get(2).metricType()).isEqualTo(PlanAcceptanceParser.MetricType.ERROR_RATE);
        assertThat(rows.get(2).targetValue()).isEqualTo(0.5d);
        assertThat(rows.get(3).metricType()).isEqualTo(PlanAcceptanceParser.MetricType.CAPACITY);
        assertThat(rows.get(3).targetValue()).isNull(); // 自由文本目标
        assertThat(rows.get(4).metricType()).isEqualTo(PlanAcceptanceParser.MetricType.OTHER);
        assertThat(rows.get(4).targetValue()).isNull();
        assertThat(rows.get(0).objectName()).isEqualTo("登录场景");
        assertThat(rows.get(0).lineNumber()).isEqualTo(1); // 数据行序号（1 基）
    }

    @Test
    void acceptsLegacyTradingHeaderForBackwardCompatibility() {
        PlanAcceptanceParser.AcceptanceSection section = PlanAcceptanceParser.parse(doc("""
                | 交易 | 指标 | 目标值 | 口径 |
                |---|---|---|---|
                | 查询交易 | TPS | ≥ 200 | 5 分钟均值 |"""));
        assertThat(section.present()).isTrue();
        assertThat(section.rows().get(0).objectName()).isEqualTo("查询交易");
    }

    @Test
    void missingSectionOrTableOrEmptyTableFallsToNoMetricPath() {
        assertThat(PlanAcceptanceParser.parse("# 无章节\n").present()).isFalse();
        assertThat(PlanAcceptanceParser.parse(doc("（纯文字，无表格）")).present()).isFalse();
        assertThat(PlanAcceptanceParser.parse(doc("""
                | 对象 | 指标 | 目标值 | 口径 |
                |---|---|---|---|""")).present()).isFalse(); // 空表（仅表头）
    }

    @Test
    void headerMissingRequiredColumnsRejected() {
        assertThatThrownBy(() -> PlanAcceptanceParser.parse(doc("""
                | 指标名 | 数值 |
                |---|---|
                | TPS | 200 |""")))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("表头缺少列")
                .hasMessageContaining("对象");
    }

    @Test
    void shortDataRowRejectedWithLineNumber() {
        assertThatThrownBy(() -> PlanAcceptanceParser.parse(doc("""
                | 对象 | 指标 | 目标值 | 口径 |
                |---|---|---|---|
                | 登录场景 | TPS |""")))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("第 1 行")
                .hasMessageContaining("单元格数不足");
    }

    @Test
    void nonNumericTargetForKnownTypeRejected() {
        assertThatThrownBy(() -> PlanAcceptanceParser.parse(doc("""
                | 对象 | 指标 | 目标值 | 口径 |
                |---|---|---|---|
                | 登录场景 | TPS | 两百 | 5 分钟均值 |""")))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("第 1 行")
                .hasMessageContaining("目标值");
    }

    @Test
    void sectionTitleOrderingTolerated() {
        // 序号容错：标题写成「二、xxx」别名仍能定位（extractSection 的序号容错）
        String body = "# p\n\n## 二、目的与指标（修订）\n\n| 对象 | 指标 | 目标值 |\n|---|---|---|\n| S | TPS | 100 |\n";
        assertThat(PlanAcceptanceParser.parse(body).present()).isTrue();
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ ./gradlew :backend:test --tests 'com.yr.perftest.platform.task.plandoc.PlanAcceptanceParserTest'
```
Expected: 编译失败（`PlanAcceptanceParser` 不存在）。

- [ ] **Step 3: 实现解析器**

```java
package com.yr.perftest.platform.task.plandoc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 「二、测试目的与指标」章节表格解析器（spec §3）：保存校验与报告判等共用，
 * 纯静态、无状态、不落库。格式非法抛 PLAN_INVALID；章节缺失/无表格/空表 → 无指标路径。
 */
public final class PlanAcceptanceParser {

    public enum MetricType { TPS, AVG_RT, P95, P99, ERROR_RATE, PEAK_CONCURRENCY, CAPACITY, OTHER }

    /** lineNumber 为数据行序号（1 基，仅数据行）；targetValue 对不可自动判类型为 null。 */
    public record AcceptanceMetricRow(
            int lineNumber, String objectName, String metricRaw, MetricType metricType,
            String targetRaw, Double targetValue) {
    }

    public record AcceptanceSection(boolean present, List<AcceptanceMetricRow> rows) {
        public static final AcceptanceSection EMPTY = new AcceptanceSection(false, List.of());
    }

    /** trim + 大小写不敏感的别名映射（spec §3.4）。 */
    private static final Map<String, MetricType> ALIASES = Map.ofEntries(
            Map.entry("tps", MetricType.TPS),
            Map.entry("吞吐量", MetricType.TPS),
            Map.entry("throughput", MetricType.TPS),
            Map.entry("平均rt", MetricType.AVG_RT),
            Map.entry("响应时间", MetricType.AVG_RT),
            Map.entry("p95", MetricType.P95),
            Map.entry("p95响应时间", MetricType.P95),
            Map.entry("p99", MetricType.P99),
            Map.entry("错误率", MetricType.ERROR_RATE),
            Map.entry("并发峰值", MetricType.PEAK_CONCURRENCY),
            Map.entry("容量", MetricType.CAPACITY));

    private static final String OBJECT_COLUMN = "对象";
    private static final String LEGACY_OBJECT_COLUMN = "交易"; // P0-1 存量文档表头，向后兼容（spec §3.3）
    private static final String METRIC_COLUMN = "指标";
    private static final String TARGET_COLUMN = "目标值";

    private PlanAcceptanceParser() {
    }

    public static AcceptanceSection parse(String body) {
        String section = PlanMarkdownSupport.extractSection(body, "二、测试目的与指标");
        if (section == null) {
            return AcceptanceSection.EMPTY;
        }
        List<String[]> table = firstTableOf(section);
        if (table.isEmpty()) {
            return AcceptanceSection.EMPTY;
        }
        String[] header = table.get(0);
        int objectCol = columnOf(header, OBJECT_COLUMN, LEGACY_OBJECT_COLUMN);
        int metricCol = columnOf(header, METRIC_COLUMN);
        int targetCol = columnOf(header, TARGET_COLUMN);
        List<String> missing = new ArrayList<>();
        if (objectCol < 0) {
            missing.add(OBJECT_COLUMN);
        }
        if (metricCol < 0) {
            missing.add(METRIC_COLUMN);
        }
        if (targetCol < 0) {
            missing.add(TARGET_COLUMN);
        }
        if (!missing.isEmpty()) {
            throw new PlanValidationException("PLAN_INVALID：指标表表头缺少列：" + String.join("、", missing));
        }
        List<AcceptanceMetricRow> rows = new ArrayList<>();
        for (int i = 1; i < table.size(); i++) { // i=0 是表头；table 已剔除分隔线行
            String[] cells = table.get(i);
            if (cells.length < header.length) {
                throw new PlanValidationException("PLAN_INVALID：指标表第 " + i + " 行单元格数不足（需 "
                        + header.length + " 列，实际 " + cells.length + " 列）");
            }
            String objectName = cells[objectCol].trim();
            String metricRaw = cells[metricCol].trim();
            String targetRaw = cells[targetCol].trim();
            MetricType type = resolveType(metricRaw);
            Double targetValue = parseTargetValue(type, targetRaw, i);
            rows.add(new AcceptanceMetricRow(i, objectName, metricRaw, type, targetRaw, targetValue));
        }
        return rows.isEmpty() ? AcceptanceSection.EMPTY : new AcceptanceSection(true, List.copyOf(rows));
    }

    /** 章节内第一个 Markdown 表：表头行 + 数据行（剔除 |---| 分隔线）；无表返回空。 */
    private static List<String[]> firstTableOf(String section) {
        List<String[]> rows = new ArrayList<>();
        for (String line : section.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.length() > 1) {
                String[] cells = splitCells(trimmed);
                if (isSeparatorRow(cells)) {
                    continue;
                }
                rows.add(cells);
            } else if (!rows.isEmpty()) {
                break; // 表格中断
            }
        }
        return rows;
    }

    private static String[] splitCells(String row) {
        String inner = row.substring(1, row.length() - 1);
        return inner.split("\\|", -1);
    }

    private static boolean isSeparatorRow(String[] cells) {
        boolean any = false;
        for (String cell : cells) {
            String c = cell.trim();
            if (c.isEmpty()) {
                continue;
            }
            if (!c.matches(":?-{2,}:?")) {
                return false;
            }
            any = true;
        }
        return any;
    }

    private static int columnOf(String[] header, String... names) {
        for (int i = 0; i < header.length; i++) {
            for (String name : names) {
                if (header[i].trim().equalsIgnoreCase(name)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static MetricType resolveType(String metricRaw) {
        MetricType type = ALIASES.get(metricRaw.trim().toLowerCase(Locale.ROOT));
        return type == null ? MetricType.OTHER : type;
    }

    /** 已知可判类型剥方向符与单位后须为数字（spec §3.1 第 4 行）；其余类型不解析数值。 */
    private static Double parseTargetValue(MetricType type, String targetRaw, int lineNumber) {
        if (type != MetricType.TPS && type != MetricType.AVG_RT && type != MetricType.P95
                && type != MetricType.P99 && type != MetricType.ERROR_RATE) {
            return null;
        }
        String stripped = targetRaw
                .replaceFirst("^[≥≤><=]{1,2}", "")
                .replaceFirst("^(>=|<=)", "")
                .trim()
                .replaceFirst("(?i)(ms|毫秒|%|req/s|r/s|次/s|次/秒|笔/s|笔/秒)$", "")
                .trim();
        try {
            return Double.parseDouble(stripped);
        } catch (NumberFormatException exception) {
            throw new PlanValidationException("PLAN_INVALID：指标表第 " + lineNumber
                    + " 行目标值「" + targetRaw + "」非数字（指标 " + type + " 需可解析数值目标）");
        }
    }
}
```

注意：方向符剥离顺序——`replaceFirst("^[≥≤><=]{1,2}", "")` 已覆盖 `>=`/`<=` 两字符形态，第二行 replaceFirst 是冗余保险，实现时可合并为一个正则 `^[≥≤><=]{1,2}\\s*`；以测试跑绿为准。

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2 命令。Expected: 8 个用例全 PASS。

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanAcceptanceParser.java backend/src/test/java/com/yr/perftest/platform/task/plandoc/PlanAcceptanceParserTest.java
git commit -m "feat：P0-3 指标章节解析器——「二、测试目的与指标」表格纯静态解析（别名映射/交易表头向后兼容/格式非法 PLAN_INVALID/无指标路径），保存校验与判等共用"
```

---

### Task 2: 保存链严格校验（解析即校验，不落库）

**Files:**
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanDocumentService.java:47-61`（`updateMarkdown`）
- Test: `backend/src/test/java/com/yr/perftest/platform/task/plandoc/PlanAcceptanceSaveValidationTest.java`（新建）

**Interfaces:**
- Consumes: Task 1 `PlanAcceptanceParser.parse(String)`。
- Produces: 保存链行为契约——三路（REST PUT / Pretty 合并提交 / MCP plan_update）保存含非法指标表的文档 → `PlanValidationException`（400 `PLAN_INVALID`），文档与 revision 不变。

- [ ] **Step 1: 写失败测试**（`@SpringBootTest` 风格参照 `PlanReportPublishTest`）

```java
package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.identity.SystemRole;
import com.yr.perftest.platform.mcp.plan.PlanUpdateTool;
import com.yr.perftest.platform.project.PersistentProjectMemberRecord;
import com.yr.perftest.platform.project.PersistentProjectMemberRepository;
import com.yr.perftest.platform.project.PersistentProjectRecord;
import com.yr.perftest.platform.project.PersistentProjectRepository;
import com.yr.perftest.platform.project.ProjectRole;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-acceptance-save-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PlanAcceptanceSaveValidationTest {

    private static final HumanPrincipal OWNER = new HumanPrincipal("owner", Set.of(SystemRole.PROJECT_MEMBER));

    @Autowired
    private PlanDocumentService documentService;
    @Autowired
    private PersistentTaskPlanRepository planRepository;
    @Autowired
    private PersistentProjectRepository projectRepository;
    @Autowired
    private PersistentProjectMemberRepository memberRepository;
    @Autowired
    private PlanUpdateTool planUpdateTool;

    private long planId;

    @BeforeEach
    void setUp() {
        PersistentProjectRecord project = projectRepository.save(new PersistentProjectRecord("P1", "项目一", "", "owner"));
        memberRepository.save(new PersistentProjectMemberRecord(project.getId(), "owner", ProjectRole.OWNER));
        PersistentTaskPlanRecord plan = planRepository.save(
                new PersistentTaskPlanRecord(project.getId(), "计划", null, "owner"));
        planId = plan.getId();
    }

    private String baseDoc(String metricTable) {
        return "## 二、测试目的与指标\n\n" + metricTable + "\n\n## 七、场景设计\n\n（空）\n";
    }

    @Test
    void validTableSavesAndParsesBackDeterministically() {
        String body = baseDoc("""
                | 对象 | 指标 | 目标值 | 口径 |
                |---|---|---|---|
                | 登录场景 | TPS | ≥ 200 | 5 分钟均值 |""");
        documentService.updateMarkdown(planId, 1, body, OWNER);
        var saved = planRepository.findById(planId).orElseThrow();
        assertThat(saved.getRevision()).isEqualTo(2);
        PlanAcceptanceParser.AcceptanceSection parsed = PlanAcceptanceParser.parse(saved.getBody());
        assertThat(parsed.present()).isTrue();
        assertThat(parsed.rows().get(0).objectName()).isEqualTo("登录场景");
    }

    @Test
    void missingSectionOrEmptyTableSavesAsNoMetricPath() {
        documentService.updateMarkdown(planId, 1, "## 一、背景\n\n纯摸底计划，无指标章节\n", OWNER);
        assertThat(planRepository.findById(planId).orElseThrow().getBody()).contains("纯摸底计划");
    }

    @Test
    void invalidHeaderRejectedAndDocumentUnchanged() {
        String original = planRepository.findById(planId).orElseThrow().getBody();
        String bad = baseDoc("""
                | 指标名 | 数值 |
                |---|---|
                | TPS | 200 |""");
        assertThatThrownBy(() -> documentService.updateMarkdown(planId, 1, bad, OWNER))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("表头缺少列");
        var unchanged = planRepository.findById(planId).orElseThrow();
        assertThat(unchanged.getBody()).isEqualTo(original);
        assertThat(unchanged.getRevision()).isEqualTo(1);
    }

    @Test
    void invalidTargetValueRejected() {
        String bad = baseDoc("""
                | 对象 | 指标 | 目标值 | 口径 |
                |---|---|---|---|
                | S | P95 | 慢 | 口径 |""");
        assertThatThrownBy(() -> documentService.updateMarkdown(planId, 1, bad, OWNER))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("非数字");
    }

    @Test
    void unrecognizedMetricTypeSavesAsOther() {
        String body = baseDoc("""
                | 对象 | 指标 | 目标值 | 口径 |
                |---|---|---|---|
                | S | GC 次数 | 少于 10 | 人工 |""");
        documentService.updateMarkdown(planId, 1, body, OWNER);
        PlanAcceptanceParser.AcceptanceSection parsed =
                PlanAcceptanceParser.parse(planRepository.findById(planId).orElseThrow().getBody());
        assertThat(parsed.rows().get(0).metricType()).isEqualTo(PlanAcceptanceParser.MetricType.OTHER);
    }

    @Test
    void mcpPlanUpdateGoesThroughSameValidation() {
        String bad = baseDoc("""
                | 对象 | 指标 | 目标值 |
                |---|---|---|
                | S"""); // 单元格数不足
        Map<String, Object> args = Map.of("planId", (Number) planId, "markdown", bad, "baseRevision", (Number) 1);
        assertThatThrownBy(() -> planUpdateTool.call(args, null))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("单元格数不足");
        assertThat(planRepository.findById(planId).orElseThrow().getRevision()).isEqualTo(1);
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ ./gradlew :backend:test --tests 'com.yr.perftest.platform.task.plandoc.PlanAcceptanceSaveValidationTest'
```
Expected: FAIL——非法表用例不抛异常（校验尚未挂上），`mcpPlanUpdateGoesThroughSameValidation` 与 invalid 用例失败。

- [ ] **Step 3: 挂校验到 updateMarkdown**

在 `PlanDocumentService.updateMarkdown` 中、`requireEditAllowed(plan, actor)` 之后、revision 校验之后、`plan.updateBody(...)` 之前插入：

```java
// 解析即校验（spec §3.1）：格式非法 400，不落库；合法/无指标均放行。
PlanAcceptanceParser.parse(markdown == null ? "" : markdown);
```

（一行改动；import 同包无需新增。）

- [ ] **Step 4: 跑测试确认通过 + 回归既有文档测试**

```bash
JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ ./gradlew :backend:test --tests 'com.yr.perftest.platform.task.plandoc.*'
```
Expected: 新测试 6 个 PASS，plandoc 既有测试全绿（若 `PlanDocumentServiceTest` 既有用例正文含非法指标表导致回归红，属该用例构造数据不合法——修测试数据使其合法，不放松校验）。

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanDocumentService.java backend/src/test/java/com/yr/perftest/platform/task/plandoc/PlanAcceptanceSaveValidationTest.java
git commit -m "feat：P0-3 保存链解析即校验——updateMarkdown 挂 PlanAcceptanceParser 严格校验（表头缺列/单元格不足/目标值非数字 400 PLAN_INVALID），REST/Pretty/MCP plan_update 三路共用，不落库"
```

---

### Task 3: PlanVerdictService 判等引擎

**Files:**
- Create: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanVerdictService.java`
- Test: `backend/src/test/java/com/yr/perftest/platform/task/plandoc/PlanVerdictServiceTest.java`

**Interfaces:**
- Consumes: Task 1 `PlanAcceptanceParser.parse/parseLeniently`（本任务给 parser 补一个 `parseLeniently`：捕获 `PlanValidationException` 返回 `AcceptanceSection.EMPTY`——报告期兜底，spec §3.2）；`PersistentTaskScenarioRepository.findAllByPlanIdOrderBySortOrderAscIdAsc(long)`；`PersistentScenarioExecutionRepository.findFirstByScenarioIdOrderByIdDesc(long)`；`ExecutionQueryService.getResult(long)`。
- Produces（Task 4/5 依赖的精确签名）:
  - `public record VerdictRow(String objectName, String metricRaw, PlanAcceptanceParser.MetricType metricType, String targetRaw, Double targetValue, String actualValue, VerdictStatus status, String reason, Long scenarioId, Long executionId)`
  - `public enum VerdictStatus { ACHIEVED, MISSED, INDETERMINATE }`
  - `public record VerdictResult(boolean present, String overall, String prefillConclusion, List<VerdictRow> rows)` — overall ∈ `PASSED|FAILED|INDETERMINATE|NONE`；present=false 时 overall=NONE、rows 空、prefillConclusion=null。
  - `public record VerdictView(boolean present, boolean available, String overall, String prefillConclusion, List<VerdictRowView> rows)`（REST 响应体；`VerdictRowView` 同 VerdictRow 但 metricType 为 `String`）
  - `public VerdictResult compute(long planId)` — 报告链路用（解析失败兜底为无法判定文本，不抛）。
  - `public VerdictView view(long planId)` — 接口用：`available = (phase==REPORT && status==DONE) || (phase==PUBLISH && status==PUBLISHED)`；available=false 时 overall=NONE、rows 空、prefillConclusion=null（present 仍按解析结果）。

- [ ] **Step 0: 给 parser 补 `parseLeniently`（含测试断言追加进 Task 1 测试文件）**

```java
/** 报告期兜底（spec §3.2）：存量文档解析失败不阻断，返回无指标节（调用方以 prefill 文本标注原因）。 */
public static AcceptanceSection parseLeniently(String body) {
    try {
        return parse(body);
    } catch (PlanValidationException exception) {
        return AcceptanceSection.EMPTY;
    }
}
```

在 `PlanAcceptanceParserTest` 追加：

```java
@Test
void lenientParseSwallowsFormatErrors() {
    assertThat(PlanAcceptanceParser.parseLeniently(doc("""
            | 指标名 | 数值 |
            |---|---|
            | TPS | 200 |""")).present()).isFalse();
}
```

- [ ] **Step 1: 写失败测试**

测试造数工具（类内静态方法）：建项目+OWNER+计划+场景+终态执行+聚合结果。

```java
package com.yr.perftest.platform.task.plandoc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.execution.ExecutionStatus;
import com.yr.perftest.platform.execution.TaskExecutionResult;
import com.yr.perftest.platform.execution.aggregate.PersistentAggregateReportRecord;
import com.yr.perftest.platform.execution.aggregate.PersistentAggregateReportRepository;
import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.identity.SystemRole;
import com.yr.perftest.platform.project.PersistentProjectMemberRecord;
import com.yr.perftest.platform.project.PersistentProjectMemberRepository;
import com.yr.perftest.platform.project.PersistentProjectRecord;
import com.yr.perftest.platform.project.PersistentProjectRepository;
import com.yr.perftest.platform.project.ProjectRole;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRecord;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRepository;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.PersistentTaskScenarioRecord;
import com.yr.perftest.platform.task.PersistentTaskScenarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-verdict-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PlanVerdictServiceTest {

    private static final HumanPrincipal OWNER = new HumanPrincipal("owner", Set.of(SystemRole.PROJECT_MEMBER));

    @Autowired
    private PlanVerdictService verdictService;
    @Autowired
    private PersistentTaskPlanRepository planRepository;
    @Autowired
    private PersistentTaskScenarioRepository scenarioRepository;
    @Autowired
    private PersistentScenarioExecutionRepository executionRepository;
    @Autowired
    private PersistentAggregateReportRepository aggregateRepository;
    @Autowired
    private PersistentProjectRepository projectRepository;
    @Autowired
    private PersistentProjectMemberRepository memberRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private long planId;

    @BeforeEach
    void setUp() {
        PersistentProjectRecord project = projectRepository.save(new PersistentProjectRecord("P1", "项目一", "", "owner"));
        memberRepository.save(new PersistentProjectMemberRecord(project.getId(), "owner", ProjectRole.OWNER));
        PersistentTaskPlanRecord plan = planRepository.save(
                new PersistentTaskPlanRecord(project.getId(), "计划", null, "owner"));
        planId = plan.getId();
    }

    private long scenario(String name) {
        return scenarioRepository.save(new PersistentTaskScenarioRecord(planId, null, name, 1)).getId();
    }

    private long finishedExecution(long scenarioId, TaskExecutionResult.Summary summary,
                                   List<TaskExecutionResult.AggregateRow> rows) {
        PersistentScenarioExecutionRecord execution =
                executionRepository.save(new PersistentScenarioExecutionRecord(scenarioId, "{\"threads\":50}"));
        execution.markFinished(ExecutionStatus.SUCCESS, Instant.now()); // 若无此 setter，用 forceState 同义公开方法；见下
        executionRepository.save(execution);
        try {
            aggregateRepository.save(new PersistentAggregateReportRecord(
                    execution.getId(), "final", 0L, 600_000L, 600d,
                    objectMapper.writeValueAsString(summary),
                    objectMapper.writeValueAsString(rows),
                    new byte[0], Instant.now(), "test"));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        return execution.getId();
    }

    private void doc(String metricTable) {
        PersistentTaskPlanRecord plan = planRepository.findById(planId).orElseThrow();
        plan.updateBody("## 二、测试目的与指标\n\n" + metricTable + "\n\n## 十一、结论\n\n（空）\n");
        planRepository.save(plan);
    }

    private static TaskExecutionResult.Summary summary(double throughput, long avgRt, long p95, double errorRate) {
        return new TaskExecutionResult.Summary(1000, throughput, avgRt, p95, errorRate, "final");
    }

    private static TaskExecutionResult.AggregateRow row(String label, long average, long p95, long p99,
                                                        double errorRate, double throughput) {
        return new TaskExecutionResult.AggregateRow(label, "线程组", 500, average, average, average,
                p95, p99, 1, 999, errorRate, throughput);
    }

    @Test
    void scenarioBindingJudgesFourMetricsWithDirections() {
        long sid = scenario("登录场景");
        long eid = finishedExecution(sid, summary(623.5, 120, 912, 0.32),
                List.of(row("登录", 120, 912, 1500, 0.32, 623.5)));
        doc("""
                | 对象 | 指标 | 目标值 | 口径 |
                |---|---|---|---|
                | 登录场景 | TPS | ≥ 500 | 5 分钟均值 |
                | 登录场景 | TPS | ≥ 700 | 5 分钟均值 |
                | 登录场景 | P95 | ≤ 800 ms | 全量样本 |
                | 登录场景 | 错误率 | ≤ 0.5% | 全量样本 |""");
        PlanVerdictService.VerdictResult result = verdictService.compute(planId);
        assertThat(result.present()).isTrue();
        List<PlanVerdictService.VerdictRow> rows = result.rows();
        assertThat(rows.get(0).status()).isEqualTo(PlanVerdictService.VerdictStatus.ACHIEVED); // 623.5 ≥ 500
        assertThat(rows.get(1).status()).isEqualTo(PlanVerdictService.VerdictStatus.MISSED);   // 623.5 < 700
        assertThat(rows.get(2).status()).isEqualTo(PlanVerdictService.VerdictStatus.MISSED);   // 912 > 800
        assertThat(rows.get(3).status()).isEqualTo(PlanVerdictService.VerdictStatus.ACHIEVED); // 0.32 ≤ 0.5
        assertThat(rows.get(0).actualValue()).isEqualTo("623.5");
        assertThat(rows.get(2).actualValue()).isEqualTo("912ms");
        assertThat(rows.get(3).actualValue()).isEqualTo("0.32%");
        assertThat(rows.get(0).scenarioId()).isEqualTo(sid);
        assertThat(rows.get(0).executionId()).isEqualTo(eid);
        assertThat(result.overall()).isEqualTo("FAILED"); // 任一未达成 → 未达成
        assertThat(result.prefillConclusion()).contains("未达成").contains("登录场景");
    }

    @Test
    void transactionBindingJudgesWithP99AndScenarioP99IsIndeterminate() {
        long sid = scenario("组合场景");
        finishedExecution(sid, summary(800, 100, 500, 0.1),
                List.of(row("下单交易", 80, 300, 950, 0.05, 400.2)));
        doc("""
                | 对象 | 指标 | 目标值 | 口径 |
                |---|---|---|---|
                | 下单交易 | P99 | ≤ 1000 ms | 全量样本 |
                | 下单交易 | TPS | ≥ 500 | 5 分钟均值 |
                | 组合场景 | P99 | ≤ 1000 ms | 全量样本 |""");
        List<PlanVerdictService.VerdictRow> rows = verdictService.compute(planId).rows();
        assertThat(rows.get(0).status()).isEqualTo(PlanVerdictService.VerdictStatus.ACHIEVED);  // 交易级 P99 950 ≤ 1000
        assertThat(rows.get(0).actualValue()).isEqualTo("950ms");
        assertThat(rows.get(1).status()).isEqualTo(PlanVerdictService.VerdictStatus.MISSED);    // 400.2 < 500
        assertThat(rows.get(2).status()).isEqualTo(PlanVerdictService.VerdictStatus.INDETERMINATE);
        assertThat(rows.get(2).reason()).contains("P99");
    }

    @Test
    void ambiguousLabelAndNoMatchAndNoExecutionAreIndeterminate() {
        long sid1 = scenario("场景一");
        long sid2 = scenario("场景二");
        finishedExecution(sid1, summary(100, 50, 100, 0),
                List.of(row("下单交易", 50, 100, 200, 0, 100)));
        finishedExecution(sid2, summary(100, 50, 100, 0),
                List.of(row("下单交易", 50, 100, 200, 0, 100)));
        long sid3 = scenario("未执行场景");
        // sid3 无执行记录
        doc("""
                | 对象 | 指标 | 目标值 | 口径 |
                |---|---|---|---|
                | 下单交易 | TPS | ≥ 50 | 口径 |
                | 不存在的对象 | TPS | ≥ 50 | 口径 |
                | 未执行场景 | TPS | ≥ 50 | 口径 |""");
        List<PlanVerdictService.VerdictRow> rows = verdictService.compute(planId).rows();
        assertThat(rows.get(0).status()).isEqualTo(PlanVerdictService.VerdictStatus.INDETERMINATE);
        assertThat(rows.get(0).reason()).contains("多个场景");
        assertThat(rows.get(1).status()).isEqualTo(PlanVerdictService.VerdictStatus.INDETERMINATE);
        assertThat(rows.get(1).reason()).contains("未匹配");
        assertThat(rows.get(2).status()).isEqualTo(PlanVerdictService.VerdictStatus.INDETERMINATE);
        assertThat(rows.get(2).reason()).contains("无执行");
        assertThat(verdictService.compute(planId).overall()).isEqualTo("INDETERMINATE");
    }

    @Test
    void peakConcurrencyCapacityOtherAreIndeterminateWithReason() {
        long sid = scenario("登录场景");
        finishedExecution(sid, summary(600, 100, 400, 0.1), List.of());
        doc("""
                | 对象 | 指标 | 目标值 | 口径 |
                |---|---|---|---|
                | 登录场景 | 并发峰值 | ≥ 1000 | - |
                | 登录场景 | 容量 | 1万在线用户 | - |
                | 登录场景 | GC 次数 | 少于 10 | 人工 |""");
        List<PlanVerdictService.VerdictRow> rows = verdictService.compute(planId).rows();
        assertThat(rows).allMatch(r -> r.status() == PlanVerdictService.VerdictStatus.INDETERMINATE);
        assertThat(rows.get(0).reason()).contains("不可自动判定");
    }

    @Test
    void noMetricPlanProducesNoVerdict() {
        scenario("登录场景");
        PersistentTaskPlanRecord plan = planRepository.findById(planId).orElseThrow();
        plan.updateBody("## 一、背景\n\n摸底计划\n");
        planRepository.save(plan);
        PlanVerdictService.VerdictResult result = verdictService.compute(planId);
        assertThat(result.present()).isFalse();
        assertThat(result.overall()).isEqualTo("NONE");
        assertThat(result.rows()).isEmpty();
        assertThat(result.prefillConclusion()).isNull();
    }

    @Test
    void legacyBrokenSectionDegradesToIndeterminateNotBlocking() {
        PersistentTaskPlanRecord plan = planRepository.findById(planId).orElseThrow();
        plan.updateBody("## 二、测试目的与指标\n\n| 指标名 | 数值 |\n|---|---|\n| TPS | 200 |\n");
        planRepository.save(plan);
        PlanVerdictService.VerdictResult result = verdictService.compute(planId);
        assertThat(result.present()).isFalse(); // lenient 兜底为空 → 无指标路径（保存校验上线后新文档不可能出现）
        assertThat(result.overall()).isEqualTo("NONE");
    }

    @Test
    void viewGatesByPhaseAndResetsAfterNewRevision() {
        doc("""
                | 对象 | 指标 | 目标值 | 口径 |
                |---|---|---|---|
                | 登录场景 | TPS | ≥ 500 | 口径 |""");
        PersistentTaskPlanRecord plan = planRepository.findById(planId).orElseThrow();
        plan.forceState(PlanPhase.REPORT, PlanStatus.PENDING); // 报告未生成
        planRepository.save(plan);
        PlanVerdictService.VerdictView before = verdictService.view(planId);
        assertThat(before.present()).isTrue();
        assertThat(before.available()).isFalse();
        assertThat(before.overall()).isEqualTo("NONE");
        assertThat(before.rows()).isEmpty();

        plan.forceState(PlanPhase.REPORT, PlanStatus.DONE);
        planRepository.save(plan);
        PlanVerdictService.VerdictView after = verdictService.view(planId);
        assertThat(after.available()).isTrue();
        assertThat(after.overall()).isEqualTo("INDETERMINATE"); // 有指标行、场景无执行 → 无法判定
        assertThat(after.prefillConclusion()).contains("无法判定");

        plan.forceState(PlanPhase.PUBLISH, PlanStatus.PUBLISHED);
        planRepository.save(plan);
        assertThat(verdictService.view(planId).available()).isTrue();
    }

    @Test
    void prefillTextCountsSummary() {
        long sid = scenario("登录场景");
        finishedExecution(sid, summary(623.5, 120, 912, 0.32), List.of());
        doc("""
                | 对象 | 指标 | 目标值 | 口径 |
                |---|---|---|---|
                | 登录场景 | TPS | ≥ 500 | 口径 |
                | 登录场景 | 容量 | 1万在线 | 口径 |""");
        PlanVerdictService.VerdictResult result = verdictService.compute(planId);
        assertThat(result.overall()).isEqualTo("INDETERMINATE");
        assertThat(result.prefillConclusion())
                .contains("无法判定")
                .contains("1 项需人工评估");
    }
}
```

（注：`execution.markFinished(...)` 若不存在——实施时先查 `PersistentScenarioExecutionRecord` 的公开 setter/transition 方法，用现有方法把状态置 `SUCCESS` 并设 endTime；若无任何公开途径，最小改动补一个包内可见的 `forceTerminal(ExecutionStatus, Instant)` 方法，仅供测试与运维兜底，不进业务语义。）

- [ ] **Step 2: 跑测试确认失败**

```bash
JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ ./gradlew :backend:test --tests 'com.yr.perftest.platform.task.plandoc.PlanVerdictServiceTest'
```
Expected: 编译失败（`PlanVerdictService` 不存在）。

- [ ] **Step 3: 实现判等引擎**

```java
package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.execution.TaskExecutionResult;
import com.yr.perftest.platform.task.ExecutionQueryService;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRecord;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRepository;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.PersistentTaskScenarioRecord;
import com.yr.perftest.platform.task.PersistentTaskScenarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

/**
 * 判等引擎（spec §4）：输入 = 冻结的文档指标章节（P0-1 阶段规则保证）+ 每场景最近执行，
 * 即时重算、不持久化（V8）。场景级绑定 Summary（TPS/AVG_RT/P95/ERROR_RATE），
 * 交易级绑定 AggregateRow（含 P99）；不可判类型落无法判定并附原因。
 */
@Service
public class PlanVerdictService {

    public enum VerdictStatus { ACHIEVED, MISSED, INDETERMINATE }

    public record VerdictRow(
            String objectName, String metricRaw, PlanAcceptanceParser.MetricType metricType,
            String targetRaw, Double targetValue, String actualValue, VerdictStatus status,
            String reason, Long scenarioId, Long executionId) {
    }

    /** overall ∈ PASSED|FAILED|INDETERMINATE|NONE（NONE = 无指标或不可用）。 */
    public record VerdictResult(boolean present, String overall, String prefillConclusion, List<VerdictRow> rows) {
        static final VerdictResult NONE = new VerdictResult(false, "NONE", null, List.of());
    }

    public record VerdictRowView(
            String objectName, String metricRaw, String metricType, String targetRaw,
            Double targetValue, String actualValue, String status, String reason,
            Long scenarioId, Long executionId) {
    }

    public record VerdictView(
            boolean present, boolean available, String overall,
            String prefillConclusion, List<VerdictRowView> rows) {
    }

    private final PersistentTaskPlanRepository planRepository;
    private final PersistentTaskScenarioRepository scenarioRepository;
    private final PersistentScenarioExecutionRepository executionRepository;
    private final ExecutionQueryService executionQueryService;

    public PlanVerdictService(
            PersistentTaskPlanRepository planRepository,
            PersistentTaskScenarioRepository scenarioRepository,
            PersistentScenarioExecutionRepository executionRepository,
            ExecutionQueryService executionQueryService
    ) {
        this.planRepository = planRepository;
        this.scenarioRepository = scenarioRepository;
        this.executionRepository = executionRepository;
        this.executionQueryService = executionQueryService;
    }

    @Transactional(readOnly = true)
    public VerdictResult compute(long planId) {
        PersistentTaskPlanRecord plan = planRepository.findById(planId).orElse(null);
        if (plan == null) {
            return VerdictResult.NONE;
        }
        PlanAcceptanceParser.AcceptanceSection acceptance =
                PlanAcceptanceParser.parseLeniently(plan.getBody() == null ? "" : plan.getBody());
        if (!acceptance.present()) {
            return VerdictResult.NONE;
        }
        List<PersistentTaskScenarioRecord> scenarios =
                scenarioRepository.findAllByPlanIdOrderBySortOrderAscIdAsc(planId);
        List<ScenarioData> data = new ArrayList<>();
        for (PersistentTaskScenarioRecord scenario : scenarios) {
            PersistentScenarioExecutionRecord latest =
                    executionRepository.findFirstByScenarioIdOrderByIdDesc(scenario.getId()).orElse(null);
            TaskExecutionResult result = latest == null ? null : executionQueryService.getResult(latest.getId());
            data.add(new ScenarioData(scenario.getId(), scenario.getName(), latest, result));
        }
        List<VerdictRow> rows = acceptance.rows().stream()
                .map(metric -> judge(metric, data))
                .toList();
        return new VerdictResult(true, overallOf(rows), prefillText(rows), rows);
    }

    @Transactional(readOnly = true)
    public VerdictView view(long planId) {
        PersistentTaskPlanRecord plan = planRepository.findById(planId).orElse(null);
        if (plan == null) {
            throw new PlanValidationException("PLAN_INVALID：task plan does not exist");
        }
        boolean available = (plan.getPhase() == PlanPhase.REPORT && plan.getStatus() == PlanStatus.DONE)
                || (plan.getPhase() == PlanPhase.PUBLISH && plan.getStatus() == PlanStatus.PUBLISHED);
        if (!available) {
            // 复测重置后返回未生成（spec §6.1），杜绝"接口算新执行、文档达成表还是旧的"
            PlanAcceptanceParser.AcceptanceSection acceptance =
                    PlanAcceptanceParser.parseLeniently(plan.getBody() == null ? "" : plan.getBody());
            return new VerdictView(acceptance.present(), false, "NONE", null, List.of());
        }
        VerdictResult result = compute(planId);
        return new VerdictView(result.present(), true, result.overall(), result.prefillConclusion(),
                result.rows().stream().map(r -> new VerdictRowView(
                        r.objectName(), r.metricRaw(), r.metricType().name(), r.targetRaw(),
                        r.targetValue(), r.actualValue(), r.status().name(), r.reason(),
                        r.scenarioId(), r.executionId())).toList());
    }

    private VerdictRow judge(PlanAcceptanceParser.AcceptanceMetricRow metric, List<ScenarioData> data) {
        // 1. 精确匹配场景名（spec §4.2）
        ScenarioData scenarioHit = data.stream()
                .filter(d -> d.scenarioName().equals(metric.objectName()))
                .findFirst().orElse(null);
        if (scenarioHit != null) {
            return judgeScenarioLevel(metric, scenarioHit);
        }
        // 2. 精确匹配交易名 label
        List<ScenarioData> labelHits = data.stream()
                .filter(d -> d.result() != null && d.result().aggregateRows() != null
                        && d.result().aggregateRows().stream()
                        .anyMatch(r -> r.label().equals(metric.objectName())))
                .toList();
        if (labelHits.size() > 1) {
            return indeterminate(metric, null, null,
                    "同名交易出现在多个场景，请改用场景名对象或拆分场景");
        }
        if (labelHits.isEmpty()) {
            return indeterminate(metric, null, null, "未匹配对象（对象需为场景名或交易名）");
        }
        ScenarioData hit = labelHits.get(0);
        if (hit.execution() == null) {
            return indeterminate(metric, hit.scenarioId(), null, "场景无执行");
        }
        return judgeTransactionLevel(metric, hit);
    }

    private VerdictRow judgeScenarioLevel(PlanAcceptanceParser.AcceptanceMetricRow metric, ScenarioData hit) {
        if (hit.execution() == null || hit.result() == null || hit.result().summary() == null) {
            return indeterminate(metric, hit.scenarioId(), null, "场景无执行");
        }
        TaskExecutionResult.Summary summary = hit.result().summary();
        return switch (metric.metricType()) {
            case TPS -> compare(metric, hit, summary.throughput(), summary.throughput() >= metric.targetValue(),
                    String.format(Locale.ROOT, "%.1f", summary.throughput()), "取自场景最近执行");
            case AVG_RT -> compare(metric, hit, (double) summary.avgRt(), summary.avgRt() <= metric.targetValue(),
                    summary.avgRt() + "ms", "取自场景最近执行");
            case P95 -> compare(metric, hit, (double) summary.p95(), summary.p95() <= metric.targetValue(),
                    summary.p95() + "ms", "取自场景最近执行");
            case ERROR_RATE -> compare(metric, hit, summary.errorRate(), summary.errorRate() <= metric.targetValue(),
                    String.format(Locale.ROOT, "%.2f%%", summary.errorRate()), "取自场景最近执行");
            case P99 -> indeterminate(metric, hit.scenarioId(), hit.execution().getId(),
                    "场景级无 P99 数据，请改用交易级对象");
            default -> indeterminate(metric, hit.scenarioId(), hit.execution().getId(),
                    "该指标当前不可自动判定，需人工评估");
        };
    }

    private VerdictRow judgeTransactionLevel(PlanAcceptanceParser.AcceptanceMetricRow metric, ScenarioData hit) {
        TaskExecutionResult.AggregateRow row = hit.result().aggregateRows().stream()
                .filter(r -> r.label().equals(metric.objectName()))
                .findFirst().orElseThrow();
        return switch (metric.metricType()) {
            case TPS -> compare(metric, hit, row.throughput(), row.throughput() >= metric.targetValue(),
                    String.format(Locale.ROOT, "%.1f", row.throughput()), "取自最近执行 label 汇总");
            case AVG_RT -> compare(metric, hit, (double) row.average(), row.average() <= metric.targetValue(),
                    row.average() + "ms", "取自最近执行 label 汇总");
            case P95 -> compare(metric, hit, (double) row.p95(), row.p95() <= metric.targetValue(),
                    row.p95() + "ms", "取自最近执行 label 汇总");
            case P99 -> compare(metric, hit, (double) row.p99(), row.p99() <= metric.targetValue(),
                    row.p99() + "ms", "取自最近执行 label 汇总");
            case ERROR_RATE -> compare(metric, hit, row.errorRate(), row.errorRate() <= metric.targetValue(),
                    String.format(Locale.ROOT, "%.2f%%", row.errorRate()), "取自最近执行 label 汇总");
            default -> indeterminate(metric, hit.scenarioId(), hit.execution().getId(),
                    "该指标当前不可自动判定，需人工评估");
        };
    }

    private VerdictRow compare(PlanAcceptanceParser.AcceptanceMetricRow metric, ScenarioData hit,
                               double actual, boolean achieved, String actualText, String note) {
        return new VerdictRow(metric.objectName(), metric.metricRaw(), metric.metricType(),
                metric.targetRaw(), metric.targetValue(), actualText,
                achieved ? VerdictStatus.ACHIEVED : VerdictStatus.MISSED,
                achieved ? note : note, hit.scenarioId(), hit.execution().getId());
    }

    private VerdictRow indeterminate(PlanAcceptanceParser.AcceptanceMetricRow metric,
                                     Long scenarioId, Long executionId, String reason) {
        return new VerdictRow(metric.objectName(), metric.metricRaw(), metric.metricType(),
                metric.targetRaw(), metric.targetValue(), "—", VerdictStatus.INDETERMINATE,
                reason, scenarioId, executionId);
    }

    private String overallOf(List<VerdictRow> rows) {
        boolean anyMissed = rows.stream().anyMatch(r -> r.status() == VerdictStatus.MISSED);
        if (anyMissed) {
            return "FAILED";
        }
        return rows.stream().allMatch(r -> r.status() == VerdictStatus.ACHIEVED) ? "PASSED" : "INDETERMINATE";
    }

    /** 自动判定文本：达成表尾行与发布预填共用（spec §5/§6.1）。 */
    private String prefillText(List<VerdictRow> rows) {
        long missed = rows.stream().filter(r -> r.status() == VerdictStatus.MISSED).count();
        long indeterminate = rows.stream().filter(r -> r.status() == VerdictStatus.INDETERMINATE).count();
        String needHuman = indeterminate > 0 ? "、" + indeterminate + " 项需人工评估" : "";
        if (missed > 0) {
            StringJoiner joiner = new StringJoiner("、");
            rows.stream().filter(r -> r.status() == VerdictStatus.MISSED)
                    .forEach(r -> joiner.add(r.objectName() + " " + r.metricRaw()));
            return "自动判定：未达成（" + missed + " 项未达标" + needHuman + "）——" + joiner;
        }
        if (indeterminate > 0) {
            return "自动判定：无法判定（部分需人工评估）（" + (rows.size() - indeterminate) + " 项达标、"
                    + indeterminate + " 项需人工评估）";
        }
        return "自动判定：达成（" + rows.size() + " 项全部达标）";
    }

    private record ScenarioData(
            Long scenarioId, String scenarioName,
            PersistentScenarioExecutionRecord execution, TaskExecutionResult result) {
    }
}
```

要点：`metric.targetValue()` 在 TPS/AVG_RT/P95/P99/ERROR_RATE 分支必非 null（解析器保证），直接拆箱。

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2 命令（含 Task 1 追加的 lenient 用例一起跑）。Expected: 全 PASS。

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanAcceptanceParser.java backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanVerdictService.java backend/src/test/java/com/yr/perftest/platform/task/plandoc/
git commit -m "feat：P0-3 判等引擎——场景级(Summary)/交易级(AggregateRow 含 P99)两层判定、三态行状态+计划级聚合、自动判定文本、verdict 只读视图阶段门槛与复测重置"
```

---

### Task 4: generateReport 达成表幂等重绘

**Files:**
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanWorkflowService.java`（`generateReport` 约 477–487 行 + 新增 `upsertVerdictTable`；`fillConclusionActualColumn` 实际列定位按表头自适应）
- Test: `backend/src/test/java/com/yr/perftest/platform/task/plandoc/PlanVerdictReportTest.java`（新建）

**Interfaces:**
- Consumes: Task 3 `PlanVerdictService.compute(long planId)` → `VerdictResult(present, overall, prefillConclusion, rows)`；Task 1 parser。
- Produces: `generateReport` 行为契约——有指标：`<!-- backfill:verdict -->` 块重绘（对象/指标/目标/实际/状态/说明 六列 + 自动判定尾行），**不**再跑 `fillConclusionActualColumn`；无指标：P0-1 现状不回归；重复生成块替换不叠加；系统回填 revision+1 不校验 baseRevision（`plan.updateBody` 既有语义，无需改）。

- [ ] **Step 1: 写失败测试**

```java
package com.yr.perftest.platform.task.plandoc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.execution.ExecutionStatus;
import com.yr.perftest.platform.execution.TaskExecutionResult;
import com.yr.perftest.platform.execution.aggregate.PersistentAggregateReportRecord;
import com.yr.perftest.platform.execution.aggregate.PersistentAggregateReportRepository;
import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.identity.SystemRole;
import com.yr.perftest.platform.project.PersistentProjectMemberRecord;
import com.yr.perftest.platform.project.PersistentProjectMemberRepository;
import com.yr.perftest.platform.project.PersistentProjectRecord;
import com.yr.perftest.platform.project.PersistentProjectRepository;
import com.yr.perftest.platform.project.ProjectRole;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRecord;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRepository;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.PersistentTaskScenarioRecord;
import com.yr.perftest.platform.task.PersistentTaskScenarioRepository;
import com.yr.perftest.platform.task.TaskPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-verdict-report-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PlanVerdictReportTest {

    private static final HumanPrincipal OWNER = new HumanPrincipal("owner", Set.of(SystemRole.PROJECT_MEMBER));

    @Autowired
    private PlanWorkflowService workflow;
    @Autowired
    private PersistentTaskPlanRepository planRepository;
    @Autowired
    private PersistentTaskScenarioRepository scenarioRepository;
    @Autowired
    private PersistentScenarioExecutionRepository executionRepository;
    @Autowired
    private PersistentAggregateReportRepository aggregateRepository;
    @Autowired
    private PersistentProjectRepository projectRepository;
    @Autowired
    private PersistentProjectMemberRepository memberRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private long planId;

    @BeforeEach
    void setUp() {
        PersistentProjectRecord project = projectRepository.save(new PersistentProjectRecord("P1", "项目一", "", "owner"));
        memberRepository.save(new PersistentProjectMemberRecord(project.getId(), "owner", ProjectRole.OWNER));
        PersistentTaskPlanRecord plan = planRepository.save(
                new PersistentTaskPlanRecord(project.getId(), "计划", null, "owner"));
        planId = plan.getId();
    }

    private void docWithMetrics(String conclusionSection) {
        PersistentTaskPlanRecord plan = planRepository.findById(planId).orElseThrow();
        plan.updateBody("""
                ## 二、测试目的与指标

                | 对象 | 指标 | 目标值 | 口径 |
                |---|---|---|---|
                | 登录场景 | TPS | ≥ 700 | 5 分钟均值 |

                ## 七、场景设计

                ### S1 登录场景 · SINGLE_TXN

                #### 执行记录

                ## 十一、结论

                """ + conclusionSection);
        planRepository.save(plan);
    }

    private void finishedExecution(String scenarioName) {
        long sid = scenarioRepository.save(new PersistentTaskScenarioRecord(planId, null, scenarioName, 1)).getId();
        PersistentScenarioExecutionRecord execution =
                executionRepository.save(new PersistentScenarioExecutionRecord(sid, "{\"threads\":50}"));
        execution.markFinished(ExecutionStatus.SUCCESS, Instant.now()); // 同 Task 3 备注：用现有/新增的终态方法
        executionRepository.save(execution);
        try {
            TaskExecutionResult.Summary summary = new TaskExecutionResult.Summary(1000, 623.5, 120, 912, 0.32, "final");
            aggregateRepository.save(new PersistentAggregateReportRecord(
                    execution.getId(), "final", 0L, 600_000L, 600d,
                    objectMapper.writeValueAsString(summary),
                    objectMapper.writeValueAsString(List.<TaskExecutionResult.AggregateRow>of()),
                    new byte[0], Instant.now(), "test"));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void reportWithMetricsRedrawsVerdictTableAndSkipsLegacyFill() {
        docWithMetrics("""
                ### 指标达成表

                | 指标 | 目标 | 实际结果 | 状态 |
                |---|---|---|---|
                | 登录 TPS | ≥ 200 | 待执行 | 待判定 |

                ### 风险与建议

                （发布前填写。）

                **总体结论**：（发布时填写）
                """);
        finishedExecution("登录场景");
        forceReportPhase();

        TaskPlan report = workflow.generateReport(planId, OWNER);
        assertThat(report.status()).isEqualTo(PlanStatus.DONE);
        String body = planRepository.findById(planId).orElseThrow().getBody();
        assertThat(body).contains("<!-- backfill:verdict -->");
        assertThat(body).contains("#### 指标达成表（生成于");
        assertThat(body).contains("| 登录场景 | TPS | ≥ 700 | 623.5 | 未达成 |"); // 数值实际列
        assertThat(body).contains("- 自动判定：未达成（1 项未达标）——登录场景 TPS");
        assertThat(body).doesNotContain("| 登录 TPS | ≥ 200 | 待执行 | 待判定 |"); // 旧占位表被重绘
        assertThat(body).doesNotContain("待执行"); // 有指标路径不再跑 fillConclusionActualColumn 场景摘要
        assertThat(body).contains("### 风险与建议"); // 块外内容保留
        assertThat(body).contains("**总体结论**：（发布时填写）"); // 总体结论不预写（V7）
    }

    @Test
    void regenerateIsIdempotentBlockReplace() {
        docWithMetrics("""
                ### 指标达成表

                | 指标 | 目标 | 实际结果 | 状态 |
                |---|---|---|---|
                | 登录 TPS | ≥ 200 | 待执行 | 待判定 |
                """);
        finishedExecution("登录场景");
        forceReportPhase();
        workflow.generateReport(planId, OWNER);
        int revisionAfterFirst = planRepository.findById(planId).orElseThrow().getRevision();
        workflow.generateReport(planId, OWNER);
        String body = planRepository.findById(planId).orElseThrow().getBody();
        assertThat(body.split("<!-- backfill:verdict -->", -1).length - 1).isEqualTo(1);
        assertThat(body.split("#### 指标达成表（生成于", -1).length - 1).isEqualTo(1);
        assertThat(planRepository.findById(planId).orElseThrow().getRevision())
                .isEqualTo(revisionAfterFirst + 1); // 系统回填每次 revision+1
    }

    @Test
    void reportWithoutMetricsKeepsP0ZeroOneBehaviour() {
        PersistentTaskPlanRecord plan = planRepository.findById(planId).orElseThrow();
        plan.updateBody("""
                ## 七、场景设计

                ### S1 登录场景 · SINGLE_TXN

                #### 执行记录

                ## 十一、结论

                ### 指标达成表

                | 指标 | 目标 | 实际结果 | 状态 |
                |---|---|---|---|
                | 登录场景 TPS | ≥ 200 | 待执行 | 待判定 |
                """);
        planRepository.save(plan);
        finishedExecution("登录场景");
        forceReportPhase();
        workflow.generateReport(planId, OWNER);
        String body = planRepository.findById(planId).orElseThrow().getBody();
        assertThat(body).doesNotContain("<!-- backfill:verdict -->"); // 无指标不重绘
        assertThat(body).contains("<!-- backfill:report -->");        // 总览照旧
        assertThat(body).contains("登录场景");                          // 实际列照 P0-1 现状填场景摘要
    }

    private void forceReportPhase() {
        PersistentTaskPlanRecord plan = planRepository.findById(planId).orElseThrow();
        plan.forceState(PlanPhase.REPORT, PlanStatus.PENDING);
        planRepository.save(plan);
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ ./gradlew :backend:test --tests 'com.yr.perftest.platform.task.plandoc.PlanVerdictReportTest'
```
Expected: `reportWithMetricsRedraws…`/`regenerate…` FAIL（无 verdict 块）。

- [ ] **Step 3: 改造 generateReport + 新增 upsertVerdictTable**

`PlanWorkflowService` 构造器注入 `PlanVerdictService verdictService`（新增一个构造参数与字段）。`generateReport` 改为：

```java
String body = plan.getBody() == null ? "" : plan.getBody();
PlanAcceptanceParser.AcceptanceSection acceptance = PlanAcceptanceParser.parseLeniently(body);
body = upsertReportOverview(body, buildScenarioOverviews(planId));
if (acceptance.present()) {
    body = upsertVerdictTable(body, verdictService.compute(planId));
} else {
    body = fillConclusionActualColumn(body, latestScenarioSummaries(planId));
}
plan.updateBody(body);
```

新增私有方法（块语义与 `upsertReportOverview` 同模式，spec §5）：

```java
/**
 * 幂等重绘达成表（spec §5/§6）：标记 `<!-- backfill:verdict -->` 之前的内容不动；
 * 无标记时替换「### 指标达成表」占位小节（标题行到下一标题行），两处皆无则追加到结论章节尾。
 * 块尾 = 其后第一个其它标题行或 `**总体结论**` 行之前（块自身 #### 指标达成表 标题除外）。
 */
private String upsertVerdictTable(String body, PlanVerdictService.VerdictResult verdict) {
    String timestamp = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(java.time.ZoneId.systemDefault()).format(Instant.now());
    StringBuilder table = new StringBuilder("| 对象 | 指标 | 目标 | 实际 | 状态 | 说明 |\n|---|---|---|---|---|---|\n");
    for (PlanVerdictService.VerdictRow row : verdict.rows()) {
        table.append("| ").append(row.objectName()).append(" | ").append(row.metricRaw())
                .append(" | ").append(row.targetRaw()).append(" | ").append(row.actualValue())
                .append(" | ").append(verdictStatusText(row.status())).append(" | ")
                .append(row.reason()).append(" |\n");
    }
    String block = "<!-- backfill:verdict -->\n#### 指标达成表（生成于 " + timestamp + "）\n\n"
            + table + "\n- " + verdict.prefillConclusion() + "\n";
    int marker = body.indexOf("<!-- backfill:verdict -->");
    if (marker >= 0) {
        return body.substring(0, marker) + block + body.substring(blockEndOf(body, marker));
    }
    int subsection = indexOfLine(body, "### 指标达成表");
    if (subsection >= 0) {
        int end = blockEndOf(body, subsection);
        return body.substring(0, subsection) + block + body.substring(end);
    }
    String conclusion = PlanMarkdownSupport.extractSection(body, "十一、结论");
    if (conclusion == null) {
        return PlanMarkdownSupport.ensureSection(body, "十一、结论", "\n" + block);
    }
    return PlanMarkdownSupport.replaceSection(body, "十一、结论", conclusion + block);
}

/** 从 startLine 起找块尾：其后第一个其它标题行（跳过本块 `#### 指标达成表`/`#### 执行结果总览`）或 `**总体结论**` 行前。 */
private int blockEndOf(String body, int startOffset) {
    int end = body.length();
    int offset = startOffset;
    for (String line : body.substring(startOffset).split("\n", -1)) {
        if (offset > startOffset && (line.startsWith("#") || line.startsWith("**总体结论**"))
                && !line.startsWith("#### 指标达成表") && !line.startsWith("#### 执行结果总览")) {
            end = offset;
            break;
        }
        offset += line.length() + 1;
    }
    return Math.min(end, body.length());
}

private int indexOfLine(String body, String exactLine) {
    int offset = 0;
    for (String line : body.split("\n", -1)) {
        if (line.trim().equals(exactLine)) {
            return offset;
        }
        offset += line.length() + 1;
    }
    return -1;
}

private String verdictStatusText(PlanVerdictService.VerdictStatus status) {
    return switch (status) {
        case ACHIEVED -> "达成";
        case MISSED -> "未达成";
        case INDETERMINATE -> "无法判定";
    };
}
```

同时把既有 `upsertReportOverview` 的块尾扫描替换为复用 `blockEndOf`（行为等价，收敛一处），并把 `fillConclusionActualColumn` 的实际列定位改为按表头自适应（新模板占位表实际列是第 4 列"实际"，旧表是第 3 列"实际结果"）：

```java
/** 达成表"实际"列自适应（spec §3.5 模板改列后兼容旧表）：表头含「实际」或「实际结果」的列下标，缺省 2。 */
private String fillConclusionActualColumn(String body, List<java.util.Map<String, String>> summaries) {
    String conclusion = PlanMarkdownSupport.extractSection(body, "十一、结论");
    if (conclusion == null) {
        return body;
    }
    StringBuilder updated = new StringBuilder();
    java.util.Set<String> knownScenarios = new java.util.HashSet<>();
    for (java.util.Map<String, String> summary : summaries) {
        knownScenarios.add(summary.get("scenarioName"));
    }
    int actualColumn = 2; // 旧占位表「实际结果」列
    for (String line : conclusion.split("\n", -1)) {
        String trimmed = line.trim();
        if (trimmed.startsWith("|") && !trimmed.startsWith("|---")) {
            String[] cells = trimmed.substring(1, trimmed.lastIndexOf('|')).split("\\|", -1);
            int actualIdx = -1;
            for (int i = 0; i < cells.length; i++) {
                String c = cells[i].trim();
                if (c.equals("实际结果") || c.equals("实际")) {
                    actualIdx = i;
                    break;
                }
            }
            if (actualIdx >= 0) { // 表头行：锁定实际列下标，原样输出
                actualColumn = actualIdx;
                updated.append(line).append('\n');
                continue;
            }
            String firstCell = cells.length > 0 ? cells[0].trim() : "";
            String matched = knownScenarios.stream().filter(firstCell::contains).findFirst().orElse(null);
            if (matched != null) {
                String summary = summaries.stream()
                        .filter(s -> s.get("scenarioName").equals(matched)).findFirst().orElseThrow().get("summary");
                line = replaceTableRowCell(line, actualColumn, summary);
            }
        }
        updated.append(line).append('\n');
    }
    return PlanMarkdownSupport.replaceSection(body, "十一、结论", updated.toString());
}
```

（保持方法名与调用点不变；行为对旧表等价——旧表头行含"实际结果"会锁定 actualColumn=2，与原硬编码一致。）

- [ ] **Step 4: 跑测试确认通过 + plandoc 全量回归**

```bash
JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ ./gradlew :backend:test --tests 'com.yr.perftest.platform.task.plandoc.*'
```
Expected: 全 PASS（含 `PlanReportPublishTest` 既有 4 用例——其文档无指标章节，走无指标路径不回归）。

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanWorkflowService.java backend/src/test/java/com/yr/perftest/platform/task/plandoc/PlanVerdictReportTest.java
git commit -m "feat：P0-3 报告链路达成表幂等重绘——有指标 upsertVerdictTable（backfill:verdict 六列表+自动判定尾行、占位小节/标记/兜底三态落点）、无指标 P0-1 现状不回归、块尾扫描收敛 blockEndOf、实际列按表头自适应"
```

---

### Task 5: verdict 只读 REST 接口

**Files:**
- Modify: `backend/src/main/java/com/yr/perftest/platform/api/PlanDocumentController.java`（新增 endpoint + record 不需要）
- Test: `backend/src/test/java/com/yr/perftest/platform/api/PlanVerdictApiTest.java`（新建，MockMvc + Bearer）

**Interfaces:**
- Consumes: Task 3 `PlanVerdictService.view(long planId)`；控制器已有 `requireMember(TaskPlan)`（读门槛与 `/report` 一致）。
- Produces: `GET /api/task-plans/{planId}/verdict` → 200 JSON `{present, available, overall, prefillConclusion, rows:[{objectName, metricRaw, metricType, targetRaw, targetValue, actualValue, status, reason, scenarioId, executionId}]}`；非项目成员 403；未登录 401。

- [ ] **Step 1: 写失败测试**（MockMvc 认证参照 `SecurityConfigurationTest`：`authTokenService.issue("admin")`）

```java
package com.yr.perftest.platform.api;

import com.yr.perftest.platform.identity.AuthTokenService;
import com.yr.perftest.platform.project.PersistentProjectMemberRecord;
import com.yr.perftest.platform.project.PersistentProjectMemberRepository;
import com.yr.perftest.platform.project.PersistentProjectRecord;
import com.yr.perftest.platform.project.PersistentProjectRepository;
import com.yr.perftest.platform.project.ProjectRole;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.plandoc.PlanPhase;
import com.yr.perftest.platform.task.plandoc.PlanStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-verdict-api-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PlanVerdictApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthTokenService authTokenService;
    @Autowired
    private PersistentProjectRepository projectRepository;
    @Autowired
    private PersistentProjectMemberRepository memberRepository;
    @Autowired
    private PersistentTaskPlanRepository planRepository;

    private long planId;
    private String token;

    @BeforeEach
    void setUp() {
        PersistentProjectRecord project = projectRepository.save(new PersistentProjectRecord("P1", "项目一", "", "admin"));
        memberRepository.save(new PersistentProjectMemberRecord(project.getId(), "admin", ProjectRole.OWNER));
        PersistentTaskPlanRecord plan = planRepository.save(
                new PersistentTaskPlanRecord(project.getId(), "计划", null, "admin"));
        plan.updateBody("""
                ## 二、测试目的与指标

                | 对象 | 指标 | 目标值 | 口径 |
                |---|---|---|---|
                | 登录场景 | TPS | ≥ 500 | 口径 |
                """);
        plan.forceState(PlanPhase.REPORT, PlanStatus.DONE);
        planId = planRepository.save(plan).getId();
        token = authTokenService.issue("admin");
    }

    @Test
    void verdictReadableByProjectMemberWhenReportDone() throws Exception {
        mockMvc.perform(get("/api/task-plans/{id}/verdict", planId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.present").value(true))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.overall").value("INDETERMINATE")) // 场景无执行
                .andExpect(jsonPath("$.rows[0].objectName").value("登录场景"))
                .andExpect(jsonPath("$.rows[0].status").value("INDETERMINATE"))
                .andExpect(jsonPath("$.rows[0].metricType").value("TPS"));
    }

    @Test
    void verdictRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/task-plans/{id}/verdict", planId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verdictDeniedForNonMember() throws Exception {
        String outsider = authTokenService.issue("nobody"); // 未注册用户签发被拒/无效则按 401 断言；注册非成员按 403
        mockMvc.perform(get("/api/task-plans/{id}/verdict", planId)
                        .header("Authorization", "Bearer " + outsider))
                .andExpect(status().is(status -> status == 403 || status == 401));
    }
}
```

（`AuthTokenService.issue` 对未知用户的行为以现有实现为准——实施者先看 `SecurityConfigurationTest` 的用法与 `AuthTokenService` 源码；若对未知用户直接抛错，则改为先通过用户注册/种子接口造非成员账号。断言语义不变：非成员拿不到判定。）

- [ ] **Step 2: 跑测试确认失败**

```bash
JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ ./gradlew :backend:test --tests 'com.yr.perftest.platform.api.PlanVerdictApiTest'
```
Expected: 404（endpoint 不存在）。

- [ ] **Step 3: 加 endpoint**

`PlanDocumentController` 构造器注入 `PlanVerdictService verdictService`，新增：

```java
/** 验收判等只读视图（spec §6.1）：即时重算不持久化，读门槛与 /report 一致（项目成员）。 */
@GetMapping("/task-plans/{planId}/verdict")
public PlanVerdictService.VerdictView verdict(@PathVariable long planId) {
    TaskPlan plan = planService.getPlan(planId);
    requireMember(plan);
    return verdictService.view(planId);
}
```

import `com.yr.perftest.platform.task.plandoc.PlanVerdictService`。

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2。Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/yr/perftest/platform/api/PlanDocumentController.java backend/src/test/java/com/yr/perftest/platform/api/PlanVerdictApiTest.java
git commit -m "feat：P0-3 verdict 只读接口——GET /task-plans/{id}/verdict 即时重算（present/available/overall/prefillConclusion/rows），项目成员读门槛与报告一致，阶段门槛复测重置不可读"
```

---

### Task 6: 内置模板 seed 微调（无生产数据，直接改 seed）

**Files:**
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanTemplateSeeder.java`（`BUILTIN_TEMPLATE`）
- Test: `backend/src/test/java/com/yr/perftest/platform/task/plandoc/PlanTemplateSeedP0ThreeTest.java`（新建，轻量断言）；另全仓 grep 旧表头引用并回归。

**Interfaces:**
- Consumes: Task 1 parser（校验新模板可解析）。
- Produces: seed 模板指标表头 `[对象|指标|目标值|口径]`；结论章节达成表占位 = 重绘后形态（六列）；`PlanTemplateSeeder` 存在即跳过逻辑不变。

- [ ] **Step 1: 写失败测试**

```java
package com.yr.perftest.platform.task.plandoc;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlanTemplateSeedP0ThreeTest {

    @Test
    void builtinTemplateUsesObjectHeaderAndRedrawShapedAchievementTable() {
        String template = PlanTemplateSeeder.BUILTIN_TEMPLATE;
        assertThat(template).contains("| 对象 | 指标 | 目标值 | 口径 |");
        assertThat(template).doesNotContain("| 交易 | 指标 | 目标值 | 口径 |");
        // 模板渲染后的文档必须能通过保存校验并解析出指标行
        String rendered = PlanMarkdownSupport.renderTemplate(template, "示例计划");
        PlanAcceptanceParser.AcceptanceSection section = PlanAcceptanceParser.parse(rendered);
        assertThat(section.present()).isTrue();
        assertThat(section.rows().get(0).objectName()).isEqualTo("（示例）查询交易");
        // 结论占位表为重绘后六列形态
        assertThat(template).contains("| 对象 | 指标 | 目标 | 实际 | 状态 | 说明 |");
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ ./gradlew :backend:test --tests 'com.yr.perftest.platform.task.plandoc.PlanTemplateSeedP0ThreeTest'
```
Expected: FAIL（模板仍是旧表头）。

- [ ] **Step 3: 改 seed**

`BUILTIN_TEMPLATE` 中（仅两处，其余原文不动）：

1. `## 二、测试目的与指标` 表：
   - `| 交易 | 指标 | 目标值 | 口径 |` → `| 对象 | 指标 | 目标值 | 口径 |`（示例行 `（示例）查询交易` 保留）；
2. `## 十一、结论` 的 `### 指标达成表` 占位替换为：

```markdown
### 指标达成表

（报告生成时按「二、测试目的与指标」自动重绘；无指标计划本表保持为实测记录。）

| 对象 | 指标 | 目标 | 实际 | 状态 | 说明 |
|---|---|---|---|---|---|
| （示例）查询交易 | TPS | ≥ 200 | 待执行 | 待判定 | |
```

（删除旧的 `| 指标 | 目标 | 实际结果 | 状态 |` 四列占位与 `| （示例）查询交易 TPS | ≥ 200 | 待执行 | 待判定 |` 行。）

- [ ] **Step 4: 全仓回归旧表头引用**

```bash
grep -rn '| 交易 | 指标' --include='*.java' --include='*.ts' --include='*.vue' backend/ frontend/ || true
JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ ./gradlew :backend:test --tests 'com.yr.perftest.platform.task.plandoc.*'
```
Expected: 无残留引用（测试夹具里的旧表头属于"存量文档向后兼容"用例，允许保留——它们测试的正是兼容性）；plandoc 全绿。

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanTemplateSeeder.java backend/src/test/java/com/yr/perftest/platform/task/plandoc/PlanTemplateSeedP0ThreeTest.java
git commit -m "feat：P0-3 内置模板 seed 微调——指标表头交易改对象、结论达成表占位更新为重绘后六列形态（spec §3.5），seed 存在即跳过逻辑不变"
```

---

### Task 7: 前端——报告 Tab 判定展示 + 发布预填 + verdict API

**Files:**
- Modify: `frontend/src/types/index.ts`（新增 `PlanVerdict`/`PlanVerdictRow` 类型）
- Modify: `frontend/src/api/plan-doc.ts`（新增 `getPlanVerdictApi`）
- Modify: `frontend/src/components/task-plans/PlanDetailReport.vue`（判定徽章 + 未达标清单 + 下钻 + 无指标提示条）
- Modify: `frontend/src/components/task-plans/PlanDetailPublish.vue`（预填 prefillConclusion）

**Interfaces:**
- Consumes: Task 5 REST `GET /api/task-plans/{planId}/verdict` 响应结构。
- Produces: `getPlanVerdictApi(planId: number): Promise<PlanVerdict>`。

- [ ] **Step 1: types（`frontend/src/types/index.ts` 追加，放在 `PrecheckRunReport` 附近）**

```typescript
export type PlanVerdictRow = {
  objectName: string;
  metricRaw: string;
  metricType: string;
  targetRaw: string;
  targetValue: number | null;
  actualValue: string | null;
  status: 'ACHIEVED' | 'MISSED' | 'INDETERMINATE';
  reason: string | null;
  scenarioId: number | null;
  executionId: number | null;
};

export type PlanVerdict = {
  present: boolean;
  available: boolean;
  overall: 'PASSED' | 'FAILED' | 'INDETERMINATE' | 'NONE';
  prefillConclusion: string | null;
  rows: PlanVerdictRow[];
};
```

- [ ] **Step 2: api（`plan-doc.ts` 追加）**

```typescript
export function getPlanVerdictApi(planId: number) {
  return request<PlanVerdict>(`/api/task-plans/${planId}/verdict`);
}
```

（types import 行补 `PlanVerdict`。）

- [ ] **Step 3: PlanDetailReport.vue——判定面板**

`<script setup>` 追加：

```typescript
import { useRouter } from 'vue-router';
import { getPlanVerdictApi } from '../../api/plan-doc';
import type { PlanVerdict, PlanVerdictRow } from '../../types';

const router = useRouter();
const verdict = ref<PlanVerdict | null>(null);

onMounted(async () => {
  const planId = props.doc.plan.value?.id;
  if (planId) verdict.value = await getPlanVerdictApi(planId).catch(() => null);
});

const overallMeta = computed(() => ({
  PASSED: { text: '达成', color: 'green' },
  FAILED: { text: '未达成', color: 'red' },
  INDETERMINATE: { text: '无法判定', color: 'orange' },
  NONE: { text: '—', color: 'default' },
} as Record<string, { text: string; color: string }>));

const missedRows = computed(() => verdict.value?.rows.filter((r) => r.status === 'MISSED') ?? []);

function drilldown(row: PlanVerdictRow) {
  const plan = props.doc.plan.value;
  if (plan && row.executionId) {
    void router.push({ name: 'project-execution-detail', params: { projectId: plan.projectId, executionId: row.executionId } });
  }
}
```

（`computed/onMounted/ref` 已在文件 import；`useRouter` 需新增 import。）

模板在 `<h3>结论章节预览</h3>` 之前插入：

```html
<h3>验收判定</h3>
<a-alert
  v-if="verdict && !verdict.present"
  type="info"
  show-icon
  message="本计划无验收指标（摸底/排查型），总体结论由人工填写。"
/>
<a-alert
  v-else-if="verdict && !verdict.available"
  type="warning"
  show-icon
  message="报告未生成，判定不可读。"
/>
<template v-else-if="verdict">
  <div class="verdict-head">
    <a-tag :color="overallMeta[verdict.overall]?.color">{{ overallMeta[verdict.overall]?.text }}</a-tag>
    <span v-if="verdict.prefillConclusion" class="verdict-prefill">{{ verdict.prefillConclusion }}</span>
  </div>
  <a-table
    v-if="missedRows.length"
    :columns="verdictColumns"
    :data-source="missedRows"
    :pagination="false"
    size="small"
    row-key="objectName"
  >
    <template #bodyCell="{ column, record }">
      <a v-if="column.key === 'drill' && record.executionId" @click="drilldown(record)">查看执行</a>
    </template>
  </a-table>
</template>
```

`verdictColumns`：

```typescript
const verdictColumns = [
  { title: '对象', dataIndex: 'objectName', key: 'objectName' },
  { title: '指标', dataIndex: 'metricRaw', key: 'metricRaw' },
  { title: '目标', dataIndex: 'targetRaw', key: 'targetRaw' },
  { title: '实际', dataIndex: 'actualValue', key: 'actualValue' },
  { title: '下钻', key: 'drill' },
];
```

`<style scoped>` 追加：`.verdict-head { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; } .verdict-prefill { color: var(--muted); }`。

- [ ] **Step 4: PlanDetailPublish.vue——预填**

`<script setup>` 追加（发布表单初始为空时预填，人可改可重写，spec §6.2）：

```typescript
import { getPlanVerdictApi } from '../../api/plan-doc';

onMounted(async () => {
  const planId = props.doc.plan.value?.id;
  if (!planId) return;
  const verdict = await getPlanVerdictApi(planId).catch(() => null);
  if (verdict?.prefillConclusion && !conclusion.value.trim()) {
    conclusion.value = verdict.prefillConclusion; // 预填可改（V7：后端不预写文档）
  }
});
```

（placeholder 改为 `总体结论（发布人确认，必填；已预填自动判定文本，可修改）`。）

- [ ] **Step 5: 构建验证**

```bash
cd frontend && npm run build
```
Expected: `vue-tsc --noEmit` 无类型错误，vite build 成功。

- [ ] **Step 6: 提交**

```bash
git add frontend/src/types/index.ts frontend/src/api/plan-doc.ts frontend/src/components/task-plans/PlanDetailReport.vue frontend/src/components/task-plans/PlanDetailPublish.vue
git commit -m "feat：P0-3 前端判定展示——报告 Tab 总体判定徽章+未达标行清单+执行详情下钻+无指标提示条，发布表单预填 prefillConclusion（可改），plan-doc API 扩 verdict 类型与调用"
```

---

### Task 8: 全量回归与收尾验证

**Files:** 无新文件（验证任务）。

- [ ] **Step 1: 后端全量测试**

```bash
JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ ./gradlew :backend:test
```
Expected: BUILD SUCCESSFUL，0 failed（既有 ~75 个测试类 + 新增 6 个测试类全绿）。

- [ ] **Step 2: 前端构建复跑**

```bash
cd frontend && npm run build
```
Expected: 成功。

- [ ] **Step 3: 冒烟核对 spec §9 端到端两条（代码级走查）**

核对（读代码/测试断言，不需要起服务）：
1. 有指标链路：指标解析（保存校验通过）→ 执行终态 → generateReport 重绘达成表（未达标行+自动判定行）→ verdict 接口 available=true → publish 必填结论（预填仅为前端行为，后端 `publish` 结论必填校验不变）。
2. 无指标链路：文档无指标章节 → 保存 200 → generateReport 只走总览+实测记录 → verdict present=false → publish 人工结论正常。

任一不成立 → 回到对应任务修。

- [ ] **Step 4: 提交剩余变更（如有）并汇总**

```bash
git status --short   # 确认无遗漏文件（report-prototype.html 为无关未跟踪文件，不提交）
```

---

## Self-Review 记录

- **Spec 覆盖**：§3.1 保存校验（Task 2）、§3.2 冻结/兜底（Task 3 parseLeniently + view 门槛）、§3.3 位置容错（extractSection 既有）、§3.4 别名（Task 1）、§3.5 模板微调（Task 6，前端无指标表单实体已在现状锚点说明）、§4 判等引擎（Task 3）、§5 报告重绘（Task 4）、§6.1 接口（Task 5）、§6.2 前端（Task 7）、§7 边界（无新 DDL/MCP 工具/新类全在 plandoc）、§9 测试计划逐条映射到各测试类。P0-1 模板接缝保留（全局约束末条）。
- **已知裁量**（实施者照此执行，最终报告复核）：
  1. verdict 响应不另加顶层 reason 字段——available=false 用 overall=NONE 表达，前端按"报告未生成"提示（spec §6.1 响应结构为准）。
  2. 存量非法指标章节的报告期兜底 = parseLeniently 落无指标路径（spec §3.2"降级为无法判定并标注原因"以"判等不阻断、达成表不重绘"落地；严格校验上线后新文档不可能进入该分支）。
  3. `fillConclusionActualColumn` 实际列按表头自适应（新六列占位表实际列 index 3，旧表 index 2）——这是模板改列后无指标路径正确性的必要适配，非顺手重构。
- **类型一致性**：`AcceptanceSection/AcceptanceMetricRow/MetricType`（Task 1）→ Task 3/4/6 引用一致；`VerdictResult/VerdictRow/VerdictView`（Task 3）→ Task 4/5 与前端 JSON 字段一致（camelCase 由 record 序列化天然保证）；`parseLeniently` 在 Task 3 Step 0 先行补齐。
