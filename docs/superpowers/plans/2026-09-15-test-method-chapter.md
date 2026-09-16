# 测试方法章节结构化改造 · 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 计划文档「测试方法」章节结构化：场景小节实体同步、可执行记录表（参数覆盖 + 结果自动回填）、表格下方监控证据区（执行数据图复用 + 手动截图上传）、两段式删行。

**Architecture:** 执行记录与截图为独立结构化数据（引用式，不写入 Markdown body）；执行链路复用 Scenario→triggerExecution→AggregateReport；监控图复用执行详情页组件以终态数据只读渲染；图片存 `platform.storage.root`，API 流式读取。

**Tech Stack:** Spring Boot 3 / JPA / JUnit5（后端）；Vue 3 + ant-design-vue + ECharts + vitest（前端）。

**Spec:** `docs/superpowers/specs/2026-09-15-test-method-chapter-design.md`（本计划从 spec 出发，执行者需同时阅读 spec 与原型 `prototype-assets/test-method-optimization/test-method-optimization-prototype.html`）

## Global Constraints

- 后端模块、前端组件 ≤500 行，按职责拆分；不新增第三方依赖。
- Java 17：`JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home`；后端命令在 `backend/` 目录执行 `../gradlew`（先 `ls` 确认 gradlew 在仓库根还是 backend/ 下）。
- 前端校验命令：`npm run build`（含 vue-tsc 类型检查）与 `npm run test`（vitest），在 `frontend/` 执行。
- Commit 格式：`<type>：<描述>`（技术名词保留英文），type ∈ feat|fix|refactor|perf|style|test|docs|ci|build|chore|revert。
- 不改内置模板（`PlanTemplateSeeder`）、不改「八、场景设计」模块既有行为。
- 测试风格遵循仓库现状：类级 `@Transactional` 回滚为主；断言绝对自增 ID 或依赖异步/REQUIRES_NEW 的用例按需 `@DirtiesContext`（参见近期提速 commit 8a8d104 的分类原则）。
- 所有新端点默认要求项目成员身份，鉴权模式照抄 `PlanWorkflowService.requireActor`（`PlanWorkflowService.java:626-641`）。

## 文件结构总览

后端（包根 `com.yr.perftest.platform`，目录 `backend/src/main/java/com/yr/perftest/platform/`）：

| 文件 | 职责 | 动作 |
|---|---|---|
| `task/PersistentScenarioExecutionRecord.java` | +methodHidden 列 | 修改 |
| `task/method/PersistentPlanEvidenceImageRecord.java` | 截图元数据实体 | 新建 |
| `task/method/PlanEvidenceImageRepository.java` | 截图仓库 | 新建 |
| `task/method/PlanEvidenceImageService.java` | 截图落盘/查询/删除 | 新建 |
| `task/method/MethodSectionService.java` | 章节聚合 + 可见性 | 新建 |
| `task/method/MethodSectionResponse.java` | 聚合 DTO | 新建 |
| `task/ThreadGroupOverrides.java` | 内联参数覆盖 record | 新建 |
| `task/ExecutionControlService.java` | StartCommand+overrides、requestHash | 修改 |
| `task/ExecutionConfigMerger.java` | overrides 应用 | 修改 |
| `task/ScenarioExecutionService.java` | triggerExecution 传参 | 修改 |
| `api/TaskPlanController.java` | 请求体/新端点 | 修改 |
| `task/plandoc/PlanScenarioDocSync.java` | 测试方法章节骨架同步 | 修改 |
| 既有批量删除服务（执行删除级联） | 定位后修改 | 修改 |

前端（目录 `frontend/src/`）：

| 文件 | 职责 | 动作 |
|---|---|---|
| `utils/plan-markdown.ts` | 注册「测试方法」规范标题 | 修改 |
| `components/task-plans/PlanDetailDocument.vue` | 模块挂载分支 | 修改 |
| `api/plan-method.ts` | 章节聚合/可见性/截图 API | 新建 |
| `api/task-plans.ts` | triggerExecutionApi + overrides | 修改 |
| `types/index.ts` | MethodSectionData 等类型 | 修改 |
| `components/task-plans/method/TestMethodModule.vue` | 章节容器（场景列表+轮询） | 新建 |
| `components/task-plans/method/MethodExecTable.vue` | 执行记录表+新增执行+删行弹窗 | 新建 |
| `components/task-plans/method/MethodEvidence.vue` | 监控证据区 | 新建 |

---

### Task 1: 后端实体——methodHidden 列与截图表

**Files:**
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/PersistentScenarioExecutionRecord.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/task/method/PersistentPlanEvidenceImageRecord.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/task/method/PlanEvidenceImageRepository.java`
- Test: `backend/src/test/java/com/yr/perftest/platform/task/method/PlanEvidenceImageRepositoryTest.java`

**Interfaces（Produces）:**
- `PersistentScenarioExecutionRecord.isMethodHidden()` / `setMethodHidden(boolean)`
- `PlanEvidenceImageRepository.findByScenarioIdOrderBySortOrderAscIdAsc(long)`、`findByExecutionIdIn(List<Long>)`、`deleteByExecutionIdIn(List<Long>)`、`findById`、`save`（JPA 自带）

- [ ] **Step 1: 确认 schema 管理方式**

读 `backend/src/main/resources/application.yml` 的 `jpa.hibernate.ddl-auto` 取值与 `backend/src/main/resources/` 下是否有 `schema.sql`/flyway。若是 `update`/`none`+无迁移文件，则新列/新表靠 JPA 自动演进（H2 测试库自动建表），无需 SQL 文件；若存在显式迁移文件，按其格式追加 DDL：

```sql
ALTER TABLE scenario_executions ADD COLUMN method_hidden TINYINT(1) NOT NULL DEFAULT 0;
CREATE TABLE plan_evidence_images (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  plan_id BIGINT NOT NULL,
  scenario_id BIGINT NOT NULL,
  execution_id BIGINT NULL,
  caption VARCHAR(200),
  sort_order INT NOT NULL DEFAULT 0,
  stored_path VARCHAR(500) NOT NULL,
  content_type VARCHAR(100) NOT NULL,
  size_bytes BIGINT NOT NULL,
  uploaded_by VARCHAR(100),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_evidence_scenario (scenario_id),
  KEY idx_evidence_execution (execution_id)
);
```

- [ ] **Step 2: 修改执行实体**

在 `PersistentScenarioExecutionRecord` 字段区（参照现有 `status` 等列的注解风格）追加：

```java
@Column(name = "method_hidden", nullable = false)
private boolean methodHidden = false;

public boolean isMethodHidden() { return methodHidden; }
public void setMethodHidden(boolean methodHidden) { this.methodHidden = methodHidden; }
```

- [ ] **Step 3: 新建截图实体与仓库**

`PersistentPlanEvidenceImageRecord`（表 `plan_evidence_images`，字段与上表一致，注解风格照抄 `PersistentScriptVersionRecord.java`——先读该文件对齐 `@Entity/@Table/@Column/@Lob` 用法）。仓库：

```java
public interface PlanEvidenceImageRepository extends JpaRepository<PersistentPlanEvidenceImageRecord, Long> {
    List<PersistentPlanEvidenceImageRecord> findByScenarioIdOrderBySortOrderAscIdAsc(long scenarioId);
    List<PersistentPlanEvidenceImageRecord> findByExecutionIdIn(List<Long> executionIds);
    void deleteByExecutionIdIn(List<Long> executionIds);
}
```

- [ ] **Step 4: 写仓库测试并运行**

```java
@DataJpaTest  // 若仓库既有测试不用此注解而用整上下文，照抄邻近测试类的注解风格
class PlanEvidenceImageRepositoryTest {
    @Autowired PlanEvidenceImageRepository repository;

    @Test
    void savesAndQueriesByScenarioOrdering() {
        PersistentPlanEvidenceImageRecord a = image(1L, 1L, "甲", 1);
        PersistentPlanEvidenceImageRecord b = image(1L, 1L, "乙", 0);
        repository.saveAll(List.of(a, b));
        List<PersistentPlanEvidenceImageRecord> list = repository.findByScenarioIdOrderBySortOrderAscIdAsc(1L);
        assertThat(list).extracting(PersistentPlanEvidenceImageRecord::getCaption).containsExactly("乙", "甲");
        assertThat(list.get(0).isMethodHidden()).isFalse(); // 编译期验证执行实体新列可访问（用真实实体替换此断言见下）
    }

    @Test
    void deletesByExecutionIds() {
        repository.saveAll(List.of(image(1L, 1L, "a", 0).setExecutionIdForTest(9L), image(1L, 1L, "b", 1)));
        repository.deleteByExecutionIdIn(List.of(9L));
        assertThat(repository.findByScenarioIdOrderBySortOrderAscIdAsc(1L)).hasSize(1);
    }

    private PersistentPlanEvidenceImageRecord image(long planId, long scenarioId, String caption, int sortOrder) { /* 按实体字段全部赋值后返回 */ }
}
```

（`setExecutionIdForTest` 若实体用链式 setter 风格则按实体实际风格调整；重点是落库与查询行为。）
运行：`JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home ./gradlew test --tests "*PlanEvidenceImageRepositoryTest"`
预期：PASS。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/yr/perftest/platform/task backend/src/test/java/com/yr/perftest/platform/task
git commit -m "feat：测试方法章节数据底座——scenario_executions 增 method_hidden 列（表格移出标记）+ 新 plan_evidence_images 实体与仓库（截图元数据，支持按场景排序查询/按执行批量级联删除）"
```

---

### Task 2: triggerExecution 内联参数覆盖（ThreadGroupOverrides）

**Files:**
- Create: `backend/src/main/java/com/yr/perftest/platform/task/ThreadGroupOverrides.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/ExecutionControlService.java:52-72,152-158`
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/ExecutionConfigMerger.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/ScenarioExecutionService.java`（先读，定位 `triggerExecution` 对 `ExecutionConfigMerger.merge` 的调用点）
- Modify: `backend/src/main/java/com/yr/perftest/platform/api/TaskPlanController.java:150-166,303-307`
- Test: `backend/src/test/java/com/yr/perftest/platform/task/ExecutionConfigMergerOverridesTest.java`

**Interfaces:**
- Produces: `record ThreadGroupOverrides(Integer threads, Integer rampUpSec, Integer durationSec)`（全字段可空，空=不覆盖）
- `ExecutionConfigMerger.merge(plan, scenario, configId, sortOrder, ThreadGroupOverrides overrides)` 新重载
- `ExecutionControlService.StartCommand(long scenarioId, String executionName, Long threadGroupConfigId, Integer threadGroupPresetSortOrder, ThreadGroupOverrides overrides)`（**末位新参数**，调用方全部更新）
- `TriggerExecutionRequest` 新增 `ThreadGroupOverrides overrides`

- [ ] **Step 1: 写失败测试**

```java
class ExecutionConfigMergerOverridesTest {
    // 构造 plan/scenario 的方式照抄 ExecutionConfigMerger 既有测试（先 Glob backend/src/test 下 *ExecutionConfigMerger* 或邻近测试，复用其 fixture 构造）
    @Test
    void overridesReplacePresetThreadValues() {
        // scenario 带一组 preset（threads=100, rampUp=30, duration=600）
        ExecutionConfig config = merger.merge(plan, scenario, null, 1, new ThreadGroupOverrides(300, 60, 900));
        assertThat(config.threads()).isEqualTo(300);
        assertThat(config.rampUp()).isEqualTo(60);
        assertThat(config.duration()).isEqualTo(900);
    }

    @Test
    void nullOverridesKeepPresetValues() {
        ExecutionConfig config = merger.merge(plan, scenario, null, 1, null);
        assertThat(config.threads()).isEqualTo(100);
    }

    @Test
    void partialOverridesOnlyReplaceGiven() {
        ExecutionConfig config = merger.merge(plan, scenario, null, 1, new ThreadGroupOverrides(250, null, null));
        assertThat(config.threads()).isEqualTo(250);
        assertThat(config.rampUp()).isEqualTo(30);
    }
}
```

- [ ] **Step 2: 运行确认失败**

`... test --tests "*ExecutionConfigMergerOverridesTest"` → 编译失败（`ThreadGroupOverrides` 不存在）。

- [ ] **Step 3: 实现**

新建 record：

```java
public record ThreadGroupOverrides(Integer threads, Integer rampUpSec, Integer durationSec) {
    public boolean isEmpty() { return threads == null && rampUpSec == null && durationSec == null; }
}
```

`ExecutionConfigMerger` 加最终重载（既有 4 参重载委托到它并传 `null`）。在两个 `return new ExecutionConfig(...)` 前应用覆盖：

```java
private ExecutionConfig applyOverrides(ExecutionConfig config, ThreadGroupOverrides o) {
    if (o == null || o.isEmpty()) return config;
    return new ExecutionConfig(
            o.threads() != null ? o.threads() : config.threads(),
            o.rampUpSec() != null ? o.rampUpSec() : config.rampUp(),
            o.durationSec() != null ? o.durationSec() : config.duration(),
            config.loops(), config.properties(), config.mode(), config.controllerNodeId(),
            config.workerNodeIds(), config.monitorTargetIds(), config.threadGroupConfigId(),
            config.threadGroupPresetSortOrder(), config.stepId(), config.stepName());
}
```

（`ExecutionConfig` 的访问器名以 `backend/.../execution/ExecutionConfig.java` 实际为准，先读再对齐。）

`ExecutionControlService`：`StartCommand` 加末位字段 `ThreadGroupOverrides overrides`；`start()` 的 requestHash 追加 `hashField(command.overrides())`；lambda 传参给 `scenarioExecutionService.triggerExecution` 时带上 overrides——该服务方法签名相应加参，内部把 overrides 传给 merger 新重载。

`TaskPlanController.triggerExecution`：`TriggerExecutionRequest` 加 `ThreadGroupOverrides overrides` 字段，组装 StartCommand 时传入。`executionName` 缺省自动生成的逻辑放前端（spec §6.2），后端不做。

- [ ] **Step 4: 全链路测试**

在既有执行控制面集成测试类（Glob `*ExecutionControl*Test`/`*TriggerExecution*Test`，选其一类追加）：

```java
@Test
void triggerWithOverridesSnapshotsOverriddenConfig() {
    // 建计划→进入 EXECUTING→建场景绑脚本（fixture 照抄同类用例）
    ScenarioExecution execution = controller.triggerExecution(scenarioId,
            new TriggerExecutionRequest(null, null, 1, new ThreadGroupOverrides(300, 60, 900)), "it-ov-1");
    String configJson = executionRepository.findById(execution.id()).orElseThrow().getConfigJson();
    assertThat(configJson).contains("\"threads\":300"); // 字段名以 configJson 实际序列化为准，先打印一次对齐断言
}
```

运行该类测试 → PASS；再跑 `--tests "*ExecutionControlService*"` 回归。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java backend/src/test/java
git commit -m "feat：执行触发支持内联线程组参数覆盖——新增 ThreadGroupOverrides(threads/rampUpSec/durationSec)，TriggerExecutionRequest→StartCommand→ExecutionConfigMerger 全链路透传，preset 基础上按字段覆盖并快照进 configJson；requestHash 纳入 overrides 防同键不同参误重放"
```

---

### Task 3: PlanScenarioDocSync 同步「测试方法」章节骨架

**Files:**
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanScenarioDocSync.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanMarkdownSupport.java`（如需新 upsert 原语）
- Test: `backend/src/test/java/com/yr/perftest/platform/task/plandoc/PlanScenarioDocSyncMethodSectionTest.java`

**Interfaces:**
- Produces: `PlanScenarioDocSync.syncTestMethodSection(PersistentTaskPlanRecord plan, List<PersistentTaskScenarioRecord> scenarios)`——若 body 不含「测试方法」章节则原样返回 false；含则在小节骨架间保留用户自由文本。
- 调用点：`TaskScenarioService` 创建/更新/删除场景处现有 `syncPlanScenarios`/`onScenarioDeleted` 调用旁（`TaskScenarioService.java:90,:144` 及删除方法——先读）。

- [ ] **Step 1: 写失败测试**

```java
@Transactional
class PlanScenarioDocSyncMethodSectionTest {
    // 依赖与 fixture 照抄既有 PlanScenarioDocSync 测试类（Glob backend/src/test/**/plandoc/*）
    @Test
    void upsertsScenarioBlocksInsideTestMethodSection() {
        PersistentTaskPlanRecord plan = planWithBody("""
                ## 八、测试方法
                章节导语保留。
                ### S1 放款提交 · 容量测试
                **方法说明**：手写内容保留。
                ## 九、风险与预案
                后续章节不受影响。""");
        sync.syncTestMethodSection(plan, List.of(scenario(1, "放款提交", "容量测试")));
        assertThat(plan.getBody()).contains("### S1 放款提交 · 容量测试");
        assertThat(plan.getBody()).contains("**方法说明**：手写内容保留。");
        assertThat(plan.getBody()).contains("## 九、风险与预案");
    }

    @Test
    void skipsWhenSectionAbsent() {
        PersistentTaskPlanRecord plan = planWithBody("## 一、背景\n内容");
        assertThat(sync.syncTestMethodSection(plan, List.of())).isFalse();
        assertThat(plan.getBody()).isEqualTo("## 一、背景\n内容");
    }

    @Test
    void addsNewScenarioAndKeepsLead() { /* 新场景追加小节；导语仍在场景前 */ }
    @Test
    void removesBlockForDeletedScenario() { /* onScenarioDeleted 路径 */ }
}
```

- [ ] **Step 2: 运行确认失败**（方法不存在）。

- [ ] **Step 3: 实现**

骨架块格式（与设计 §3 一致）：

```
### S{n} {name} · {testType}
**方法说明**：（自由编辑，实体同步不触碰此处）
> 执行记录与监控证据：见 Pretty 视图 / 报告
```

实现要点：用 `PlanMarkdownSupport.sectionBounds(body, 标题含「测试方法」的 ## 行)` 定位章节区间；区间内复用场景块 upsert/remove 语义（读 `upsertScenarioFacts` 的保留策略注释 `PlanMarkdownSupport.java:153`，方法说明自由区从 `**方法说明**：` 行到块尾，同步时整块替换标题外仅保留该自由区）。场景不存在时追加在章节导语之后、下一场景小节之前，顺序 = `sortOrder`。接线到 `TaskScenarioService` 三处调用点。

- [ ] **Step 4: 跑测试**（新类 + 既有 `*PlanScenarioDocSync*`、`*PlanMarkdownSupport*` 回归）→ PASS。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java backend/src/test/java
git commit -m "feat：场景骨架同步扩展至测试方法章节——PlanScenarioDocSync 新增 syncTestMethodSection，含「测试方法」章节的文档在场景增删改时维护 ### S{n} 小节骨架，方法说明自由区保留；章节缺失时不动 body"
```

---

### Task 4: 章节聚合端点 GET /api/task-plans/{planId}/method

**Files:**
- Create: `backend/src/main/java/com/yr/perftest/platform/task/method/MethodSectionResponse.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/task/method/MethodSectionService.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/api/TaskPlanController.java`
- Test: `backend/src/test/java/com/yr/perftest/platform/task/method/MethodSectionServiceTest.java`

**Interfaces:**
- Consumes: Task 1 的 `PlanEvidenceImageRepository`、`methodHidden`。
- Produces:

```java
public record MethodSectionResponse(long planId, List<ScenarioMethodData> scenarios) {
    public record ScenarioMethodData(long scenarioId, String name, String testType, int sortOrder,
            Long scriptVersionId, String scriptName,
            List<ExecutionRow> executions, long hiddenCount, List<EvidenceImage> images) {}
    public record ExecutionRow(long executionId, String executionName,
            int threads, int rampUpSec, int durationSec, String status,
            Long samples, Double successRate, Double avgRtMs, Double p95Ms, Double tps,
            String startedAtText, boolean hidden) {}
    public record EvidenceImage(long id, Long executionId, String caption, int sortOrder,
            String contentType, long sizeBytes) {}
}
```

- `GET /api/task-plans/{planId}/method` → 200 + 上述 JSON（编辑视图含 hidden=true 行与 hiddenCount；执行列表另需 `GET /api/scenarios/{id}/executions` 全量——复用既有端点，不改）。

- [ ] **Step 1: 写失败测试**

```java
@Transactional
class MethodSectionServiceTest {
    // fixture：建 plan(EXECUTING)+2 场景（其一绑脚本）+3 execution（2 成功带 AggregateReport、1 RUNNING、1 methodHidden=true）+2 截图
    @Test
    void assemblesScenariosWithRowsAndImages() {
        MethodSectionResponse res = service.getPlanMethod(planId);
        assertThat(res.scenarios()).hasSize(2);
        ScenarioMethodData s1 = res.scenarios().get(0);
        assertThat(s1.scriptName()).isEqualTo("loan-submit.jmx");
        assertThat(s1.executions()).extracting(MethodSectionResponse.ExecutionRow::tps).contains(682.4, 712.0, (Double) null);
        assertThat(s1.executions().get(0).successRate()).isEqualTo(99.8);
        assertThat(s1.hiddenCount()).isEqualTo(1L);
        assertThat(s1.images()).hasSize(2);
    }
}
```

字段来源对齐（执行前先读再映射）：`threads/rampUpSec/durationSec` 从 `execution.configJson` 解析（复用 `TaskJsonSupport` 或直接 ObjectMapper 读 `threads/rampUp/duration` 键，**以 configJson 实际键名为准**——Task 2 测试里已打印过样例）；`samples/tps(avg→throughput)/avgRt/p95/errorRate` 从 `PersistentAggregateReportRecord.summaryJson`（字段名 `samples/throughput/avgRt|avgRtMs/p95/errorRate`，读一次真实 JSON 对齐）；`successRate = 100 - errorRate`，四舍五入 1 位；`scriptName` 由 `scriptVersionId` 查 `ScriptService`/script 仓库（读 `ScriptController.java:35-42` 的取数方式）。

- [ ] **Step 2: 运行确认失败** → **Step 3: 实现**（service 一次查询场景列表 + 批量查 executions + 批量查 aggregate + 批量查 images，禁止 N+1 循环查库——按 scenarioId 分组一次拉取）+ Controller：

```java
@GetMapping("/{planId}/method")
public MethodSectionResponse getPlanMethod(@PathVariable long planId) {
    planWorkflowService.requireActor(planId, currentActor(), "EDIT"); // 鉴权模式照抄本 Controller 既有方法
    return methodSectionService.getPlanMethod(planId);
}
```

- [ ] **Step 4: 跑测试** → PASS；`*TaskPlanController*` 回归无破坏。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java backend/src/test/java
git commit -m "feat：测试方法章节聚合端点 GET /api/task-plans/{id}/method——场景（绑脚本）+执行行（configJson 参数快照+聚合指标回填+hidden 标记+hiddenCount）+截图元数据一次组装，批量子查询无 N+1"
```

---

### Task 5: 行可见性 PATCH 与彻底删除级联删图

**Files:**
- Modify: `backend/src/main/java/com/yr/perftest/platform/api/TaskPlanController.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/method/MethodSectionService.java`（或独立 `setVisibility` 方法）
- Modify: 既有执行删除服务（`TaskPlanController.java:184-194` 调用的 `executionService.deleteExecution/deleteExecutions`——读实现文件定位）
- Test: `backend/src/test/java/com/yr/perftest/platform/task/method/MethodVisibilityAndCascadeTest.java`

**Interfaces:**
- Produces: `PATCH /api/executions/{executionId}/method-visibility`，body `{"hidden": true|false}` → 204。
- 彻底删除（既有 `DELETE /api/executions/batch`）新增级联：删除 `plan_evidence_images.execution_id ∈ ids` 的记录。

- [ ] **Step 1: 写失败测试**

```java
@Transactional
class MethodVisibilityAndCascadeTest {
    @Test
    void patchTogglesMethodHidden() {
        assertThat(executionRepository.findById(eid).orElseThrow().isMethodHidden()).isFalse();
        controller.setMethodVisibility(eid, new MethodSectionService.VisibilityRequest(true));
        assertThat(executionRepository.findById(eid).orElseThrow().isMethodHidden()).isTrue();
    }

    @Test
    void batchDeleteRemovesEvidenceImages() {
        long imgId = imageService.store(planId, scenarioId, eid, "GC 截图", pngBytes()); // Task 6 前可先 repository.save 直接造数
        controller.deleteExecutions(List.of(eid));
        assertThat(imageRepository.findById(imgId)).isEmpty();
    }
}
```

- [ ] **Step 2: 失败确认** → **Step 3: 实现**（PATCH 端点内 `requireActor` 按 plan→scenario→plan 反查鉴权；删除级联在 `deleteExecutions` 实现里加 `evidenceImageRepository.deleteByExecutionIdIn(ids)`，注意与聚合数据删除同事务）→ **Step 4: 跑测试 + 既有删除测试回归** → PASS。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java backend/src/test/java
git commit -m "feat：执行行可见性与删除级联——PATCH method-visibility 控制表格移出/恢复；批量删除执行时同事务级联删除挂其名下的补充截图"
```

---

### Task 6: 截图上传/元数据/文件流端点

**Files:**
- Create: `backend/src/main/java/com/yr/perftest/platform/task/method/PlanEvidenceImageService.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/api/TaskPlanController.java`
- Test: `backend/src/test/java/com/yr/perftest/platform/task/method/PlanEvidenceImageServiceTest.java`

**Interfaces:**
- Consumes: Task 1 仓库；存储注入模式照抄 `ScriptService.java:31-46`（`@Value("${platform.storage.root:./storage}")`）。
- Produces:
  - `POST /api/task-plans/{planId}/scenarios/{scenarioId}/evidence-images`：multipart `file` + `caption`(可选) + `executionId`(可选) → 201 + `EvidenceImage`
  - `PUT /api/images/{imageId}`：`{"caption": "...", "sortOrder": 3}` → 200
  - `DELETE /api/images/{imageId}` → 204
  - `GET /api/images/{imageId}/file` → `image/png|jpeg|webp` 二进制流
- `PlanEvidenceImageService.store(planId, scenarioId, executionId, caption, MultipartFile)`；校验：类型 png/jpg/webp、≤5MB、scenario 属于该 plan。

- [ ] **Step 1: 写失败测试**（store 落盘到临时目录 + 校验拒绝：`@TempDir` 覆盖 storageRoot 构造 service；上传 .txt 拒绝、6MB 拒绝、跨 plan scenario 拒绝、file 流读回字节一致——MockMvc 集成测试模式照抄 `ScriptController` 上传测试，Glob `*Script*Test` 参考）。

- [ ] **Step 2: 失败确认** → **Step 3: 实现**（落盘路径 `{storageRoot}/images/plans/{planId}/{uuid}.{ext}`，`storedPath` 存相对路径字符串；`GET file` 端点按 image→plan 反查成员权限后 `Files.copy(path, response.getOutputStream())`，`Content-Type` 用存的 `contentType`）→ **Step 4: 跑测试** → PASS。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java backend/src/test/java
git commit -m "feat：补充截图上传与文件流——multipart 上传(png/jpg/webp≤5MB)落盘 storageRoot/images/plans/{planId}，图注/排序可改可删，GET /api/images/{id}/file 带项目成员鉴权流式返回"
```

---

### Task 7: 前端章节识别与模块挂载

**Files:**
- Modify: `frontend/src/utils/plan-markdown.ts:1-48`
- Modify: `frontend/src/components/task-plans/PlanDetailDocument.vue:79-101,446`（行号为当前版本，编辑前先读）
- Test: `frontend/src/utils/plan-markdown.test.ts`（vitest，若已有同名测试文件则追加）

**Interfaces:**
- Produces: `export const METHOD_SECTION_TITLE = '测试方法'`；`canonicalTitleOf` 命中规则：标题文本以「测试方法」结尾（如 `八、测试方法`）→ title=`'测试方法'`，**优先于序号前缀容错**（否则被 `八、` 吞并到「八、场景设计」）。
- `PlanDetailDocument.vue` 渲染分支：`section.title === METHOD_SECTION_TITLE` → `<TestMethodModule>`（Task 9 之前先以最小占位组件挂载，本任务交付识别+挂载骨架）；`isModuleSection()` 纳入。

- [ ] **Step 1: 写失败测试**

```ts
import { describe, expect, it } from 'vitest';
import { splitSections, METHOD_SECTION_TITLE } from './plan-markdown';

describe('测试方法章节识别', () => {
  it('八、测试方法 独立成章且不被序号前缀吞并到场景设计', () => {
    const sections = splitSections('## 一、背景\nx\n## 八、测试方法\n内容\n## 九、风险与预案\ny');
    expect(sections.map(s => s.title)).toEqual(['一、背景', METHOD_SECTION_TITLE, '九、风险与预案']);
    expect(sections[1].heading).toBe('八、测试方法');
  });
  it('内置模板（无测试方法章节）不受影响', () => {
    const sections = splitSections('## 一、背景\nx\n## 八、场景设计\ny');
    expect(sections.map(s => s.title)).toEqual(['一、背景', '八、场景设计']);
  });
});
```

- [ ] **Step 2: `npm run test -- plan-markdown` 确认失败** → **Step 3: 实现**（`canonicalTitleOf` 在 exact 匹配后、序号前缀循环前插入：`if (text.endsWith(METHOD_SECTION_TITLE)) return METHOD_SECTION_TITLE;`；`PlanDetailDocument.vue` 在 `ScenarioDesignModule` 分支后加 `v-else-if="section.title === METHOD_SECTION_TITLE"` 渲染占位组件 `<TestMethodModule :plan="plan" :scenarios="scenarios" @changed="emit('changed')" @request-add="emit('request-add')" @request-edit="(s) => emit('request-edit', s)"/>`，本任务先建仅含 `<section class="scenario-design-module">测试方法模块（构建中）</section>` 的占位文件；`isModuleSection` 加入 title）→ **Step 4: `npm run test -- plan-markdown` + `npm run build` 通过**。

- [ ] **Step 5: Commit**

```bash
git add frontend/src
git commit -m "feat：前端识别测试方法章节——plan-markdown 注册规范标题（关键词优先于序号前缀），PlanDetailDocument 挂载 TestMethodModule 分支并入 isModuleSection"
```

---

### Task 8: 前端 API 客户端与类型

**Files:**
- Create: `frontend/src/api/plan-method.ts`
- Modify: `frontend/src/api/task-plans.ts:129-148`（triggerExecutionApi 请求体加 overrides）
- Modify: `frontend/src/types/index.ts`（追加类型）

**Interfaces:**
- Produces（`api/plan-method.ts`，HTTP 封装方式照抄 `api/task-plans.ts` 既有函数——先读其 fetch 封装与鉴权头处理）:

```ts
export interface EvidenceImage { id: number; executionId: number | null; caption: string; sortOrder: number; contentType: string; sizeBytes: number; }
export interface MethodExecutionRow { executionId: number; executionName: string; threads: number; rampUpSec: number; durationSec: number; status: string; samples: number | null; successRate: number | null; avgRtMs: number | null; p95Ms: number | null; tps: number | null; startedAtText: string; hidden: boolean; }
export interface MethodScenarioData { scenarioId: number; name: string; testType: string; sortOrder: number; scriptVersionId: number | null; scriptName: string | null; executions: MethodExecutionRow[]; hiddenCount: number; images: EvidenceImage[]; }
export interface MethodSectionData { planId: number; scenarios: MethodScenarioData[]; }

export function getPlanMethodApi(planId: number): Promise<MethodSectionData>;
export function setExecutionVisibilityApi(executionId: number, hidden: boolean): Promise<void>;
export function uploadEvidenceImageApi(planId: number, scenarioId: number, file: File, caption?: string, executionId?: number): Promise<EvidenceImage>;
export function updateEvidenceImageApi(imageId: number, patch: { caption?: string; sortOrder?: number }): Promise<EvidenceImage>;
export function deleteEvidenceImageApi(imageId: number): Promise<void>;
```

- `triggerExecutionApi` 请求体类型扩展 `overrides?: { threads: number; rampUpSec: number; durationSec: number }`（参数快照后端落 configJson）。

- [ ] **Step 1: 实现全部函数**（无独立测试，类型正确性靠 `vue-tsc`；上传用 `FormData`，勿手工设 Content-Type）。
- [ ] **Step 2: `npm run build` 通过** → **Step 3: Commit**

```bash
git add frontend/src
git commit -m "feat：测试方法章节前端 API 客户端——章节聚合/行可见性/截图上传改删/文件流类型定义，triggerExecutionApi 支持内联 overrides"
```

---

### Task 9: TestMethodModule + MethodExecTable（场景小节与执行记录表）

**Files:**
- Create: `frontend/src/components/task-plans/method/TestMethodModule.vue`（≤500 行：场景列表循环、章节头「+ 新增场景」、活跃执行轮询）
- Create: `frontend/src/components/task-plans/method/MethodExecTable.vue`（≤500 行：表格列渲染、新增执行行、触发+跳转、行点击、删行弹窗、已移出恢复）

**Interfaces:**
- Consumes: Task 7 挂载（props：`plan: TaskPlan`、`scenarios: TaskScenario[]`；emit：`changed/request-add/request-edit`）；Task 8 API；`useTaskPlans().openExecution({id, projectId})` 跳转（`useTaskPlans.ts:264-266`）；`triggerExecutionApi`；预检跳过弹窗模式照抄 `ScenarioDesignModule.vue:207-224`。
- Produces: 占位组件替换为真实实现；`MethodExecTable` props `{ plan, scenario: MethodScenarioData, canExecute: boolean }`，emit `refresh`（执行/删除后父级重拉聚合）。

- [ ] **Step 1: TestMethodModule 骨架**

职责与要点：
- `onMounted` + `watch(plan.id)` 调 `getPlanMethodApi`；存在非终态 execution（status ∈ PENDING/RUNNING/STOPPING）时 `setInterval(load, 5000)`，`onUnmounted` 清理；
- 每场景渲染 `<MethodScenarioCard>` 结构按原型：小节头（`S{n} 序号徽标 + 场景名 + testType 徽标 + 关联脚本下拉`）——脚本下拉复用 `BindScriptDialog` 的数据源与 `bindScenarioScriptApi`（读 `ScenarioDesignModule.vue:199` 绑定实现，交互改为小节头内联 a-select：options=项目脚本版本，change 即绑定）；
- 方法说明自由文本：本迭代显示 body 骨架中的方法说明（由 `parseScenarioBlocks` 类似逻辑解析「测试方法」章节——新增本地解析函数 `parseMethodSections(body)` 放 `plan-markdown.ts`，返回 `[{ name, testType, methodText }]`，与实体场景按名称对齐展示；未对齐的手写小节仅展示文本不可执行）；编辑走 `PlanSectionInlineEditor` 模式成本高，**本迭代方法说明只读展示 + 「编辑」按钮切到 Markdown 视图**（spec §3 行内编辑留待后续，不阻塞主链路——在组件注释注明）；
- 「+ 新增场景」emit `request-add` 复用既有场景创建流。
- `canExecute` = `plan.permissions?.EXECUTE`（读 `usePlanDoc`/plan 详情里 permissions map 的消费方式对齐 `PlanDetailDocument`）。

- [ ] **Step 2: MethodExecTable**

列与交互对齐原型（`prototype-assets/test-method-optimization/test-method-optimization-prototype.html`）：`# | 用户数 | Ramp-up(s) | 压测时间 | 样本数 | 成功率 | 平均RT(ms) | P95(ms) | TPS | 状态 | 执行时间 | 操作(眼睛/垃圾桶图标)`；数字列等宽字体右对齐；未终态结果列 `—`；状态徽标复用 `toUiStatus/executionStatusText`（`api/task-plans.ts` 既有）。
- 新增执行行：三个 `a-input-number`（默认值 = 该场景最近一次执行参数，无历史取场景 preset/`threads/rampUp/duration`），「执行」=圆形主色播放图标按钮（24px，样式抄原型 `.btn-exec`）；点击 → `triggerExecutionApi(scenarioId, { overrides: {...}, executionName: 'S{n} {threads}并发 {MM-dd HH:mm}' })` → 成功 `openExecution` 跳详情页；`PLAN_PRECHECK_FAILED` 复用确认跳过弹窗；`canExecute=false` 时禁用 + tooltip「需进入执行阶段」；未绑脚本禁用 + tooltip「先绑定压测脚本」；
- 行点击（非按钮区）→ `openExecution`；操作列图标按钮：眼睛=详情、垃圾桶=打开删行弹窗；
- 删行弹窗（a-modal）：两单选——「仅从表格移出」调 `setExecutionVisibilityApi(id, true)`；「彻底删除」红字警示后调 `deleteExecutionsApi([id])`（既有）；完成后 emit `refresh`；
- 表格下方「已从表格移出 N 条记录（数据保留，执行详情页历史可见）+ 恢复展示」条：点击展开 hidden 行列表，逐条 `setExecutionVisibilityApi(id, false)`。

- [ ] **Step 3: 手动验证**

`npm run dev` 起前后端（后端 `bootRun`），走查：含「八、测试方法」章节的计划 → 场景小节渲染；调参执行 → 跳详情页；返回后行追加、结果回填（需等执行终态）；移出/恢复/彻底删除；无「测试方法」章节的计划不受影响；「八、场景设计」模块回归正常。

- [ ] **Step 4: `npm run build` + 后端全量 `test` 通过** → **Step 5: Commit**

```bash
git add frontend/src
git commit -m "feat：测试方法模块场景小节与执行记录表——场景实体小节（脚本内联下拉绑定）+可执行表格（参数覆盖触发→跳详情跟进→结果自动回填→轮询至终态）+两段式删行与移出恢复"
```

---

### Task 10: MethodEvidence 监控证据区（表格下方）

**Files:**
- Create: `frontend/src/components/task-plans/method/MethodEvidence.vue`（≤500 行）

**Interfaces:**
- Consumes: `TaskMonitoringCharts.vue`（props `{ monitoring: TaskMetricSeries }`，纯展示）；`TargetServerMetricsPanel.vue`（props `{ executionId, targets, polling=false, refreshIntervalMs }`，`polling=false` 单次拉终态序列）；执行详情数据获取方式照抄 `ExecutionDetailView.vue:352-353`（`execution.monitoring` 来自执行详情接口，`targetMonitoring.serverTargets` 同源——读 `useTaskPlans` 中 executionDetail 的加载函数并在本组件内按 executionId 复用同一 API）。
- Props: `{ planId: number; scenario: MethodScenarioData }`；emit `refresh`。

- [ ] **Step 1: 实现**

结构对齐原型「监控证据」区块（表格下方）：
- 区块头：`监控证据` + 执行选择器 `a-select`（options=`scenario.executions` 中非 hidden 行，label=`#id · N并发 · 时间`，默认值=最新一条**成功**执行）+ 说明文案「与执行详情页同源数据，只读渲染」；
- 选中执行 → 拉执行详情（monitoring + targetMonitoring）→ 渲染四卡：`TaskMonitoringCharts` 已含 TPS/RT 两图；CPU/内存两张用 `TargetMetricChartCard`（kind=`SERVER_CPU`/`SERVER_MEM`，`usePrometheusSeries` 单次拉取，`TargetServerMetricsPanel` 若不便只挑两种 kind 复用，则直接引 `TargetMetricChartCard`——读 `TargetServerMetricsPanel.vue:45-48` 的卡片组装方式后择一，**保持组件复用不复制实现**）；未绑被测目标时占位「未绑定被测目标监控」；
- 「补充截图」子区：两列大图网格（`object-fit: contain` 原始比例完整显示，**禁止 cover 裁切**——原型已验证该结论）、图注 `a-input` 行内编辑（失焦 `updateEvidenceImageApi`）、拖拽排序（HTML5 drag 或上下移按钮，低成本方案：`sortOrder` 调整按钮）、删除（`deleteEvidenceImageApi` + 确认）、上传卡（`a-upload` 手动模式 → `uploadEvidenceImageApi`，可选挂当前选中 execution）；点击图片灯箱放大（简易 fixed 遮罩 + img）；
- 场景无执行时整区显示空态「完成首次执行后，此处自动生成 TPS / 响应时间 / CPU / 内存趋势图」。

- [ ] **Step 2: 手动验证**：执行完成的场景 → 四图渲染且与执行详情页一致；上传/图注/删除/排序生效；无执行场景显示空态。

- [ ] **Step 3: `npm run build`** → **Step 4: Commit**

```bash
git add frontend/src
git commit -m "feat：测试方法章节监控证据区——表格下方按场景归档：执行选择器默认最新成功，TPS/RT/CPU/内存复用执行详情页组件终态只读渲染；补充截图两列原始比例网格（上传/图注/排序/删除/灯箱）"
```

---

### Task 11: Word/PDF 导出嵌入监控图与截图

**Files:**
- Modify: 既有导出链路（先读 `ReportExportController` 与前端触发导出的调用点，确定请求形态）
- Modify: `frontend/src`（导出触发前收集 PNG）

**Interfaces:**
- Produces: 导出请求携带 `chartImages: { executionId: number; kind: 'TPS'|'RT'|'CPU'|'MEM'; dataUrl: string }[]` 与截图由后端自行读取（截图有 storedPath 无需前端传）。

- [ ] **Step 1: 读导出链路**（`ReportExportController` 的请求入口、Word/PDF 生成器如何取图——若已有图片插入原语则直接复用；前端导出按钮位置）。
- [ ] **Step 2: 前端收集**：导出按钮点击 → 对测试方法章节每条非 hidden 执行，用离屏 `echarts.init` 渲染四图（option 复用图表组件的 option 构建函数——若 option 构建在组件内联，抽公共函数到 `frontend/src/components/tasks/chart-options.ts` 供组件与导出共用）→ `getDataURL({ type: 'png', pixelRatio: 2 })` → 随导出请求 POST。
- [ ] **Step 3: 后端嵌入**：导出生成器在测试方法章节位置插入执行结果表（数据=Task 4 聚合）+ 四图 + 截图（按 storedPath 读文件流插入），图片缺失时降级为文字占位不阻断导出。
- [ ] **Step 4: 手动验证**：导出 Word/PDF 打开检查表格与图片；无执行/无图计划导出不报错。
- [ ] **Step 5: Commit**

```bash
git add backend/src frontend/src
git commit -m "feat：导出报告嵌入测试方法章节——前端 ECharts 离屏预渲染四趋势图随导出请求提交，后端嵌入结果表+趋势图+补充截图，缺图降级文字占位"
```

---

### Task 12: 全量回归与验收清单

**Files:** 无新文件。

- [ ] **Step 1: 后端全量**：`JAVA_HOME=... ./gradlew test`（基线 497 用例 0 失败，新增用例全绿；耗时参考 7m）。
- [ ] **Step 2: 前端**：`npm run build && npm run test`。
- [ ] **Step 3: 验收走查**（对照 spec §10 与原型）：
  1. 自定义模板「八、测试方法」章节激活模块；内置模板「八、场景设计」不受影响；
  2. PLANNING 状态执行按钮禁用（tooltip）；EXECUTING 可执行；
  3. 调参执行 → 跳详情页 → 终态后表格行结果回填、参数为当次快照；
  4. 重跑同档并发追加行不覆盖；移出/恢复；彻底删除连带截图且报告页对应 preset 消失；
  5. 监控证据四图与执行详情页一致；截图上传/图注/排序/删除/灯箱；
  6. Markdown 视图显示骨架+方法说明，无表格污染；文档 revision 不因执行/删行变化（引用式核心承诺）；
  7. 导出 Word/PDF 含表格与图。
- [ ] **Step 4: Commit（如有收尾修复）**

---

## Self-Review 结论

- **Spec 覆盖**：spec §3 挂载/骨架（Task 3/7）、§4 表格/触发/删行（Task 2/4/5/9）、§5 证据区（Task 10）、§6 数据与 API（Task 1/4/5/6）、§7 权限（各端点 requireActor + Task 9 canExecute）、§8 导出（Task 11）、§9 非目标（未涉及内置模板改造）。方法说明「行内编辑」降级为只读+跳 Markdown 视图（Task 9 注明），属 spec §3 的弱化，已显式标注。
- **占位符**：无 TBD/TODO；既有文件接缝均给出定位方式（行号/Glob 模式/对齐对象）与期望签名。
- **类型一致性**：`ThreadGroupOverrides`、`MethodSectionResponse` 三层 record、前端 `MethodSectionData/EvidenceImage/MethodExecutionRow` 在 Task 2/4/8 定义，Task 9/10/11 消费名称一致。
