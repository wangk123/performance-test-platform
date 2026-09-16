# P1-5 / P1-7 脚本模块功能完善 · 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付 P1-5（项目级 CSV 数据文件资产 + 场景绑定 + 多节点分发）与 P1-7（JSR223 前后置处理器 + 片段库 + `${__P()}` 属性透传），修复 worker 只收 `.jar` 与属性丢弃两个执行链路断点。

**Architecture:** 数据文件走「项目级实体（data_files/data_file_versions）+ 场景 JSON 绑定（stepId→dataFileId，仿线程组预设）+ 装配期复制进执行目录 + 依赖收集器无感分发」；JSR223 复用已预留步骤枚举，补 renderer/parser/DOM 对称实现，内联 Groovy 随 JMX 经 RMI 天然双端生效；场景属性经 payload → controller `-J`/`-G` 注入。

**Tech Stack:** Spring Boot 3 / JPA / Flyway / H2(test) · Vue3 + ant-design-vue + CodeMirror 6 · Python remote-runner (paramiko)

**Spec:** `docs/superpowers/specs/2026-09-16-p1-5-p1-7-script-module-design.md`（本计划从该文档推导，执行者需同时阅读）
原型：`prototype-assets/script-module/script-module-prototype.html`

## Global Constraints

- Java 17：`JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/`，所有 gradlew 命令前缀该环境变量。
- 后端测试惯例：**纯静态测试类**（`static void runAll()` 聚合）注册进 `backend/src/test/java/com/yr/perftest/platform/TestRunner.java`，由 JUnit 的 `BackendCoreBehaviorTest` 驱动；需要 Spring 上下文的测试仿 `CaptureAnalysisExecutionTest` 的 `@SpringBootTest` 风格。默认 `./gradlew :backend:test` 跑 H2；MySQL 专用用例打 `@Tag("mysql")` 只进 `testMysql`，本期无需。
- 唯一允许新增依赖：`@codemirror/lang-groovy`（用户 2026-09-16 批准）。其余零新增。
- 行数上限：后端模块 / 前端组件 500 行；按职责拆分。
- 脚本装配唯一 seam：`ExecutionScriptAssembler`（CONTEXT.md 既有决策，不得在 runner 直接改 JMX）。
- 交付物验收以 spec §5 测试口径为准；commit 格式 `<type>：<描述>`。
- 前端验证：`cd frontend && npm run build`（含 vue-tsc 类型检查）必须通过；`npm run test`（vitest）不回归。
- 后端基线：改动前后 `JAVA_HOME=... ./gradlew :backend:test` 全绿（当前基线 454 用例）。

**阶段独立性：Phase A（P1-5，Task 1–11）与 Phase B（P1-7，Task 12–17）互不依赖，可并行或任意顺序执行。**

---

## Phase A · P1-5 CSV 测试数据管理

### Task 1: V10 迁移 + 数据文件实体与仓库

**Files:**
- Create: `backend/src/main/resources/db/migration/V10__data_files.sql`
- Create: `backend/src/main/java/com/yr/perftest/platform/datafile/PersistentDataFileRecord.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/datafile/PersistentDataFileVersionRecord.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/datafile/DataFileRepository.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/datafile/DataFileVersionRepository.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/task/ScenarioDataFileBinding.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/PersistentTaskScenarioRecord.java`（加列字段）

**Interfaces:**
- Produces: 表 `data_files`/`data_file_versions`、`task_scenarios.data_file_bindings_json`；实体与仓库供 Task 2/5/6 使用；record `ScenarioDataFileBinding(String stepId, String stepName, Long dataFileId)`。

- [ ] **Step 1: 写迁移 SQL**

```sql
-- V10__data_files.sql
CREATE TABLE data_files (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id  BIGINT NOT NULL,
  name        VARCHAR(200) NOT NULL,
  remark      VARCHAR(500) NULL,
  created_by  VARCHAR(64) NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_data_file_name UNIQUE (project_id, name)
);

CREATE TABLE data_file_versions (
  id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
  data_file_id         BIGINT NOT NULL,
  version_no           INT NOT NULL,
  original_filename    VARCHAR(255) NOT NULL,
  stored_path          VARCHAR(500) NOT NULL,
  size_bytes           BIGINT NOT NULL,
  row_count            BIGINT NULL,
  header_columns_json  TEXT NULL,
  sha256               CHAR(64) NOT NULL,
  uploaded_by          VARCHAR(64) NULL,
  uploaded_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  remark               VARCHAR(500) NULL,
  CONSTRAINT uk_data_file_version UNIQUE (data_file_id, version_no)
);

ALTER TABLE task_scenarios ADD COLUMN data_file_bindings_json TEXT NULL;
```

- [ ] **Step 2: 写实体（JPA record 模式，仿 `PersistentScriptVersionRecord`：@Entity/@Table/@Id/@GeneratedValue，LocalDateTime 字段）**

`PersistentDataFileRecord` 映射 `data_files` 全列；`PersistentDataFileVersionRecord` 映射 `data_file_versions` 全列（`header_columns_json` 存 String，JSON 编解码在服务层做）。

- [ ] **Step 3: 写仓库接口**

```java
public interface DataFileRepository extends JpaRepository<PersistentDataFileRecord, Long> {
    Optional<PersistentDataFileRecord> findByProjectIdAndName(long projectId, String name);
    List<PersistentDataFileRecord> findAllByProjectIdOrderByIdAsc(long projectId);
}

public interface DataFileVersionRepository extends JpaRepository<PersistentDataFileVersionRecord, Long> {
    List<PersistentDataFileVersionRecord> findAllByDataFileIdOrderByVersionNoDesc(long dataFileId);
    Optional<PersistentDataFileVersionRecord> findByDataFileIdAndVersionNo(long dataFileId, int versionNo);
    List<PersistentDataFileVersionRecord> findAllByOriginalFilename(String originalFilename);
}
```

- [ ] **Step 4: 写绑定 record 与场景实体扩展**

```java
public record ScenarioDataFileBinding(String stepId, String stepName, Long dataFileId) {}
```

`PersistentTaskScenarioRecord` 仿 `threadGroupConfigsJson` 字段全套（列映射 + getter/setter + `updateProfile` 追加可空参数 + 构造默认 `"[]"`），字段名 `dataFileBindingsJson`。

- [ ] **Step 5: 编译 + 全量测试（实体由 Flyway 在测试库建表，Task 2 的服务测试验证行为）**

Run: `JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ ./gradlew :backend:test`
Expected: 全绿（无行为变化）。

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/db/migration/V10__data_files.sql backend/src/main/java/com/yr/perftest/platform/datafile/ backend/src/main/java/com/yr/perftest/platform/task/ScenarioDataFileBinding.java backend/src/main/java/com/yr/perftest/platform/task/PersistentTaskScenarioRecord.java
git commit -m "feat：P1-5 数据文件表与实体——data_files/data_file_versions 迁移、场景 data_file_bindings 列与绑定 record"
```

---

### Task 2: DataFileService（上传/列表/版本/预览/下载/删除）

**Files:**
- Create: `backend/src/main/java/com/yr/perftest/platform/datafile/DataFileService.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/datafile/DataFile.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/datafile/DataFileVersion.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/datafile/DataFileVersionDetail.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/datafile/DataFileValidationException.java`
- Test: `backend/src/test/java/com/yr/perftest/platform/datafile/DataFileServiceTest.java`
- Modify: `backend/src/test/java/com/yr/perftest/platform/TestRunner.java`（注册）
- Modify: `backend/src/main/resources/application.yml`（`platform.datafile.max-size`，默认 104857600）

**Interfaces:**
- Produces:
  - `DataFileService.upload(long projectId, String name, MultipartFile file, boolean hasHeader, String encoding, String remark, String uploadedBy) -> DataFileVersion`（name 已存在则追加版本）
  - `list(long projectId) -> List<DataFile>`；`versions(long projectId, long dataFileId) -> List<DataFileVersion>`
  - `detail(long projectId, long dataFileId, int versionNo) -> DataFileVersionDetail`（含前 50 行预览）
  - `version(long projectId, long dataFileId, int versionNo) -> DataFileVersion`（下载/装配用，暴露 storedPath）
  - `latestVersion(long dataFileId) -> DataFileVersion`（Task 6 装配用）
  - `findByOriginalFilename(String) -> List<DataFileVersion>`（Task 6 裸名兜底用）
  - `delete(long projectId, long dataFileId) -> void`（记录 + 文件级联硬删）
  - record `DataFile(long id, long projectId, String name, String remark, String createdBy, LocalDateTime createdAt, DataFileVersion latestVersion)`
  - record `DataFileVersion(long id, long dataFileId, int versionNo, String originalFilename, String storedPath, long sizeBytes, Long rowCount, List<String> headerColumns, String sha256, String uploadedBy, LocalDateTime uploadedAt, String remark)`
  - record `DataFileVersionDetail(DataFileVersion version, List<List<String>> previewRows)`

- [ ] **Step 1: 写失败测试（@SpringBootTest + H2 + @TempDir 存储，仿 CaptureAnalysisExecutionTest 注解风格）**

```java
@SpringBootTest(properties = {"platform.datafile.max-size=1000"})
class DataFileServiceTest {
    static Path tempRoot;
    @BeforeAll static void init() throws IOException { tempRoot = Files.createTempDirectory("datafiles"); }
    @DynamicPropertySource static void props(DynamicPropertyRegistry r) { r.add("platform.storage.root", () -> tempRoot.toString()); }
    @Autowired DataFileService service;

    static void runAll() throws Exception {
        init(); // 由 TestRunner 环境 spring 上下文承载时改为实例方法，见下
    }
    // —— 正式实现为实例方法，runAll 聚合调用，以下为用例清单 ——
    @Test void uploadCreatesV1WithHeaderRowsAndSha256() { /* name=用户数据, 3行CSV("mobile,name\n138,张三\n139,李四")
        断言 versionNo=1、rowCount=3、headerColumns=[mobile,name]、sha256 非空且 64 位、文件存在于 tempRoot/datafiles/{pid}/df{id}/v1-users.csv */ }
    @Test void reUploadSameNameAppendsV2() { /* 再传一次 → versionNo=2，v1 仍可 version(...,1) 取到 */ }
    @Test void oversizeRejected() { /* max-size=1000，传 >1000 字节 → DataFileValidationException 含 DATAFILE_TOO_LARGE */ }
    @Test void detailPreviewReturnsFirst50Rows() { /* 造 60 行 → previewRows.size()=50，首行=表头 */ }
    @Test void deleteRemovesRecordsAndFiles() { /* 删除后 list 不含、目录 df{id} 不存在 */ }
}
```

（用例注释即实现要求；照 JmeterScriptRendererTest 的静态聚合风格，Spring 用例以实例方法 + 一个 `@Test void all()` 调用聚合，或直接保留 @Test 注解逐个跑——两者 TestRunner 均可承载，以编译通过为准。）

- [ ] **Step 2: 跑测试确认失败**

Run: `JAVA_HOME=... ./gradlew :backend:test --tests '*DataFileServiceTest*'`
Expected: FAIL（DataFileService 不存在）。

- [ ] **Step 3: 实现 DataFileService**

要点（全部落码）：
- 构造注入两个仓库 + `@Value("${platform.storage.root:./storage}") String storageRoot` + `@Value("${platform.datafile.max-size:104857600}") long maxBytes` + Jackson `ObjectMapper`；
- `upload`：非空校验 / `file.getSize() > maxBytes` 抛 `DataFileValidationException("DATAFILE_TOO_LARGE: ...")`；`findByProjectIdAndName` 命中则 `versionNo = 该文件最大版本 + 1`，否则新建主表记录 versionNo=1；落盘路径 `storageRoot/datafiles/{projectId}/df{id}/v{n}-{sanitized(originalFilename)}`（sanitize 去路径分隔符）；流式处理 `DigestInputStream(file.getInputStream(), sha256)` 边写盘边计数 `\n`（末行无换行且非空 +1）并捕获首行字节数组；首行按 `encoding` 解码 → 逗号分隔去空白为 headerColumns（`hasHeader=false` 存 null 且 rowCount 含首行）；实体落库（headerColumns 经 ObjectMapper 序列化）→ 返回映射 record；
- `detail`：`version(...)` 后 `BufferedReader`（指定 encoding）读前 50 行逗号切分为 `previewRows`；
- `delete`：`versionRepository` 与 `fileRepository` 删除 + 递归删 `df{id}` 目录；
- 所有"项目不匹配/不存在"路径抛 `DataFileValidationException`（message 带 dataFileId）。

- [ ] **Step 4: 跑测试通过**

Run: `JAVA_HOME=... ./gradlew :backend:test --tests '*DataFileServiceTest*'` → PASS；再全量 `:backend:test` 全绿。

- [ ] **Step 5: application.yml 增加 `platform.datafile.max-size: 104857600`（platform.storage 同级）**

- [ ] **Step 6: Commit**（含 TestRunner 注册 `DataFileServiceTest` 聚合入口）

```bash
git add backend/src/main/java/com/yr/perftest/platform/datafile/ backend/src/test/java/com/yr/perftest/platform/datafile/ backend/src/test/java/com/yr/perftest/platform/TestRunner.java backend/src/main/resources/application.yml
git commit -m "feat：P1-5 数据文件服务——流式上传（sha256/表头/行数）、版本追加、预览、级联删除"
```

---

### Task 3: DataFileController + multipart 上限调整

**Files:**
- Create: `backend/src/main/java/com/yr/perftest/platform/api/DataFileController.java`
- Modify: `backend/src/main/resources/application.yml:31-36`（multipart 5MB→100MB）
- Test: `backend/src/test/java/com/yr/perftest/platform/api/DataFileControllerTest.java`（静态聚合，MockMvc 可选；最小做法 = 参数绑定/路径校验单测）

**Interfaces:**
- Consumes: Task 2 全部方法。
- Produces REST（供 Task 8 前端对接）：
  - `POST /api/projects/{projectId}/data-files`（multipart: file/name/hasHeader/encoding/remark）→ `DataFileVersion`
  - `GET /api/projects/{projectId}/data-files` → `List<DataFile>`
  - `GET .../data-files/{id}/versions` → `List<DataFileVersion>`
  - `GET .../data-files/{id}/versions/{versionNo}` → `DataFileVersionDetail`
  - `GET .../data-files/{id}/versions/{versionNo}/download` → 文件流（`Content-Disposition: attachment; filename*=UTF-8''<urlencoded originalFilename>`）
  - `DELETE .../data-files/{id}` → 204

- [ ] **Step 1: 写 controller**

```java
@RestController
@RequestMapping("/api/projects/{projectId}/data-files")
public class DataFileController {
    // POST：@RequestParam MultipartFile file + String name + boolean hasHeader(默认true)
    //      + String encoding(默认UTF-8) + String remark(可空)；调用 service.upload，DataFileValidationException
    //      → ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()))
    // GET/DELETE 逐一映射 service 方法；download 用 UrlResource + ResponseEntity.ok()
    //      .header(CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + URLEncoder.encode(...))
}
```

- [ ] **Step 2: 调整 multipart**

```yaml
  servlet:
    multipart:
      # 截图上传红线 5MB 由 PlanEvidenceImageService 自行校验保持不变；全局上限放宽给数据文件（platform.datafile.max-size 二次校验）
      max-file-size: 100MB
      max-request-size: 110MB
```

- [ ] **Step 3: 写测试（静态聚合：DataFileValidationException→400 映射、上传成功响应体字段）+ 跑绿 + 全量回归**

Run: `JAVA_HOME=... ./gradlew :backend:test`

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/yr/perftest/platform/api/DataFileController.java backend/src/main/resources/application.yml backend/src/test/java/com/yr/perftest/platform/api/DataFileControllerTest.java backend/src/test/java/com/yr/perftest/platform/TestRunner.java
git commit -m "feat：P1-5 数据文件 REST 接口——上传/列表/版本/预览/下载/删除，multipart 上限放宽至 100MB"
```

---

### Task 4: CSVDataSet 属性补齐（渲染/解析往返）

**Files:**
- Modify: `backend/src/main/java/com/yr/perftest/platform/script/JmeterScriptRenderer.java:194-202`（appendCsv）
- Modify: `backend/src/main/java/com/yr/perftest/platform/script/JmeterScriptParser.java:158-168`（parseCsv）
- Test: `backend/src/test/java/com/yr/perftest/platform/script/JmeterScriptRendererTest.java`、`JmeterScriptParserTest.java`（追加用例）

**Interfaces:**
- Produces: CSV 步骤 config 扩展键（全部可选、缺省即现状）：`delimiter`（默认 `,`）、`fileEncoding`（默认 `UTF-8`）、`ignoreFirstLine`（默认 `true`）、`recycle`（默认 `true`）、`stopThread`（默认 `false`）、`shareMode`（默认 `shareMode.all`）。JMX stringProp 名与 JMeter CSVDataSet 对齐：`delimiter`/`fileEncoding`/`ignoreFirstLine`/`recycle`/`stopThread`/`shareMode`。

- [ ] **Step 1: 追加失败测试（RendererTest：全属性 CSV 渲染含六个 stringProp；ParserTest：渲染产物解析回 config 键值相等；两者合成一个 round-trip 用例即可）**

```java
static void csvFullAttributesRoundTrip() {
    var renderer = new JmeterScriptRenderer();
    var step = new ScriptStepDefinition("csv-1", ScriptStepType.CSV_DATA.code(), "用户数据",
        Map.of("fileName","users.csv","variableNames","mobile,name","delimiter","|",
               "fileEncoding","GBK","ignoreFirstLine",false,"recycle",false,
               "stopThread",true,"shareMode","shareMode.thread"), List.of());
    var xml = renderer.renderStepFragment(step);
    var parsed = new JmeterScriptParser().parseSteps("<jmeterTestPlan>...包裹 xml...</jmeterTestPlan>");
    // 断言 parsed[0].config() 六个新键与输入相等（类型：boolean 键解析为 Boolean）
}
```

（包裹结构照既有 round-trip 用例的 TestSupport 工具。）

- [ ] **Step 2: 跑测试失败 → 实现 appendCsv/parseCsv 属性补齐（renderer：六个 `<stringProp name="...">` 逐个 append，值经既有 `xml()` 转义；parser：按 stringProp 名读取，缺省返回默认值）→ 跑绿 → 全量回归**

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/yr/perftest/platform/script/JmeterScriptRenderer.java backend/src/main/java/com/yr/perftest/platform/script/JmeterScriptParser.java backend/src/test/java/com/yr/perftest/platform/script/
git commit -m "feat：P1-5 CSVDataSet 全属性往返——delimiter/encoding/ignoreFirstLine/recycle/stopThread/shareMode"
```

---

### Task 5: 场景绑定后端（applyScenarioProfile + normalize）

**Files:**
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/ScenarioThreadGroupConfigSupport.java`（新增 normalizeDataFileBindings）
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/TaskScenarioService.java`（create/applyScenarioProfile 加参与透传）
- Test: `backend/src/test/java/com/yr/perftest/platform/task/ScenarioDataFileBindingTest.java`（静态聚合）

**Interfaces:**
- Consumes: `ScenarioDataFileBinding`（Task 1）。
- Produces:
  - `ScenarioThreadGroupConfigSupport.normalizeDataFileBindings(Path scriptPath, List<ScenarioDataFileBinding> inputs) -> List<ScenarioDataFileBinding>`：剔除 dataFileId 为空或 stepId 不在脚本 CSV 步骤集合内的项，并刷新 stepName；null/空入参返回 `List.of()`；无脚本（scriptVersionId 为空）返回空。
  - `TaskScenario.toScenario` 输出新增 `dataFileBindings`（List<ScenarioDataFileBinding>）。
  - 场景创建/更新 API 请求体新增可选字段 `dataFileBindings`（前端 Task 11 使用）。

- [ ] **Step 1: 写失败测试**

```java
static void normalizeKeepsOnlyBoundCsvSteps() {
    // 造 JMX（含两个 CSVDataSet 步骤）写临时文件；
    // 入参 [绑定A(stepId=csv-1, dataFileId=3), 绑定B(stepId=http-1, dataFileId=4), 绑定C(stepId=csv-1, dataFileId=null)]
    // 断言仅剩绑定A，且 stepName 刷新为脚本中该步骤名
}
static void normalizeEmptyWhenNoScript() { /* scriptPath 传 null 或 inputs null → List.of() */ }
```

- [ ] **Step 2: 跑失败 → 实现**

`ScenarioThreadGroupConfigSupport` 内新增：复用类内既有的脚本解析入口（该类 normalize 已按 scriptPath 解析步骤树）拿全部步骤，递归收集 `type == CSV_DATA.code()` 的 `Map<stepId, stepName>`；过滤 + 刷新 + toList。`TaskScenarioService` 的 `applyScenarioProfile`/createScenario 仿 `threadGroupConfigs` 三处：参数 → normalize → `scenario.updateProfile(...)`/`PersistentTaskScenarioRecord` 写入 `dataFileBindingsJson`（复用类内既有 JSON 读写 util）；`toScenario` 反序列化输出。

- [ ] **Step 3: 跑绿 + 全量回归**

Run: `JAVA_HOME=... ./gradlew :backend:test`

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/yr/perftest/platform/task/ backend/src/test/java/com/yr/perftest/platform/task/ScenarioDataFileBindingTest.java backend/src/test/java/com/yr/perftest/platform/TestRunner.java
git commit -m "feat：P1-5 场景 CSV 绑定——applyScenarioProfile 扩展与按脚本步骤 normalize"
```

---

### Task 6: 执行装配（DataFileAssemblyService + Assembler 扩展 + manifest）

**Files:**
- Create: `backend/src/main/java/com/yr/perftest/platform/datafile/DataFileAssemblyService.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/datafile/CsvAssemblyPlan.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/execution/distributed/ExecutionScriptAssembler.java:50-66`（prepare 签名与流程）
- Modify: `backend/src/main/java/com/yr/perftest/platform/execution/distributed/DistributedJmeterExecutionRunner.java:159,276-323,593`（preparation 记录加字段 + 传参）
- Test: `backend/src/test/java/com/yr/perftest/platform/datafile/DataFileAssemblyServiceTest.java`（静态聚合，临时 JMX + 临时文件）

**Interfaces:**
- Consumes: `DataFileService.latestVersion/findByOriginalFilename`（Task 2）、`JmeterScriptParser`、`ScenarioDataFileBinding`。
- Produces:
  - record `CsvAssemblyPlan(String stepId, String stepName, long dataFileId, int versionNo, String sha256, String targetFileName, Path sourcePath)`
  - `DataFileAssemblyService.plan(long projectId, String bindingsJson, Path scriptPath, Path executionDirectory) -> List<CsvAssemblyPlan>`：
    1. 解析脚本全部 CSV 步骤 → `Map<stepId, (stepName, fileName)>`；
    2. 绑定项（stepId 命中）：`latestVersion(dataFileId)`（不存在或不属于 project → 抛 `ExecutionValidationException("步骤 [name] 绑定的数据文件 [id] 不存在")`）；
    3. 未绑定步骤：`Files.notExists(executionDirectory/fileName)` 时按 `findByOriginalFilename(fileName)` **唯一**匹配项目版本（0 或 >1 个 → 跳过）；
    4. 全部 plan 的 `targetFileName` 去重，重复 → 抛 `ExecutionValidationException`（列出冲突步骤名与文件名）。
  - `ExecutionScriptAssembler.prepare` 新签名（新增参数 `long projectId, String storedDataFileBindingsJson`，其余不变）：在 BackendListener 注入**前**调用装配：plan → `Files.copy(plan.sourcePath(), executionDir.resolve(targetFileName), REPLACE_EXISTING)` → 写 `executionDir/data-files.json`（ObjectMapper 序列化 plan 列表）。
  - `DistributedExecutionPreparation` 记录新增 `dataFileBindingsJson` 字段；`loadPreparation` 从 `scenario.getDataFileBindingsJson()` 取值传入。

- [ ] **Step 1: 写失败测试（用例）**
  - `boundStepResolvesLatestVersionAndCopiesFile`：脚本含 csv 步骤 fileName=users.csv；dataFile 建两版；bindings=[{csv-1→df}] → plan.versionNo=2、targetFileName=users.csv； assembler 场景断言执行目录出现 users.csv + data-files.json 含该条目；
  - `missingBindingFailsWithStepName`：绑定已删 dataFileId → ExecutionValidationException 消息含步骤名；
  - `bareFilenameUniqueFallback`：无绑定、执行目录无 orders.csv、项目恰好一个同名 originalFilename → plan 生成；
  - `duplicateTargetFileNameRejected`：两个 CSV 步骤同 fileName → 异常含两个步骤名。

- [ ] **Step 2: 跑失败 → 实现 service + assembler + runner 传参 → 跑绿 + 全量回归（既有 ExecutionScriptAssembler 相关用例如受签名影响同步更新调用点）**

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/yr/perftest/platform/datafile/DataFileAssemblyService.java backend/src/main/java/com/yr/perftest/platform/datafile/CsvAssemblyPlan.java backend/src/main/java/com/yr/perftest/platform/execution/distributed/ backend/src/test/java/com/yr/perftest/platform/datafile/DataFileAssemblyServiceTest.java backend/src/test/java/com/yr/perftest/platform/TestRunner.java
git commit -m "feat：P1-5 执行装配——绑定解析/裸名兜底/冲突校验/文件复制/manifest，保持装配唯一 seam"
```

---

### Task 7: remote-runner worker 全量依赖分发 + WORKDIR 防御

**Files:**
- Modify: `remote-runner/remote_jmeter_runner/main.py:283-294,308-317,322-332,470-476`（start_worker 签名、两处 docker run、runtime_jar_sources 移除）

**Interfaces:**
- Consumes: payload `dependencies`（结构不变：`{sourcePath, targetPath}`，Java 侧 Task 6 已把 CSV 复制进执行目录、依赖收集器自然带上）。
- Produces: worker 与 controller 一致接收全部 dependencies。

- [ ] **Step 1: 改 start_worker 收全量依赖**

```python
def start_worker(node, run_id, dependencies=None):
    ...
    for dependency in dependencies or []:
        target = Path(dependency["targetPath"])
        sftp_put(client, Path(dependency["sourcePath"]), remote_dir / target)
```

调用点（launch_controller 内启动 worker 处）由 `runtime_jar_sources(payload)` 改为 `payload.get("dependencies", [])`；删除 `runtime_jar_sources` 函数；容器内 `cp /test/*.jar` 逻辑保留不动。

- [ ] **Step 2: 两处 docker run（controller 与 worker）加 `-w /test`（WORKDIR 假设的一行防御）**

- [ ] **Step 3: 语法与手工验证**

Run: `python3 -m py_compile remote-runner/remote_jmeter_runner/main.py` → 无输出。
手工（有节点环境时）：上传 CSV → 绑定场景 → 多节点执行 → 断言 controller 与全部 worker 的 `{remoteWorkDir}/{runId}/` 内均有该文件（SSH `ls`），执行成功消费数据。无节点环境则留待 Task 17 验收走查一并执行。

- [ ] **Step 4: Commit**

```bash
git add remote-runner/remote_jmeter_runner/main.py
git commit -m "fix：P1-5 worker 全量依赖分发——数据文件随 dependencies 推送全部节点，docker run 固化 /test 工作目录"
```

---

### Task 8: 前端 API 与类型

**Files:**
- Create: `frontend/src/api/data-files.ts`
- Modify: `frontend/src/types/index.ts`（DataFile/DataFileVersion/DataFileVersionDetail/ScenarioDataFileBinding/TaskScenario.dataFileBindings、CSV config 类型扩展）
- Modify: `frontend/src/api/task-plans.ts`（场景创建/更新请求体加 dataFileBindings）

**Interfaces:**
- Consumes: Task 3 REST 契约。
- Produces（Task 9/10/11 使用）:

```ts
export type DataFile = { id: number; projectId: number; name: string; remark?: string; latestVersion?: DataFileVersion };
export type DataFileVersion = { id: number; dataFileId: number; versionNo: number; originalFilename: string;
  sizeBytes: number; rowCount: number | null; headerColumns: string[] | null; sha256: string;
  uploadedBy: string; uploadedAt: string; remark?: string };
export type DataFileVersionDetail = { version: DataFileVersion; previewRows: string[][] };

export function listDataFilesApi(projectId: number): Promise<DataFile[]>
export function uploadDataFileApi(projectId: number, file: File, name: string, hasHeader: boolean, encoding: string, remark: string, username: string): Promise<DataFileVersion>  // FormData POST
export function listDataFileVersionsApi(projectId: number, dataFileId: number): Promise<DataFileVersion[]>
export function getDataFileVersionApi(projectId: number, dataFileId: number, versionNo: number): Promise<DataFileVersionDetail>
export function dataFileDownloadUrl(projectId: number, dataFileId: number, versionNo: number): string
export function deleteDataFileApi(projectId: number, dataFileId: number): Promise<void>
```

`ScriptStep` 的 CSV config 类型：`fileName/variableNames` 保持，新增可选 `delimiter?: string; fileEncoding?: string; ignoreFirstLine?: boolean; recycle?: boolean; stopThread?: boolean; shareMode?: string`。

- [ ] **Step 1: 实现 api/types（照 scripts.ts 的 axios 封装风格）→ `npm run build` 通过 → Commit**

```bash
git add frontend/src/api/data-files.ts frontend/src/api/task-plans.ts frontend/src/types/index.ts
git commit -m "feat：P1-5 前端数据文件 API 与类型契约"
```

---

### Task 9: 数据文件管理面板（脚本编辑器左栏标签页）

**Files:**
- Create: `frontend/src/components/scripts/DataFilePanel.vue`（≤500 行，超限再拆 DataFileUploadDialog.vue）
- Modify: `frontend/src/components/scripts/ScriptWorkspace.vue:120-124`（左栏 tabs「脚本 / 数据文件」）

**Interfaces:**
- Consumes: Task 8 全部 API。
- Produces: 管理面板（列表 + 上传 + 版本时间线 + 预览 + 删除），无对外接口（自包含）。

- [ ] **Step 1: 实现 DataFilePanel.vue**

要点：顶部工具行（a-input-search 搜索 + a-button primary 上传）；a-table 列 = 名称/表头（名称单元格下方 mono 小字展示 headerColumns）、最新版本 chip、大小（`(bytes/1048576).toFixed(1) MB`）、行数、更新时间、操作（版本/预览/删除 danger）；「上传」a-modal：a-upload(dragger, accept=".csv", beforeUpload 返回 false 手动提交) + name/hasHeader(a-switch)/encoding(a-select UTF-8|GBK)/remark → `uploadDataFileApi`（username 取现有用户态，照 MethodEvidenceShots.vue 的取法）；「版本」侧拉 a-drawer：时间线（v{N} 当前 chip + 元信息 + 下载链接 `dataFileDownloadUrl`）；「预览」a-modal：a-table 动态列 = version.headerColumns；删除 a-popconfirm 二次确认。上传/删除成功后刷新列表。

- [ ] **Step 2: ScriptWorkspace 左栏改 a-tabs**

两个 tab：「脚本」= 现有版本时间线内容原样移入；「数据文件」= `<DataFilePanel :project-id="projectId" />`（projectId 从现有 props/composable 取）。

- [ ] **Step 3: 验证**：`npm run build` + `npm run test` 通过；`npm run dev` 手工冒烟（上传→列表→版本→预览→删除）。

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/scripts/DataFilePanel.vue frontend/src/components/scripts/ScriptWorkspace.vue
git commit -m "feat：P1-5 数据文件管理面板——上传/版本时间线/预览/删除，脚本编辑器左栏标签页接入"
```

---

### Task 10: CSV 步骤表单升级（StepDetail）

**Files:**
- Modify: `frontend/src/components/editor/StepDetail.vue:45-60`（CSV 分支）
- Modify: `frontend/src/utils/script-steps.ts:185-225`（CSV 默认 config 补默认值）

**Interfaces:**
- Consumes: Task 8 CSV config 类型。
- Produces: CSV 步骤可视化编辑支持全属性。

- [ ] **Step 1: 扩表单**：现有 fileName/variableNames 下方加 a-collapse「高级属性」：delimiter(a-input)、fileEncoding(a-select UTF-8/GBK/ISO-8859-1)、shareMode(a-select: shareMode.all 所有线程/shareMode.thread 当前线程/shareMode.threadGroup 线程组)、ignoreFirstLine/recycle/stopThread 三个 a-switch（label 带说明文案）；表单顶部 a-alert(info) 文案：「文件名与变量在此定义；数据文件本体在 计划 → 场景 → CSV 数据文件 中绑定分发」。`script-steps.ts` 的 CSV 默认 config 补 `delimiter: ',', fileEncoding: 'UTF-8', ignoreFirstLine: true, recycle: true, stopThread: false, shareMode: 'shareMode.all'`。
- [ ] **Step 2: 验证**：`npm run build`；手工：新建 CSV 步骤改属性保存 → 重开回显一致（往返）。
- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/editor/StepDetail.vue frontend/src/utils/script-steps.ts
git commit -m "feat：P1-5 CSV 步骤表单全属性编辑与绑定指引"
```

---

### Task 11: 场景绑定 UI（ScenarioDialog CSV 区）

**Files:**
- Modify: `frontend/src/components/task-plans/ScenarioDialog.vue`
- Modify: `frontend/src/api/task-plans.ts`（如 Task 8 未覆盖提交体则此处补）

**Interfaces:**
- Consumes: `getScriptDefinitionApi(projectId, versionId)`（既有）解析 CSV 步骤；`listDataFilesApi`。
- Produces: 场景保存请求体含 `dataFileBindings: {stepId, stepName, dataFileId}[]`（仅绑定项，未绑定不入数组）。

- [ ] **Step 1: 实现绑定区**：watch `form.scriptVersionId` 变化 → `getScriptDefinitionApi` 拿步骤树 → 递归收集 `type === 'CSV_DATA'` 步骤（stepId/name/config.fileName/config.variableNames）存 `csvSteps` ref；线程组档位区下方渲染「CSV 数据文件」区块（a-table 或行卡片）：每行 = 步骤名 + fileName chip + variableNames mono 小字 + 右侧 a-select（options = 项目数据文件 `name · vN · 行数 · 大小`，allowClear；option label tooltip 展示 headerColumns）；选择写入 `form.dataFileBindings`；编辑回显：`props.editingScenario.dataFileBindings` 按 stepId 预填；行尾提示未绑定时「执行时按同名文件兜底匹配」。提交体带上 `dataFileBindings`。
- [ ] **Step 2: 验证**：`npm run build` + 手工：关联脚本后 CSV 区出现两行，绑定保存重开回显；换绑脚本后失效 stepId 消失（后端 normalize 兜底）。
- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/task-plans/ScenarioDialog.vue frontend/src/api/task-plans.ts
git commit -m "feat：P1-5 场景 CSV 绑定 UI——脚本 CSV 步骤逐步骤下拉绑定项目数据文件"
```

---

## Phase B · P1-7 JSR223 前后置处理器

### Task 12: JSR223 渲染/解析/DOM 识别

**Files:**
- Modify: `backend/src/main/java/com/yr/perftest/platform/script/JmeterScriptRenderer.java:94-104`（switch 两 case + appendJsr223）
- Modify: `backend/src/main/java/com/yr/perftest/platform/script/JmeterScriptParser.java:62-74`（switch 两 case + parseJsr223）
- Modify: `backend/src/main/java/com/yr/perftest/platform/script/JmeterScriptDom.java:27-38`（stepType 标签映射 + stepId 前缀）
- Test: `JmeterScriptRendererTest`/`JmeterScriptParserTest`/`JmeterScriptPatcherTest` 各追加用例

**Interfaces:**
- Produces: 步骤 config 契约 `scriptLanguage`(默认 `groovy`) / `script` / `parameters` / `cacheKey`(默认 `true`)；JMX 标签 `JSR223PreProcessor`/`JSR223PostProcessor`（`guiclass="TestBeanGUI"`、`testclass` 同标签），stringProp 名：`cacheKey`/`scriptLanguage`/`parameters`/`filename`(恒空)/`script`；stepId 前缀 `jsr223-pre-`/`jsr223-post-`。

- [ ] **Step 1: 写失败测试**

```java
static void jsr223RoundTripEscapesGroovy() {
    var step = new ScriptStepDefinition("jsr223-pre-1", ScriptStepType.JSR223_PRE_PROCESSOR.code(), "签名",
        Map.of("scriptLanguage","groovy","script","def s = \"a<b\" & 'c'\nif (x < 1) {}",
               "parameters","env=prod","cacheKey",true), List.of());
    var xml = new JmeterScriptRenderer().renderStepFragment(step);
    // 断言 xml 含 "&lt;" 与 "&amp;"（转义文本非 CDATA）、testclass/guiclass 正确
    var parsed = ...parse...(xml 包裹);
    // 断言四个 config 键往返相等（cacheKey 为 Boolean.TRUE；script 换行保留）
}
```

ParserTest 追加：外部 JMX 片段（beanshell）解析保留 `scriptLanguage=beanshell`；PatcherTest 追加：两步骤断言按 id 替换后 script 文本更新。

- [ ] **Step 2: 实现**

Renderer（显式拼接，不用 CDATA）：

```java
private void appendJsr223(StringBuilder builder, ScriptStepDefinition step, String tag) {
    Map<String, Object> config = step.config() == null ? Map.of() : step.config();
    String lang = str(config, "scriptLanguage", "groovy");
    String params = str(config, "parameters", "");
    String script = str(config, "script", "");
    String cacheKey = String.valueOf(bool(config, "cacheKey", true));
    builder.append("    <").append(tag).append(" guiclass=\"TestBeanGUI\" testclass=\"").append(tag)
            .append("\" testname=\"").append(xml(step.name())).append("\" enabled=\"true\">\n");
    builder.append("      <stringProp name=\"cacheKey\">").append(xml(cacheKey)).append("</stringProp>\n");
    builder.append("      <stringProp name=\"scriptLanguage\">").append(xml(lang)).append("</stringProp>\n");
    builder.append("      <stringProp name=\"parameters\">").append(xml(params)).append("</stringProp>\n");
    builder.append("      <stringProp name=\"filename\"></stringProp>\n");
    builder.append("      <stringProp name=\"script\">").append(xml(script)).append("</stringProp>\n");
    builder.append("    </").append(tag).append(">\n");
}
// str/bool：config 取值转 String/boolean，缺省给默认（照文件内既有取值辅助风格新增私有方法）
```

switch：`case JSR223_PRE_PROCESSOR -> appendJsr223(builder, step, "JSR223PreProcessor");` + POST 同理。Parser：tag case 进 `parseJsr223(element, ScriptStepType.JSR223_PRE_PROCESSOR)`，读五个 stringProp（filename 丢弃）。Dom：`stepType` 加两标签映射；stepId 前缀映射加 `jsr223-pre-`/`jsr223-post-`（照 csv- 的生成路径）。

- [ ] **Step 3: 跑绿 + 全量回归 → Commit**

```bash
git add backend/src/main/java/com/yr/perftest/platform/script/ backend/src/test/java/com/yr/perftest/platform/script/
git commit -m "feat：P1-7 JSR223 前后置渲染解析——转义文本往返、DOM 识别、patcher 按 id 替换"
```

---

### Task 13: 片段库 + 明文密钥扫描 + 保存 warnings

**Files:**
- Create: `backend/src/main/resources/jsr223-snippets.json`
- Create: `backend/src/main/java/com/yr/perftest/platform/script/Jsr223SnippetRegistry.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/api/Jsr223SnippetController.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/script/Jsr223SecretScanner.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/api/ScriptController.java:83-95`（PUT /definition 响应改 `SaveScriptDefinitionResult`）
- Create: `backend/src/main/java/com/yr/perftest/platform/script/SaveScriptDefinitionResult.java`
- Test: `backend/src/test/java/com/yr/perftest/platform/script/Jsr223SnippetTest.java`（含 scanner 用例）

**Interfaces:**
- Produces:
  - `GET /api/jsr223-snippets` → `[{key,name,category,description,params:[String],code}]`（registry 读 classpath `jsr223-snippets.json`）。
  - `Jsr223SecretScanner.scanScriptContent(String jmxContent) -> List<String>`（warning 文案：`步骤 [name] 第 N 处疑似明文密钥（secret = 'xxxx…'），建议改用 ${__P(...)} 参数化引用`）。
  - record `SaveScriptDefinitionResult(ScriptVersion version, List<String> warnings)`；PUT `/api/projects/{projectId}/scripts/{versionId}/definition` 与保存端点响应改为该 record（`warnings` 恒非空数组）。
  - regex：`(?i)(secret|password|passwd|token|appkey|api[-_]?key|salt|private[-_]?key)\s*=\s*['\"]([A-Za-z0-9+/=_-]{16,})['\"]`

- [ ] **Step 1: 写失败测试**：scanner 命中（`secret = 'a3f8d02c9e17b6f4ad55'` 在 JSR223 步骤 script 内 → 1 条 warning 含步骤名）；不命中（值 <16 位 / 变量引用 `${__P(k)}` / 非 JSR223 步骤同款文本 → 空）；registry：json 可加载、条目数 ≥5、每条 code 含 `${__P(`（密钥位占位约定）。
- [ ] **Step 2: 实现 snippets.json（六条：hmac-sha256-sign / aes256-encrypt / md5-digest / rsa-sign / uuid-timestamp / base64；category= 签名/加解密/工具；code 为完整可运行 Groovy，密钥位一律 `${__P(xxxKey)}`）+ registry（仿 JmeterFunctionRegistry 的 classpath 读取）+ controller + scanner（解析步骤树，仅扫 JSR223_PRE/POST 的 script 字段，Matcher 逐命中生成文案，`步骤名` 取 step.name()）+ ScriptController 响应包装（scanner 在 controller 调用：`new SaveScriptDefinitionResult(version, scanner.scanScriptContent(request.content()))`）**
- [ ] **Step 3: 跑绿 + 全量回归（若存在依赖旧响应体的前端/测试调用点同步适配——前端 Task 16 才改，后端测试如有 mock 该端点的同步改）→ Commit**

```bash
git add backend/src/main/resources/jsr223-snippets.json backend/src/main/java/com/yr/perftest/platform/script/ backend/src/main/java/com/yr/perftest/platform/api/Jsr223SnippetController.java backend/src/test/java/com/yr/perftest/platform/script/Jsr223SnippetTest.java backend/src/test/java/com/yr/perftest/platform/TestRunner.java
git commit -m "feat：P1-7 片段库接口与明文密钥扫描——保存响应携带 warnings 不阻断"
```

---

### Task 14: jmeterProperties 透传（payload + -J/-G）

**Files:**
- Modify: `backend/src/main/java/com/yr/perftest/platform/execution/distributed/DistributedJmeterExecutionRunner.java:325-351`（payload）
- Modify: `remote-runner/remote_jmeter_runner/main.py`（launch_controller jmeter_args）
- Test: 既有 Runner payload 相关用例追加断言（若 payload 无直接单测，则在 DataFileAssemblyServiceTest 同文件加一个最小 payload 构造用例不现实——改为：为 payload 方法提一个包内可见静态组装函数并测之，或直接在 Task 6 已动的测试文件中补断言；最简：把 `"jmeterProperties", config.jmeterProperties()` 加入 payload 后，跑全量回归 + 以下手工验证）

**Interfaces:**
- Produces: payload 新增 `jmeterProperties: Map<String,String>`；controller 命令行逐属性追加 `-J{k}={v}` 与 `-G{k}={v}`（`-G` 经 RMI 同步 worker）。

- [ ] **Step 1: Java 侧**：payload Map.of 追加 `"jmeterProperties", config.jmeterProperties() == null ? Map.of() : config.jmeterProperties()`；`ExecutionConfig.jmeterProperties` 既有 key 非空校验不变。
- [ ] **Step 2: Python 侧**（launch_controller 的 jmeter_args 构建尾部，`-JjmeterRoleHost` 之后）：

```python
for key, value in (payload.get("jmeterProperties") or {}).items():
    jmeter_args.append(shell_quote(f"-J{key}={value}"))
    jmeter_args.append(shell_quote(f"-G{key}={value}"))
```

Run: `python3 -m py_compile remote-runner/remote_jmeter_runner/main.py`；`JAVA_HOME=... ./gradlew :backend:test` 全绿。
- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/yr/perftest/platform/execution/distributed/DistributedJmeterExecutionRunner.java remote-runner/remote_jmeter_runner/main.py
git commit -m "fix：P1-7 场景属性透传——jmeterProperties 进 payload，controller 以 -J/-G 注入并 RMI 同步 worker"
```

---

### Task 15: 前端类型/创建卡片/导入/默认值

**Files:**
- Modify: `frontend/package.json`（新增 `@codemirror/lang-groovy`，用户已批准）
- Modify: `frontend/src/constants/index.ts:38-60`（stepTypeOptions/stepTypeMeta）
- Modify: `frontend/src/types/index.ts:85-92`（ScriptStepType 联合类型 + Jsr223Config）
- Modify: `frontend/src/components/editor/StepTypeIcon.vue`
- Modify: `frontend/src/utils/script-steps.ts:173-236`（默认 config）
- Modify: `frontend/src/utils/jmeter-xml-import.ts:33-41`（两标签导入）

**Interfaces:**
- Produces: `ScriptStepType` 增加 `'JSR223_PRE_PROCESSOR' | 'JSR223_POST_PROCESSOR'`；默认 config `{ scriptLanguage: 'groovy', script: '', parameters: '', cacheKey: true }`；创建卡片由 stepTypeOptions 自动出现（对话框数据驱动，无需改 StepCreateDialog）；导入映射两标签 → 新类型（五 stringProp → config，filename 丢弃）。

- [ ] **Step 1: 逐文件实现（icon 用简洁 SVG，照 StepTypeIcon 既有风格；meta 的 label=「JSR223 前置/后置处理器」、hint 说明挂载语义）→ `npm install`（新增依赖）→ `npm run build` 通过 → Commit**

```bash
git add frontend/package.json frontend/package-lock.json frontend/src/constants/index.ts frontend/src/types/index.ts frontend/src/components/editor/StepTypeIcon.vue frontend/src/utils/script-steps.ts frontend/src/utils/jmeter-xml-import.ts
git commit -m "feat：P1-7 前端类型注册——JSR223 步骤类型/图标/默认值/导入映射与 groovy 语言包"
```

---

### Task 16: JSR223 编辑表单 + 片段面板 + warnings 黄条

**Files:**
- Create: `frontend/src/components/editor/Jsr223SnippetPanel.vue`
- Modify: `frontend/src/components/editor/CodeEditor.vue`（language 联合类型加 `'groovy'` + `defineExpose({ insertAtCursor })`）
- Modify: `frontend/src/components/editor/StepDetail.vue`（JSR223 分支）
- Modify: `frontend/src/composables/useScriptEditor.ts:417-455`（保存响应 warnings 状态）
- Modify: `frontend/src/components/scripts/ScriptWorkspace.vue`（保存工具行附近黄条）
- Modify: `frontend/src/api/scripts.ts`（saveScriptContentApi 返回类型 = `{ version: BackendScriptVersion; warnings: string[] }`）

**Interfaces:**
- Consumes: Task 13 `GET /api/jsr223-snippets` 与保存响应 warnings。
- Produces: JSR223 可视化编辑（名称/语言只读 groovy（导入他语言显示原值）/parameters/脚本 CodeEditor groovy 高亮 + 行号）；片段面板按 category 分组点击插入光标处；保存后 warnings 黄条（a-alert warning，可关闭）。

- [ ] **Step 1: CodeEditor 扩展**：`import { groovy } from '@codemirror/lang-groovy'`，`languageExtension()` 加 case；新增：

```ts
function insertAtCursor(text: string) {
  if (!editorView) return;
  const pos = editorView.state.selection.main.head;
  editorView.dispatch({ changes: { from: pos, insert: text }, selection: { anchor: pos + text.length } });
}
defineExpose({ insertAtCursor });
```

- [ ] **Step 2: Jsr223SnippetPanel.vue**：props `{ onInsert: (code: string) => void }`；onMounted 拉 `/api/jsr223-snippets`，按 category 分组渲染卡片（name + description + params 提示 `${__P(...)}` 占位 + hover「插入」），点击 `emit('insert', snippet.code)`。
- [ ] **Step 3: StepDetail JSR223 分支**：表单（名称 a-input；语言：`config.scriptLanguage !== 'groovy'` 时显示 a-tag 原值 + 「切换为 groovy」按钮，否则只读 tag groovy；parameters a-input）；脚本区 `<CodeEditor ref="codeRef" language="groovy" v-model="step.config.script" />` + 右侧/下方 `<Jsr223SnippetPanel :on-insert="(c) => codeRef?.insertAtCursor('\n' + c + '\n')" />`（布局照原型 D 区：宽屏三栏/窄屏片段面板折到下方）。
- [ ] **Step 4: warnings 黄条**：`useScriptEditor` 增加 `const saveWarnings = ref<string[]>([])`，保存成功后赋值 `res.warnings ?? []`；`ScriptWorkspace` 在保存按钮区域下方 `a-alert type="warning" v-if="saveWarnings.length" :message="saveWarnings[0]" closable @close="saveWarnings = []"`。
- [ ] **Step 5: 验证**：`npm run build` + `npm run test`；手工：新建前置处理器 → 插入 HMAC 片段 → 脚本里粘 `secret = 'a3f8d02c9e17b6f4'`（19 位）保存 → 黄条出现且已保存；导出 XML 视图检查转义。
- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/editor/ frontend/src/composables/useScriptEditor.ts frontend/src/components/scripts/ScriptWorkspace.vue frontend/src/api/scripts.ts
git commit -m "feat：P1-7 JSR223 编辑器——groovy 高亮、片段插入面板、明文密钥警告黄条"
```

---

### Task 17: 端到端验收走查（两项 roadmap 口径）

**Files:** 无代码（验证任务；发现问题回改对应任务文件）

- [ ] **Step 1: P1-5 验收**：上传 CSV（>5MB 验证 multipart）→ 计划场景关联脚本并下拉绑定 → 多节点执行 → SSH 检查 controller 与全部 worker `{remoteWorkDir}/{runId}/` 均含该文件且容器内可见于 `/test` → 执行成功、variableNames 出现在请求参数（被测侧可加 echo 接口或查 JMeter 日志）；换绑脚本版本后失效绑定被剔除；同名 fileName 冲突报错信息含步骤名。
- [ ] **Step 2: P1-7 验收**：给请求加「HMAC-SHA256 签名 PreProcessor（signKey 用 `${__P(signKey)}`）」→ 场景属性配 `signKey` → 分布式执行，响应断言或 `log.info` 验证签名/属性双端（controller 与 worker）生效；导出 JMX 在本地 JMeter GUI 打开元素完整、`-JsignKey=xx` 本地跑通。
- [ ] **Step 3: 更新 `docs/architecture-and-roadmap.md`** 两行状态为 🟨（代码完成待验收）并在行内追加交付说明（照 P1-1 行格式）。
- [ ] **Step 4: Commit**

```bash
git add docs/architecture-and-roadmap.md
git commit -m "docs：P1-5/P1-7 验收走查通过，roadmap 状态更新为待人工验收"
```

---

## Self-Review 记录

- **Spec 覆盖**：§3.1 C1→Task 1/2、C2/C4→Task 5/6/11、C3 manifest→Task 6、C5→Task 7、C6→Task 2/3、C7→Task 4/10、C8 不做（无任务，符合）、C9→Task 2/6；§4.1 J1→Task 15（options 驱动 + 层级限 2 既有）、J2/J3/J4→Task 12/15/16、J5→Task 13/16、J6→Task 15、J7→Task 14、J8→Task 13/16、J9 红线→J8 覆盖、J10→无代码任务（验收 Step 2 覆盖本地 JMeter 口径）、J11 不修（无任务，符合）；§5 测试口径→各任务 Step 与 Task 17。无缺口。
- **占位符**：Task 2 Step 1 的测试用例以"断言清单注释"形式给出完整行为定义并注明聚合方式二选一（编译为准），其余步骤均含可直接落码的内容。
- **类型一致性**：`ScenarioDataFileBinding(stepId, stepName, dataFileId)`（Task 1/5/6/8/11 一致）；`CsvAssemblyPlan` 字段与 Task 6 装配/manifest 一致；`SaveScriptDefinitionResult(version, warnings)`（Task 13/16 一致）；JSR223 config 四键（Task 12/15/16 一致）。
