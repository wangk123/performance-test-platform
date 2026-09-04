# P0-1 计划文档模块重构 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 TaskPlan 升级为"一稿走到头"的压测计划文档（计划→执行回填→报告→发布），含二级状态机、评审批注、模板、revision 冲突三选一、发布快照与只读分享。

**Architecture:** 后端不拆 Maven 模块，新域全部放 `task/plandoc` 子包；执行门禁与环境检查挂既有 `ExecutionControlService.start` 唯一 seam（CONTEXT.md C1）；Markdown 原文是唯一数据源，Pretty=原文受约束章节的提取视图。前端重构 `TaskPlanDetail` 为 文档/评审/报告/发布 四 Tab，引入 `md-editor-v3` + `diff`。

**Tech Stack:** Spring Boot 3 / JPA（ddl-auto: update，无 Flyway）/ JUnit 5 `@SpringBootTest`（H2）+ AssertJ；Vue 3.5 + ant-design-vue 4 + md-editor-v3 + diff（无前端测试框架，以 `npm run build` 类型检查 + 手工验证步骤为门槛）。

**Spec:** `docs/superpowers/specs/2026-09-02-plan-document-module-design.md`（实现以该设计文档为准；本计划逐条论证自它）

## Global Constraints

- Java 17：`export JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/`（AGENTS.md），所有 gradle 命令前设置。
- 后端单测命令模板：`export JAVA_HOME=... && ./gradlew :backend:test --tests "<FQCN>"`；全量：`./gradlew :backend:test`。
- 前端验证：`cd frontend && npm run build`（vue-tsc --noEmit + vite build）；前端无测试框架，每个前端任务的"验证"= build 通过 + 列出的手工检查步骤。
- 无 Flyway/Liquibase：新列新表靠 JPA `ddl-auto: update`（H2 开发态）自动建，同时**必须**手写进 `docs/database/mysql-schema.sql`（MySQL 目标态，P0-4 负责正式迁移）。
- 新端点身份取 SecurityContext `HumanPrincipal`（`config/AuthenticationFilter` 注入），不信任 `X-User` 头；既有 `POST /api/projects/{projectId}/task-plans` 的 `X-User` 过渡期兼容（principal 优先，头兜底）。
- 包根：`backend/src/main/java/com/yr/perftest/platform`。新后端类除 `ProjectAccessResolver`（放 `project`）与 `ExecutionLifecycleEvent`/`TestType`（放 `task`）外，全部放 `task/plandoc`。
- 状态枚举值固定：`PlanPhase = {DRAFT, REVIEW, EXECUTION, REPORT, PUBLISH}`；`PlanStatus = {DRAFT, PENDING, IN_REVIEW, APPROVED, RUNNING, DONE, GENERATING, PUBLISHED}`。
- 权限动作键固定（17 个，响应 `permissions` map 的 key）：`EDIT/SUBMIT/START_REVIEW/APPROVE/REJECT/WITHDRAW/BACK_TO_DRAFT/START_EXECUTION/TO_REPORT/GENERATE_REPORT/PUBLISH/NEW_REVISION/PRECHECK_RUN/PRECHECK_SKIP/DELETE/COMMENT/SHARE`。
- 章节标题固定（中文序号一~十一，`## ` 二级标题）：`一、背景 / 二、测试目的与指标 / 三、测试范围 / 四、测试资源 / 五、测试约束 / 六、测试策略 / 七、场景设计 / 八、风险与预案 / 九、排期与协作 / 十、附录 / 十一、结论`。
- 错误码固定：`PLAN_STATE`(409) / `PLAN_REVISION_CONFLICT`(409) / `PLAN_PRECHECK_FAILED`(409) / `PLAN_ACCESS_DENIED`(403) / `PLAN_INVALID`(400) / `SHARE_NOT_FOUND`(404)。异常 message 以错误码前缀开头（如 `PLAN_REVISION_CONFLICT：…`），前端靠 message 判别。
- 回填幂等标记：`<!-- backfill:execution:<executionId> -->`（场景块内查重）；报告总览标记 `<!-- backfill:report -->`（整块替换）。
- 提交信息格式沿用仓库惯例：`feat：P0-1 <内容>`（中文全角冒号）。
- 设计文档 §2 的代码事实表是本计划的事实基线；与本计划冲突时以设计文档为准并停下询问。

## File Structure（全景）

```
backend/src/main/java/com/yr/perftest/platform/
├─ task/
│  ├─ PersistentTaskPlanRecord.java          [改] +phase/status/body/revision/publishedAt/precheckJson/precheckExecutedAt +状态迁移方法
│  ├─ PersistentTaskScenarioRecord.java      [改] scriptVersionId 可空 +purpose/testType
│  ├─ TaskPlan.java                          [改] record 扩展 7 字段
│  ├─ TaskPlanService.java                   [改] 创建带模板/删级联/默认配置仅草稿
│  ├─ TaskScenarioService.java               [改] 脚本可空+purpose/testType+文档回写联动
│  ├─ ExecutionControlService.java           [改] start 前置门禁+启动置 RUNNING+cancel 终态事件
│  ├─ ExecutionLifecycleEvent.java           [新] 终态事件 record
│  ├─ TestType.java                          [新] 场景测试类型枚举
│  └─ plandoc/
│     ├─ PlanPhase.java / PlanStatus.java / PlanCommentKind.java   [新]
│     ├─ PlanStateException.java / PlanRevisionConflictException.java /
│     │  PlanAccessDeniedException.java / PlanValidationException.java /
│     │  PlanPrecheckFailedException.java                            [新]
│     ├─ PlanMarkdownSupport.java            [新] 章节切分/替换/场景块回填/模板渲染（纯静态）
│     ├─ PlanScenarioDocSync.java            [新] 场景实体 → 文档七章节回写（保留自由文本+执行记录）
│     ├─ PlanDocumentService.java            [新] 文档读(惰性纠偏)/写(409 冲突)/回填
│     ├─ PlanWorkflowService.java            [新] 12 流转+批注+模板+分享+precheck+发布快照+生命周期回调
│     ├─ PlanQuickExecuteService.java        [新] 快捷执行（依赖 Control+Workflow，破循环）
│     ├─ PlanExecutionLifecycleListener.java [新] 终态事件 → 回填 + DONE
│     ├─ PlanAccess.java                     [新] 权限矩阵计算（静态）
│     ├─ PersistentPlanCommentRecord.java + PersistentPlanCommentRepository.java        [新]
│     ├─ PersistentPlanTemplateRecord.java + PersistentPlanTemplateRepository.java      [新]
│     ├─ PersistentPlanPublishSnapshotRecord.java + …Repository.java                    [新]
│     └─ PersistentPlanShareTokenRecord.java + …Repository.java                         [新]
├─ project/ProjectAccessResolver.java        [新] 角色解析（含 PlanActorRole 枚举）
├─ api/
│  ├─ PlanDocumentController.java            [新] 计划项级 REST + 流转 + 批注 + 模板 + 分享 + 公开页
│  ├─ TaskPlanController.java                [改] 创建带模板/场景扩展/关联脚本/快捷执行；移除项级 GET/PUT/DELETE
│  └─ PlatformExceptionHandler.java          [改] 注册 5 个 plan 异常
├─ execution/distributed/DistributedJmeterExecutionRunner.java [改] 三个 mark* 终态后发事件
├─ config/SecurityConfiguration.java         [改] 放行 /api/share/**
docs/database/mysql-schema.sql               [改] task_plans/task_scenarios 新列 + 4 张新表
frontend/
├─ src/types/index.ts                        [改] Plan 域类型 + TaskPlan/TaskScenario 扩展
├─ src/api/plan-doc.ts                       [新] 全量 plan 域 API
├─ src/utils/plan-markdown.ts                [新] 前端章节解析/写回（与后端同约定）
├─ src/composables/usePlanDoc.ts             [新] 计划文档状态+动作
├─ src/components/task-plans/
│  ├─ TaskPlanDetail.vue                     [改→重写] 壳：步进条+四 Tab+权限动作条
│  ├─ PlanPhaseStepper.vue                   [新]
│  ├─ PlanDetailDocument.vue                 [新] Pretty|Markdown 分段控件 + TOC + 执行设置抽屉
│  ├─ PlanDetailReview.vue                   [新] 批注时间线 + 流转动作
│  ├─ PlanDetailReport.vue                   [新] 报告阶段 + 达成表
│  ├─ PlanDetailPublish.vue                  [新] 发布 + 快照 + 分享
│  ├─ PlanConflictDialog.vue                 [新] 双栏 diff 三选一
│  ├─ ScenarioDesignModule.vue               [新] Pretty 场景设计（业务字段+关联脚本+执行）
│  ├─ TaskPlanList.vue                       [改] 阶段/子状态 badge
│  ├─ TaskPlanDialog.vue                     [改] 模板选择
│  ├─ ScenarioDialog.vue                     [改] 测试类型/目的/脚本可选
│  └─ ExecuteConfirmDialog.vue               [改] precheck 失败→跳过继续
├─ src/views/SharePlanPage.vue               [新] 公开只读页
├─ src/router/index.ts                       [改] /share/plans/:token + 守卫放行
└─ src/composables/useTaskPlans.ts           [改] runScriptAsset 改快捷执行端点
CONTEXT.md                                    [改] 领域词汇补 Plan 文档域
docs/implementation-log.md                    [改] 追加 P0-1 记录
```

**任务依赖**：Task 1→2→3 是地基；4/5/6/7 依赖 1-3；8/9 依赖 5+7；10/11 依赖 5；12 依赖全部后端；13 依赖 12（对着真接口写）；14-17 顺序执行；18 收尾。

---

### Task 1: 状态枚举、异常与实体扩展（task_plans/task_scenarios）

**Files:**
- Create: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanPhase.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanStatus.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanCommentKind.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/task/TestType.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanStateException.java`（及其余 4 个异常，同类一个文件）
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/PersistentTaskPlanRecord.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/PersistentTaskScenarioRecord.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/TaskPlan.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/TaskPlanService.java:106-120`（toPlan 映射）
- Modify: `docs/database/mysql-schema.sql:74-115`
- Test: `backend/src/test/java/com/yr/perftest/platform/task/plandoc/PlanEntityExtensionTest.java`

**Interfaces:**
- Produces: `PlanPhase`/`PlanStatus`/`TestType`/`PlanCommentKind` 枚举；`PersistentTaskPlanRecord` 新增 getter `getPhase()/getStatus()/getBody()/getRevision()/getPublishedAt()/getPrecheckJson()/getPrecheckExecutedAt()` 与方法 `forceState(PlanPhase,PlanStatus)`、`transitionTo(PlanPhase,PlanStatus)`、`updateBody(String)`（revision+1）、`applyPublish(Instant)`、`applyNewRevision()`、`markPrecheckExecuted(Instant)`；`PersistentTaskScenarioRecord` 新增 `getPurpose()/getTestType()` 与 `updateBusinessFields(String purpose, TestType testType)`、`bindScript(long scriptVersionId)`；`TaskPlan` record 尾部新增 7 个组件（后续所有任务按此签名）。
- Produces: 5 个异常类，均为 `RuntimeException` 子类、ctor `(String message)`，其中 `PlanStateException(String message, PlanPhase phase, PlanStatus status, java.util.List<String> allowedActions)` 与 `PlanRevisionConflictException(String message, int currentRevision, String serverMarkdown)` 带额外字段及 getter。

- [ ] **Step 1: 基线确认**

Run: `export JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ && ./gradlew :backend:test`
Expected: BUILD SUCCESSFUL（若本就失败，先停下报告，不带病开工）。

- [ ] **Step 2: 写失败测试**

```java
package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskScenarioRecord;
import com.yr.perftest.platform.task.TestType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PlanEntityExtensionTest {
    @Autowired
    private TestEntityManager entityManager;

    @Test
    void newPlanDefaultsToDraftPhaseAndRevisionOne() {
        PersistentTaskPlanRecord plan = entityManager.persistFlushFind(
                new PersistentTaskPlanRecord(1L, "plan-a", null, "admin"));
        assertThat(plan.getPhase()).isEqualTo(PlanPhase.DRAFT);
        assertThat(plan.getStatus()).isEqualTo(PlanStatus.DRAFT);
        assertThat(plan.getRevision()).isEqualTo(1);
        assertThat(plan.getBody()).isNull();
        assertThat(plan.getPublishedAt()).isNull();
        assertThat(plan.getPrecheckJson()).isNull();
        assertThat(plan.getPrecheckExecutedAt()).isNull();
    }

    @Test
    void updateBodyBumpsRevisionEachWrite() {
        PersistentTaskPlanRecord plan = entityManager.persist(new PersistentTaskPlanRecord(1L, "p", null, "a"));
        plan.updateBody("# 一、背景\n内容");
        assertThat(plan.getRevision()).isEqualTo(2);
        plan.updateBody("# 一、背景\n内容2");
        assertThat(plan.getRevision()).isEqualTo(3);
        assertThat(plan.getBody()).isEqualTo("# 一、背景\n内容2");
    }

    @Test
    void scenarioAcceptsNullScriptAndBusinessFields() {
        PersistentTaskPlanRecord plan = entityManager.persist(new PersistentTaskPlanRecord(1L, "p", null, "a"));
        PersistentTaskScenarioRecord scenario = entityManager.persistFlushFind(
                new PersistentTaskScenarioRecord(plan.getId(), null, "场景A", 0));
        assertThat(scenario.getScriptVersionId()).isNull();
        scenario.updateBusinessFields("验证单交易并发能力", TestType.SINGLE_TXN);
        scenario.bindScript(42L);
        assertThat(scenario.getPurpose()).isEqualTo("验证单交易并发能力");
        assertThat(scenario.getTestType()).isEqualTo(TestType.SINGLE_TXN);
        assertThat(scenario.getScriptVersionId()).isEqualTo(42L);
    }
}
```

- [ ] **Step 3: 跑测试确认失败**

Run: `export JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ && ./gradlew :backend:test --tests "com.yr.perftest.platform.task.plandoc.PlanEntityExtensionTest"`
Expected: 编译失败（PlanPhase/PlanStatus/TestType/getPhase 不存在）。

- [ ] **Step 4: 最小实现**

枚举（三个文件 + TestType，纯枚举无逻辑）：

```java
package com.yr.perftest.platform.task.plandoc;
public enum PlanPhase { DRAFT, REVIEW, EXECUTION, REPORT, PUBLISH }
```
```java
package com.yr.perftest.platform.task.plandoc;
public enum PlanStatus { DRAFT, PENDING, IN_REVIEW, APPROVED, RUNNING, DONE, GENERATING, PUBLISHED }
```
```java
package com.yr.perftest.platform.task.plandoc;
public enum PlanCommentKind { REVIEW, SYSTEM }
```
```java
package com.yr.perftest.platform.task;
/** 场景测试类型：BENCHMARK 基准 / SINGLE_TXN 单交易并发 / COMPOSITE 组合交易 / STABILITY 稳定性 */
public enum TestType { BENCHMARK, SINGLE_TXN, COMPOSITE, STABILITY }
```

异常（各自独立文件，package 均为 `com.yr.perftest.platform.task.plandoc`）：

```java
public class PlanStateException extends RuntimeException {
    private final PlanPhase phase;
    private final PlanStatus status;
    private final java.util.List<String> allowedActions;
    public PlanStateException(String message, PlanPhase phase, PlanStatus status, java.util.List<String> allowedActions) {
        super(message);
        this.phase = phase;
        this.status = status;
        this.allowedActions = java.util.List.copyOf(allowedActions);
    }
    public PlanPhase getPhase() { return phase; }
    public PlanStatus getStatus() { return status; }
    public java.util.List<String> getAllowedActions() { return allowedActions; }
}
```
```java
public class PlanRevisionConflictException extends RuntimeException {
    private final int currentRevision;
    private final String serverMarkdown;
    public PlanRevisionConflictException(String message, int currentRevision, String serverMarkdown) {
        super(message);
        this.currentRevision = currentRevision;
        this.serverMarkdown = serverMarkdown;
    }
    public int getCurrentRevision() { return currentRevision; }
    public String getServerMarkdown() { return serverMarkdown; }
}
```
```java
public class PlanPrecheckFailedException extends RuntimeException {
    private final java.util.List<String> failures;
    public PlanPrecheckFailedException(String message, java.util.List<String> failures) {
        super(message);
        this.failures = java.util.List.copyOf(failures);
    }
    public java.util.List<String> getFailures() { return failures; }
}
```
```java
public class PlanAccessDeniedException extends RuntimeException {
    public PlanAccessDeniedException(String message) { super(message); }
}
```
```java
public class PlanValidationException extends RuntimeException {
    public PlanValidationException(String message) { super(message); }
}
```

`PersistentTaskPlanRecord` 追加字段与方法（放在现有 `remark` 字段之后、构造器里初始化默认值；`PlanPhase/PlanStatus` 用 `@Enumerated(EnumType.STRING)`）：

```java
@Enumerated(EnumType.STRING)
@Column(nullable = false, length = 20)
private PlanPhase phase = PlanPhase.DRAFT;

@Enumerated(EnumType.STRING)
@Column(nullable = false, length = 20)
private PlanStatus status = PlanStatus.DRAFT;

@Lob
private String body;

@Column(nullable = false)
private int revision = 1;

private Instant publishedAt;

@Lob
private String precheckJson;

private Instant precheckExecutedAt;

public PlanPhase getPhase() { return phase; }
public PlanStatus getStatus() { return status; }
public String getBody() { return body; }
public int getRevision() { return revision; }
public Instant getPublishedAt() { return publishedAt; }
public String getPrecheckJson() { return precheckJson; }
public Instant getPrecheckExecutedAt() { return precheckExecutedAt; }

/** 仅供测试与数据订正直接置状态；正常流转走 PlanWorkflowService。 */
public void forceState(PlanPhase phase, PlanStatus status) {
    this.phase = phase;
    this.status = status;
    this.updatedAt = Instant.now();
}

/** 状态机流转写入（前置校验在 PlanWorkflowService）。 */
public void transitionTo(PlanPhase phase, PlanStatus status) {
    this.phase = phase;
    this.status = status;
    this.updatedAt = Instant.now();
}

/** 文档原文变更统一入口：body 变化必然 revision+1（设计 §5.1）。 */
public void updateBody(String body) {
    this.body = body;
    this.revision = this.revision + 1;
    this.updatedAt = Instant.now();
}

public void applyPublish(Instant publishedAt) {
    this.phase = PlanPhase.PUBLISH;
    this.status = PlanStatus.PUBLISHED;
    this.publishedAt = publishedAt;
    this.updatedAt = Instant.now();
}

public void applyNewRevision() {
    this.phase = PlanPhase.DRAFT;
    this.status = PlanStatus.DRAFT;
    this.revision = this.revision + 1;
    this.precheckExecutedAt = null;
    this.updatedAt = Instant.now();
}

public void markPrecheckExecuted(Instant at) {
    this.precheckExecutedAt = at;
    this.updatedAt = Instant.now();
}
```
（导入 `jakarta.persistence.Enumerated`、`jakarta.persistence.EnumType`、`com.yr.perftest.platform.task.plandoc.PlanPhase/PlanStatus`。）

`PersistentTaskScenarioRecord` 改造：`scriptVersionId` 去掉 `nullable = false`；追加：

```java
@Lob
private String purpose;

@Enumerated(EnumType.STRING)
@Column(length = 20)
private TestType testType;

public String getPurpose() { return purpose; }
public TestType getTestType() { return testType; }

public void updateBusinessFields(String purpose, TestType testType) {
    this.purpose = purpose;
    this.testType = testType;
    this.updatedAt = Instant.now();
}

public void bindScript(long scriptVersionId) {
    this.scriptVersionId = scriptVersionId;
    this.updatedAt = Instant.now();
}
```
（现有 `updateProfile` 的 `if (scriptVersionId != null)` 逻辑保持不变，兼容可空。）

`TaskPlan` record 替换为：

```java
package com.yr.perftest.platform.task;

import com.yr.perftest.platform.task.plandoc.PlanPhase;
import com.yr.perftest.platform.task.plandoc.PlanStatus;

import java.time.Instant;
import java.util.List;

public record TaskPlan(
        long id,
        long projectId,
        String name,
        String remark,
        String createdBy,
        Instant createdAt,
        Instant updatedAt,
        Long defaultControllerNodeId,
        List<Long> defaultWorkerNodeIds,
        List<Long> defaultMonitorTargetIds,
        long scenarioCount,
        PlanPhase phase,
        PlanStatus status,
        String body,
        int revision,
        Instant publishedAt,
        String precheckJson,
        Instant precheckExecutedAt
) {
}
```

`TaskPlanService.toPlan` 补齐新参数：

```java
private TaskPlan toPlan(PersistentTaskPlanRecord plan) {
    return new TaskPlan(
            plan.getId(),
            plan.getProjectId(),
            plan.getName(),
            plan.getRemark(),
            plan.getCreatedBy(),
            plan.getCreatedAt(),
            plan.getUpdatedAt(),
            plan.getDefaultControllerNodeId(),
            taskJson.readLongList(plan.getDefaultWorkerNodeIdsJson()),
            taskJson.readLongList(plan.getDefaultMonitorTargetIdsJson()),
            scenarioRepository.countByPlanId(plan.getId()),
            plan.getPhase(),
            plan.getStatus(),
            plan.getBody(),
            plan.getRevision(),
            plan.getPublishedAt(),
            plan.getPrecheckJson(),
            plan.getPrecheckExecutedAt()
    );
}
```

`docs/database/mysql-schema.sql`：`task_plans` 的 CREATE TABLE 里 `updated_at` 行后追加列定义；`task_scenarios` 的 `script_version_id` 行改为可空并追加两列（对齐设计 §11.1/§11.2）：

```sql
    -- task_plans 追加（放在 updated_at 之后）
    `phase`                         VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT '计划阶段（DRAFT/REVIEW/EXECUTION/REPORT/PUBLISH）',
    `status`                        VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT '阶段内子状态（见 PlanStatus）',
    `body`                          LONGTEXT              COMMENT 'Markdown 原文（唯一数据源，含全部章节）',
    `revision`                      INT          NOT NULL DEFAULT 1 COMMENT '文档修订号',
    `published_at`                  DATETIME(3)           COMMENT '发布时间',
    `precheck_json`                 LONGTEXT              COMMENT '环境检查执行设置 {enabled, items:[]}',
    `precheck_executed_at`          DATETIME(3)           COMMENT '首次执行环境检查运行时间（newRevision 重置）',
```
```sql
    -- task_scenarios：script_version_id 改为 BIGINT NULL，并在 monitor_target_ids_json 后追加
    `purpose`                        TEXT                  COMMENT '场景目的',
    `test_type`                      VARCHAR(20)           COMMENT '测试类型（BENCHMARK/SINGLE_TXN/COMPOSITE/STABILITY）',
```

- [ ] **Step 5: 跑测试确认通过 + 全量回归**

Run: `export JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ && ./gradlew :backend:test --tests "com.yr.perftest.platform.task.plandoc.PlanEntityExtensionTest" && ./gradlew :backend:test`
Expected: 均通过（record 扩展只影响 toPlan 一处构造）。

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/yr/perftest/platform/task backend/src/test/java/com/yr/perftest/platform/task/plandoc docs/database/mysql-schema.sql
git commit -m "feat：P0-1 Task1 计划/场景实体扩展——二级状态列、文档正文与 revision、脚本可空与业务字段"
```

---

### Task 2: PlanMarkdownSupport —— 章节解析/写回/场景块回填（纯静态，无 Spring）

**Files:**
- Create: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanMarkdownSupport.java`
- Test: `backend/src/test/java/com/yr/perftest/platform/task/plandoc/PlanMarkdownSupportTest.java`

**Interfaces:**
- Consumes: Task 1 的 `PlanValidationException`。
- Produces（后续任务全靠这些签名）：
  - `List<String> CANONICAL_HEADINGS`（11 个固定章节标题常量）
  - `record Section(String title, String content)` —— title 形如 `二、测试目的与指标`（不含 `## `）
  - `List<Section> splitSections(String body)`
  - `String extractSection(String body, String title)`（缺失返回 null）
  - `String replaceSection(String body, String title, String newContent)`（缺失抛 `PlanValidationException`；newContent 不含标题行）
  - `String ensureSection(String body, String title, String content)`（存在则替换，缺失则按 CANONICAL 顺序插入或追加到末尾）
  - `String appendExecutionRecord(String body, String scenarioName, long executionId, String entryLine)`（幂等；场景块缺失时在七章节末尾新建块）
  - `List<String> parseExecutionRecords(String body, String scenarioName)`（读"最新执行"列用）
  - `String renderTemplate(String templateMarkdown, String planName)`（替换 `{{planName}}`）
  - `List<String> parseChecklistItems(String sectionContent)`（解析 `- [ ] 文本（自动/人工）` 清单，返回条目文本）
  - `String upsertScenarioFacts(String body, String scenarioName, String generatedBlock)`（Task 7 场景章节回写：替换标题行/场景目的行/场景设置表三处"事实"，保留两处之间的自由文本与 `#### 执行记录` 小节；块缺失则追加新块）
  - `String removeScenarioBlock(String body, String scenarioName)`（Task 7 场景删除时整块移除）
  - `String replaceScenarioBusinessBlock(String body, String scenarioName, String generatedBlock)`（`upsertScenarioFacts` 的兜底路径：块结构不符约定时整块替换业务部分，仍保留执行记录小节）

- [ ] **Step 1: 写失败测试**

```java
package com.yr.perftest.platform.task.plandoc;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanMarkdownSupportTest {
    private static final String BODY = """
            ## 一、背景

            系统升级后需评估容量。

            ## 二、测试目的与指标

            | 交易 | 指标 | 目标值 | 口径 |
            |---|---|---|---|
            | 查询 | TPS | 200 | 5分钟均值 |

            ## 五、测试约束

            ### 入口准则

            - [ ] 指标已定义（自动）
            - [ ] 环境就绪（人工）

            ### 出口准则

            - [ ] 全部场景通过（人工）

            ## 七、场景设计

            ### S1 登录场景 · SINGLE_TXN

            **场景目的**：验证登录并发

            **测试方法**：逐步加压观察拐点

            **场景设置**（由场景执行配置生成，勿手改）：

            | 用户数 | 持续时长 | 加载方式 | 退出方式 |
            |---|---|---|---|
            | 50 | 300 秒 | 匀速加载 30 秒 | 同时退出 |

            #### 执行记录
            """;

    @Test
    void splitsSectionsByCanonicalHeading() {
        List<PlanMarkdownSupport.Section> sections = PlanMarkdownSupport.splitSections(BODY);
        assertThat(sections).extracting(PlanMarkdownSupport.Section::title)
                .containsExactly("一、背景", "二、测试目的与指标", "五、测试约束", "七、场景设计");
        assertThat(sections.get(0).content()).contains("系统升级后需评估容量");
    }

    @Test
    void extractsAndReplacesSectionContent() {
        String replaced = PlanMarkdownSupport.replaceSection(BODY, "二、测试目的与指标", "\n| 交易 | 指标 | 目标值 |\n|---|---|---|\n| 转账 | TPS | 300 |\n");
        assertThat(PlanMarkdownSupport.extractSection(replaced, "二、测试目的与指标")).contains("转账");
        assertThat(PlanMarkdownSupport.extractSection(replaced, "一、背景")).contains("系统升级");
        assertThatThrownBy(() -> PlanMarkdownSupport.replaceSection(BODY, "十二、不存在", "x"))
                .isInstanceOf(PlanValidationException.class);
    }

    @Test
    void appendsExecutionRecordIdempotentlyInsideScenarioBlock() {
        String first = PlanMarkdownSupport.appendExecutionRecord(BODY, "登录场景", 101L,
                "- 2026-09-04 14:30 · 50 并发 · SUCCESS · 吞吐 158.3 TPS · P95 96 ms · 错误率 0.42%");
        String second = PlanMarkdownSupport.appendExecutionRecord(first, "登录场景", 101L, "- 重复条目");
        assertThat(second).isEqualTo(first);
        assertThat(PlanMarkdownSupport.parseExecutionRecords(first, "登录场景")).hasSize(1);
        String other = PlanMarkdownSupport.appendExecutionRecord(first, "登录场景", 102L, "- 2026-09-04 15:00 · SUCCESS");
        assertThat(PlanMarkdownSupport.parseExecutionRecords(other, "登录场景")).hasSize(2);
        // 条目必须落在场景块内的 #### 执行记录 下，而不是章节末尾之外
        int blockStart = other.indexOf("### S1 登录场景");
        int marker = other.indexOf("<!-- backfill:execution:102 -->");
        assertThat(blockStart).isLessThan(marker);
        assertThat(other.indexOf("## 八、")).isEqualTo(-1); // 本文档无八章节
        assertThat(marker).isGreaterThan(other.indexOf("#### 执行记录", blockStart));
    }

    @Test
    void parsesChecklistItems() {
        List<String> items = PlanMarkdownSupport.parseChecklistItems(PlanMarkdownSupport.extractSection(BODY, "五、测试约束"));
        assertThat(items).containsExactly("指标已定义（自动）", "环境就绪（人工）", "全部场景通过（人工）");
    }

    @Test
    void rendersTemplatePlaceholder() {
        assertThat(PlanMarkdownSupport.renderTemplate("# {{planName}} 计划", "零售3.1")).isEqualTo("# 零售3.1 计划");
    }

    @Test
    void upsertsScenarioFactsKeepingFreeTextAndExecutionRecords() {
        String generated = """
                ### S1 登录场景 · BENCHMARK

                **场景目的**：改后的目的

                **测试方法**：（实体同步不触碰此处）

                **场景设置**（由场景执行配置生成，勿手改）：

                | 用户数 | 持续时长 | 加载方式 | 退出方式 |
                |---|---|---|---|
                | 100 | 300 秒 | 匀速加载 30 秒 | 同时退出 |
                """;
        String replaced = PlanMarkdownSupport.upsertScenarioFacts(BODY, "登录场景", generated);
        assertThat(replaced).contains("### S1 登录场景 · BENCHMARK");
        assertThat(replaced).contains("**场景目的**：改后的目的");
        assertThat(replaced).contains("| 100 | 300 秒 |");          // 设置表已更新
        assertThat(replaced).contains("**测试方法**：逐步加压观察拐点"); // 自由文本保留
        assertThat(replaced).doesNotContain("SINGLE_TXN");           // 旧标题被替换
        assertThat(replaced).contains("#### 执行记录");
    }

    @Test
    void upsertAppendsNewBlockWhenScenarioMissing() {
        String generated = "### S2 新场景 · STABILITY\n\n**场景目的**：新\n\n**场景设置**（由场景执行配置生成，勿手改）：\n\n| 用户数 | 持续时长 | 加载方式 | 退出方式 |\n|---|---|---|---|\n| 30 | 1800 秒 | 同时加载 | 同时退出 |\n";
        String replaced = PlanMarkdownSupport.upsertScenarioFacts(BODY, "新场景", generated);
        assertThat(replaced).contains("### S2 新场景 · STABILITY");
        assertThat(replaced).contains("#### 执行记录");
    }

    @Test
    void removesScenarioBlockEntirely() {
        String removed = PlanMarkdownSupport.removeScenarioBlock(BODY, "登录场景");
        assertThat(removed).doesNotContain("登录场景");
        assertThat(removed).contains("## 七、场景设计");
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `export JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ && ./gradlew :backend:test --tests "com.yr.perftest.platform.task.plandoc.PlanMarkdownSupportTest"`
Expected: 编译失败（类不存在）。

- [ ] **Step 3: 实现（完整类）**

```java
package com.yr.perftest.platform.task.plandoc;

import java.util.ArrayList;
import java.util.List;

/** Markdown 原文的结构操作：章节定位/替换、场景块回填、清单解析。纯静态、无状态。 */
public final class PlanMarkdownSupport {

    public static final List<String> CANONICAL_HEADINGS = List.of(
            "一、背景", "二、测试目的与指标", "三、测试范围", "四、测试资源", "五、测试约束",
            "六、测试策略", "七、场景设计", "八、风险与预案", "九、排期与协作", "十、附录", "十一、结论");

    private static final String EXECUTION_RECORD_HEADING = "#### 执行记录";

    private PlanMarkdownSupport() {
    }

    public record Section(String title, String content) {
    }

    public static List<Section> splitSections(String body) {
        List<Section> sections = new ArrayList<>();
        if (body == null || body.isBlank()) {
            return sections;
        }
        String[] lines = body.split("\n", -1);
        String currentTitle = null;
        StringBuilder current = new StringBuilder();
        for (String line : lines) {
            String title = canonicalTitleOf(line);
            if (title != null) {
                if (currentTitle != null) {
                    sections.add(new Section(currentTitle, current.toString()));
                }
                currentTitle = title;
                current = new StringBuilder();
            } else if (currentTitle != null) {
                current.append(line).append('\n');
            }
        }
        if (currentTitle != null) {
            sections.add(new Section(currentTitle, current.toString()));
        }
        return sections;
    }

    public static String extractSection(String body, String title) {
        int[] bounds = sectionBounds(body, title);
        if (bounds == null) {
            return null;
        }
        return body.substring(bounds[0], bounds[1]);
    }

    public static String replaceSection(String body, String title, String newContent) {
        int[] bounds = sectionBounds(body, title);
        if (bounds == null) {
            throw new PlanValidationException("PLAN_INVALID：文档缺少章节「" + title + "」，无法写回");
        }
        String normalized = newContent == null ? "\n" : (newContent.endsWith("\n") ? newContent : newContent + "\n");
        return body.substring(0, bounds[0]) + normalized + body.substring(bounds[1]);
    }

    public static String ensureSection(String body, String title, String content) {
        if (sectionBounds(body, title) != null) {
            return replaceSection(body, title, content);
        }
        String base = body == null ? "" : body;
        String heading = "\n## " + title + "\n" + (content == null ? "\n" : content.endsWith("\n") ? content : content + "\n");
        int insertAt = canonicalInsertIndex(base, title);
        if (insertAt < 0) {
            return base + heading;
        }
        return base.substring(0, insertAt) + heading.trim() + "\n" + base.substring(insertAt);
    }

    public static String appendExecutionRecord(String body, String scenarioName, long executionId, String entryLine) {
        String marker = "<!-- backfill:execution:" + executionId + " -->";
        int[] block = scenarioBlockBounds(body, scenarioName);
        if (block == null) {
            String generated = "### S? " + scenarioName + " · UNKNOWN\n\n**场景目的**：（待补充）\n\n"
                    + EXECUTION_RECORD_HEADING + "\n";
            String withBlock = ensureSection(body, "七、场景设计", generated);
            block = scenarioBlockBounds(withBlock, scenarioName);
            body = withBlock;
        }
        String blockText = body.substring(block[0], block[1]);
        if (blockText.contains(marker)) {
            return body; // 幂等：场景块内查重（设计 §8.1）
        }
        int recordHeading = blockText.indexOf(EXECUTION_RECORD_HEADING);
        if (recordHeading < 0) {
            String before = body.substring(0, block[1]);
            String after = body.substring(block[1]);
            return before + EXECUTION_RECORD_HEADING + "\n" + marker + "\n" + entryLine + "\n" + after;
        }
        int insertAt = block[0] + blockText.length(); // 追加到块尾（即执行记录小节末尾）
        return body.substring(0, insertAt) + marker + "\n" + entryLine + "\n" + body.substring(insertAt);
    }

    public static List<String> parseExecutionRecords(String body, String scenarioName) {
        int[] block = scenarioBlockBounds(body, scenarioName);
        if (block == null) {
            return List.of();
        }
        List<String> entries = new ArrayList<>();
        for (String line : body.substring(block[0], block[1]).split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("- ") && !trimmed.startsWith("- [ ]") && !trimmed.startsWith("- [x]")) {
                entries.add(trimmed.substring(2));
            }
        }
        return entries;
    }

    public static String renderTemplate(String templateMarkdown, String planName) {
        if (templateMarkdown == null) {
            return null;
        }
        return templateMarkdown.replace("{{planName}}", planName == null ? "" : planName);
    }

    public static List<String> parseChecklistItems(String sectionContent) {
        List<String> items = new ArrayList<>();
        if (sectionContent == null) {
            return items;
        }
        for (String line : sectionContent.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("- [ ] ") || trimmed.startsWith("- [x] ")) {
                items.add(trimmed.substring(6).trim());
            }
        }
        return items;
    }

    public static String replaceScenarioBusinessBlock(String body, String scenarioName, String generatedBlock) {
        int[] block = scenarioBlockBounds(body, scenarioName);
        if (block == null) {
            return appendExecutionlessBlock(body, generatedBlock);
        }
        String blockText = body.substring(block[0], block[1]);
        int recordHeading = blockText.indexOf(EXECUTION_RECORD_HEADING);
        if (recordHeading < 0) {
            return body.substring(0, block[0]) + generatedBlock + body.substring(block[1]);
        }
        String preserved = blockText.substring(recordHeading); // #### 执行记录 及其后内容原样保留
        return body.substring(0, block[0]) + generatedBlock + preserved + body.substring(block[1]);
    }

    /**
     * 场景事实回写：只替换 标题行 / 场景目的行 / 场景设置表 三处实体事实，
     * 保留两处之间的自由文本（测试方法/交易范围）与执行记录小节（设计 §3.1"场景章节由实体渲染生成并随实体变更回写"）。
     * 块缺失 → 追加新块；块内缺少约定标记 → 兜底整块替换业务部分。
     */
    public static String upsertScenarioFacts(String body, String scenarioName, String generatedBlock) {
        int[] block = scenarioBlockBounds(body, scenarioName);
        if (block == null) {
            return appendExecutionlessBlock(body, generatedBlock);
        }
        String blockText = body.substring(block[0], block[1]);
        String genHeading = firstLine(generatedBlock);
        String genPurpose = markerLine(generatedBlock, "**场景目的**");
        String genSettings = settingsRegion(generatedBlock);
        if (genPurpose == null || genSettings == null
                || !blockText.contains("**场景目的**") || !blockText.contains("**场景设置**")) {
            return replaceScenarioBusinessBlock(body, scenarioName, generatedBlock);
        }
        StringBuilder out = new StringBuilder();
        boolean inSettings = false;
        for (String line : blockText.split("\n", -1)) {
            if (line.startsWith("### ")) {
                out.append(genHeading).append('\n');
            } else if (line.startsWith("**场景目的**")) {
                out.append(genPurpose).append('\n');
            } else if (line.startsWith("**场景设置**")) {
                out.append(genSettings);
                if (!genSettings.endsWith("\n")) {
                    out.append('\n');
                }
                inSettings = true;
            } else if (line.startsWith("#### 执行记录")) {
                inSettings = false;
                out.append(line).append('\n');
            } else if (!inSettings) {
                out.append(line).append('\n'); // 自由文本与执行记录内容原样保留
            }
        }
        String rebuilt = out.toString().stripTrailing();
        return body.substring(0, block[0]) + rebuilt + "\n" + body.substring(block[1]);
    }

    public static String removeScenarioBlock(String body, String scenarioName) {
        int[] block = scenarioBlockBounds(body, scenarioName);
        if (block == null) {
            return body;
        }
        return body.substring(0, block[0]) + body.substring(block[1]);
    }

    private static String firstLine(String text) {
        int i = text.indexOf('\n');
        return i < 0 ? text : text.substring(0, i);
    }

    private static String markerLine(String text, String marker) {
        for (String line : text.split("\n", -1)) {
            if (line.startsWith(marker)) {
                return line;
            }
        }
        return null;
    }

    /** generatedBlock 中从 **场景设置** 标记行到块尾的区域。 */
    private static String settingsRegion(String generatedBlock) {
        int i = generatedBlock.indexOf("**场景设置**");
        return i < 0 ? null : generatedBlock.substring(i);
    }

    private static String appendExecutionlessBlock(String body, String generatedBlock) {
        String section = extractSection(body, "七、场景设计");
        String block = generatedBlock + "\n" + EXECUTION_RECORD_HEADING + "\n";
        if (section == null) {
            return ensureSection(body, "七、场景设计", "\n" + block);
        }
        return replaceSection(body, "七、场景设计", section + block);
    }

    /** 返回 [contentStart, contentEnd)：标题行之后到下一 `## ` 标题行之前。 */
    private static int[] sectionBounds(String body, String title) {
        if (body == null) {
            return null;
        }
        String[] lines = body.split("\n", -1);
        int lineStart = 0;
        int contentStart = -1;
        for (String line : lines) {
            if (contentStart < 0) {
                if (canonicalTitleOf(line) != null && canonicalTitleOf(line).equals(title)) {
                    contentStart = lineStart + line.length() + 1;
                }
            } else if (line.startsWith("## ")) {
                return new int[]{contentStart, lineStart};
            }
            lineStart += line.length() + 1;
        }
        return contentStart < 0 ? null : new int[]{contentStart, body.length()};
    }

    /** 场景块 = 七章节内以 `### ` 开头且包含场景名的行，到下一 `### `/`## ` 或文末。 */
    private static int[] scenarioBlockBounds(String body, String scenarioName) {
        int[] section = sectionBounds(body, "七、场景设计");
        if (section == null) {
            return null;
        }
        String sectionText = body.substring(section[0], section[1]);
        int offset = section[0];
        String[] lines = sectionText.split("\n", -1);
        int lineStart = 0;
        int blockStart = -1;
        for (String line : lines) {
            if (line.startsWith("### ")) {
                if (blockStart >= 0) {
                    return new int[]{blockStart, offset + lineStart};
                }
                if (line.contains(scenarioName)) {
                    blockStart = offset + lineStart;
                }
            }
            lineStart += line.length() + 1;
        }
        return blockStart < 0 ? null : new int[]{blockStart, section[1]};
    }

    /** `## 二、测试目的与指标` → `二、测试目的与指标`；非规范标题返回 null。 */
    private static String canonicalTitleOf(String line) {
        if (line == null || !line.startsWith("## ")) {
            return null;
        }
        String text = line.substring(3).trim();
        for (String heading : CANONICAL_HEADINGS) {
            if (text.equals(heading) || (text.startsWith(heading) && text.length() > heading.length()
                    && isSeparator(text.charAt(heading.length())))) {
                return heading;
            }
            // 容错：仅序号前缀匹配（如「## 二、xxx」改名场景），按序号取第一个规范标题
            String numeral = heading.substring(0, heading.indexOf('、') + 1);
            if (!numeral.equals("十一、") && text.startsWith(numeral)) {
                return heading;
            }
        }
        return null;
    }

    private static boolean isSeparator(char c) {
        return c == ' ' || c == '　' || c == '：' || c == ':' || c == '-';
    }

    private static int canonicalInsertIndex(String body, String title) {
        int target = CANONICAL_HEADINGS.indexOf(title);
        for (int i = target + 1; i < CANONICAL_HEADINGS.size(); i++) {
            int[] bounds = sectionBounds(body, CANONICAL_HEADINGS.get(i));
            if (bounds != null) {
                return body.lastIndexOf("## " + CANONICAL_HEADINGS.get(i));
            }
        }
        return -1;
    }
}
```

注意 `canonicalInsertIndex` 依赖 `sectionBounds` 返回的标题存在性，`body.lastIndexOf(...)` 找到的就是该标题行首——如出现偏差（标题行前后有空格），以测试为准修正，不要引入正则。

- [ ] **Step 4: 跑测试确认通过**

Run: `export JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ && ./gradlew :backend:test --tests "com.yr.perftest.platform.task.plandoc.PlanMarkdownSupportTest"`
Expected: PASS（8 个用例）。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanMarkdownSupport.java backend/src/test/java/com/yr/perftest/platform/task/plandoc/PlanMarkdownSupportTest.java
git commit -m "feat：P0-1 Task2 PlanMarkdownSupport——章节切分/替换、场景块执行记录幂等回填、清单解析"
```

---

### Task 3: ProjectAccessResolver + PlanAccess 权限矩阵

**Files:**
- Create: `backend/src/main/java/com/yr/perftest/platform/project/ProjectAccessResolver.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanAccess.java`
- Test: `backend/src/test/java/com/yr/perftest/platform/task/plandoc/PlanAccessTest.java`

**Interfaces:**
- Consumes: `identity/HumanPrincipal(String username, Set<SystemRole> roles)`、`project/PersistentProjectRepository`、`project/PersistentProjectMemberRepository`（`findByProjectIdAndUsername`）、`PersistentProjectRecord.getOwnerUsername()`（已存在）。
- Produces:
  - `enum ProjectAccessResolver.PlanActorRole { SYSTEM_ADMIN, PROJECT_OWNER, PLAN_OWNER, MEMBER, NONE }`
  - `ProjectAccessResolver.resolve(long projectId, HumanPrincipal principal, String planCreatedBy)` → `PlanActorRole`
  - `PlanAccess.compute(PlanActorRole role, PlanPhase phase, PlanStatus status, boolean hasAnyExecution)` → `java.util.Map<String,Boolean>`（key 为 Global Constraints 的 17 个动作键）

- [ ] **Step 1: 写失败测试**

```java
package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.project.ProjectAccessResolver.PlanActorRole;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlanAccessTest {

    @Test
    void memberCanReviewAndExecuteButNotEditOrPublish() {
        Map<String, Boolean> p = PlanAccess.compute(PlanActorRole.MEMBER, PlanPhase.REVIEW, PlanStatus.IN_REVIEW, false);
        assertThat(p.get("START_REVIEW")).isTrue();
        assertThat(p.get("APPROVE")).isTrue();
        assertThat(p.get("REJECT")).isTrue();
        assertThat(p.get("COMMENT")).isTrue();
        assertThat(p.get("EDIT")).isFalse();
        assertThat(p.get("SUBMIT")).isFalse();
        assertThat(p.get("PUBLISH")).isFalse();
        assertThat(p.get("DELETE")).isFalse();
        assertThat(p.get("SHARE")).isFalse();
    }

    @Test
    void ownerCanEditOnlyInDraftPhase() {
        assertThat(PlanAccess.compute(PlanActorRole.PLAN_OWNER, PlanPhase.DRAFT, PlanStatus.DRAFT, false).get("EDIT")).isTrue();
        assertThat(PlanAccess.compute(PlanActorRole.PLAN_OWNER, PlanPhase.REVIEW, PlanStatus.PENDING, false).get("EDIT")).isFalse();
        assertThat(PlanAccess.compute(PlanActorRole.PLAN_OWNER, PlanPhase.EXECUTION, PlanStatus.DONE, true).get("EDIT")).isFalse();
        assertThat(PlanAccess.compute(PlanActorRole.PLAN_OWNER, PlanPhase.PUBLISH, PlanStatus.PUBLISHED, true).get("EDIT")).isFalse();
    }

    @Test
    void backToDraftRequiresNoExecution() {
        assertThat(PlanAccess.compute(PlanActorRole.PLAN_OWNER, PlanPhase.EXECUTION, PlanStatus.PENDING, false).get("BACK_TO_DRAFT")).isTrue();
        assertThat(PlanAccess.compute(PlanActorRole.PLAN_OWNER, PlanPhase.EXECUTION, PlanStatus.PENDING, true).get("BACK_TO_DRAFT")).isFalse();
        assertThat(PlanAccess.compute(PlanActorRole.PLAN_OWNER, PlanPhase.REVIEW, PlanStatus.APPROVED, false).get("BACK_TO_DRAFT")).isTrue();
    }

    @Test
    void publishRequiresReportDone() {
        assertThat(PlanAccess.compute(PlanActorRole.PLAN_OWNER, PlanPhase.REPORT, PlanStatus.DONE, true).get("PUBLISH")).isTrue();
        assertThat(PlanAccess.compute(PlanActorRole.PLAN_OWNER, PlanPhase.REPORT, PlanStatus.PENDING, true).get("PUBLISH")).isFalse();
        assertThat(PlanAccess.compute(PlanActorRole.MEMBER, PlanPhase.REPORT, PlanStatus.DONE, true).get("PUBLISH")).isFalse();
    }

    @Test
    void newRevisionOnlyFromPublishedForOwner() {
        assertThat(PlanAccess.compute(PlanActorRole.PLAN_OWNER, PlanPhase.PUBLISH, PlanStatus.PUBLISHED, true).get("NEW_REVISION")).isTrue();
        assertThat(PlanAccess.compute(PlanActorRole.MEMBER, PlanPhase.PUBLISH, PlanStatus.PUBLISHED, true).get("NEW_REVISION")).isFalse();
    }

    @Test
    void nonMemberHasNothing() {
        Map<String, Boolean> p = PlanAccess.compute(PlanActorRole.NONE, PlanPhase.DRAFT, PlanStatus.DRAFT, false);
        assertThat(p.values()).allMatch(v -> !v);
    }
}
```

（`ProjectAccessResolver.resolve` 的数据库路径在 Task 5 的集成测试里覆盖，这里只测纯矩阵。）

- [ ] **Step 2: 跑测试确认失败**

Run: `export JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ && ./gradlew :backend:test --tests "com.yr.perftest.platform.task.plandoc.PlanAccessTest"`
Expected: 编译失败。

- [ ] **Step 3: 实现**

```java
package com.yr.perftest.platform.project;

import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.identity.SystemRole;
import com.yr.perftest.platform.project.ProjectAccessResolver.PlanActorRole;
import org.springframework.stereotype.Service;

/** 计划动作的角色解析（设计 §13.1）：ADMIN > 项目 OWNER > 计划负责人 > 成员 > 无。 */
@Service
public class ProjectAccessResolver {

    public enum PlanActorRole { SYSTEM_ADMIN, PROJECT_OWNER, PLAN_OWNER, MEMBER, NONE }

    private final PersistentProjectRepository projectRepository;
    private final PersistentProjectMemberRepository memberRepository;

    public ProjectAccessResolver(PersistentProjectRepository projectRepository,
                                 PersistentProjectMemberRepository memberRepository) {
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
    }

    public PlanActorRole resolve(long projectId, HumanPrincipal principal, String planCreatedBy) {
        if (principal == null || principal.username() == null) {
            return PlanActorRole.NONE;
        }
        if (principal.roles().contains(SystemRole.ADMIN)) {
            return PlanActorRole.SYSTEM_ADMIN;
        }
        String username = principal.username();
        if (projectRepository.findById(projectId)
                .map(project -> username.equals(project.getOwnerUsername()))
                .orElse(false)) {
            return PlanActorRole.PROJECT_OWNER;
        }
        boolean isMember = memberRepository.findByProjectIdAndUsername(projectId, username).isPresent();
        if (!isMember) {
            return PlanActorRole.NONE; // 负责人以成员身份为前提（设计 §13.1 第 4 条）
        }
        if (username.equals(planCreatedBy)) {
            return PlanActorRole.PLAN_OWNER;
        }
        return PlanActorRole.MEMBER;
    }
}
```

```java
package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.project.ProjectAccessResolver.PlanActorRole;

import java.util.LinkedHashMap;
import java.util.Map;

/** 动作可见性矩阵（设计 §13.2 + §4.4 + §4.5）。键集合见 Global Constraints。 */
public final class PlanAccess {

    private PlanAccess() {
    }

    public static Map<String, Boolean> compute(PlanActorRole role, PlanPhase phase, PlanStatus status, boolean hasAnyExecution) {
        boolean ownerLike = role == PlanActorRole.SYSTEM_ADMIN
                || role == PlanActorRole.PROJECT_OWNER
                || role == PlanActorRole.PLAN_OWNER;
        boolean memberLike = ownerLike || role == PlanActorRole.MEMBER;
        boolean editable = ownerLike && phase == PlanPhase.DRAFT;
        boolean frozen = phase == PlanPhase.PUBLISH;

        Map<String, Boolean> p = new LinkedHashMap<>();
        p.put("EDIT", editable);
        p.put("SUBMIT", ownerLike && phase == PlanPhase.DRAFT);
        p.put("START_REVIEW", memberLike && phase == PlanPhase.REVIEW && status == PlanStatus.PENDING);
        p.put("APPROVE", memberLike && phase == PlanPhase.REVIEW && status == PlanStatus.IN_REVIEW);
        p.put("REJECT", memberLike && phase == PlanPhase.REVIEW && status == PlanStatus.IN_REVIEW);
        p.put("WITHDRAW", ownerLike && phase == PlanPhase.REVIEW && (status == PlanStatus.PENDING || status == PlanStatus.IN_REVIEW));
        p.put("BACK_TO_DRAFT", ownerLike && !hasAnyExecution
                && ((phase == PlanPhase.REVIEW && status == PlanStatus.APPROVED)
                || (phase == PlanPhase.EXECUTION && status == PlanStatus.PENDING)));
        p.put("START_EXECUTION", memberLike && phase == PlanPhase.REVIEW && status == PlanStatus.APPROVED);
        p.put("TO_REPORT", memberLike && phase == PlanPhase.EXECUTION && status == PlanStatus.DONE);
        p.put("GENERATE_REPORT", memberLike && phase == PlanPhase.REPORT && (status == PlanStatus.PENDING || status == PlanStatus.DONE));
        p.put("PUBLISH", ownerLike && phase == PlanPhase.REPORT && status == PlanStatus.DONE);
        p.put("NEW_REVISION", ownerLike && frozen);
        p.put("PRECHECK_RUN", memberLike && !frozen);
        p.put("PRECHECK_SKIP", memberLike && !frozen);
        p.put("DELETE", ownerLike);
        p.put("COMMENT", memberLike && (phase == PlanPhase.DRAFT || phase == PlanPhase.REVIEW));
        p.put("SHARE", ownerLike && frozen);
        return p;
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `export JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ && ./gradlew :backend:test --tests "com.yr.perftest.platform.task.plandoc.PlanAccessTest"`
Expected: PASS（6 个用例）。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/yr/perftest/platform/project/ProjectAccessResolver.java backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanAccess.java backend/src/test/java/com/yr/perftest/platform/task/plandoc/PlanAccessTest.java
git commit -m "feat：P0-1 Task3 计划域角色解析与 17 动作权限矩阵"
```

---

### Task 4: PlanDocumentService —— 文档读写、revision 冲突、批注实体

**Files:**
- Create: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PersistentPlanCommentRecord.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PersistentPlanCommentRepository.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanDocumentService.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/PersistentScenarioExecutionRepository.java`（加一个查询方法）
- Test: `backend/src/test/java/com/yr/perftest/platform/task/plandoc/PlanDocumentServiceTest.java`

**Interfaces:**
- Consumes: Task 1 实体方法、Task 2 `PlanMarkdownSupport`、Task 3 `ProjectAccessResolver`/`PlanActorRole`。
- Produces:
  - `PersistentScenarioExecutionRepository.countByScenarioIdInAndStatusIn(List<Long> scenarioIds, List<ExecutionStatus> statuses)` → `long`
  - `record CommentView(long id, long planId, String author, String content, PlanCommentKind kind, Instant createdAt)`（放 `PlanWorkflowService` 所在文件外不行——Java 一文件一公共类，放独立文件 `PlanCommentView.java`？不，用嵌套 record：`PlanWorkflowService.CommentView`。本任务先建 `PersistentPlanCommentRecord(planId, author, content, kind)` + repo `findAllByPlanIdOrderByIdAsc(Long planId)` / `deleteAllByPlanId(Long planId)`）
  - `PlanDocumentService`：
    - `TaskPlan getDocument(long planId)`（含惰性纠偏，见下）
    - `TaskPlan updateMarkdown(long planId, long baseRevision, String markdown, HumanPrincipal actor)`
    - `String renderInitialBody(Long templateId, String planName)`（Task 6 模板表就绪前先返回 null——本任务不调用它，签名占位于 Task 6 实现？**不行，无占位**：本任务先不引入该方法，Task 6 加。）
    - `void backfillExecutionRecord(long planId, String scenarioName, long executionId, String entryLine)`（系统写入：不校验 baseRevision/阶段，revision+1）
    - `void correctExecutionState(long planId)`（惰性纠偏：`EXECUTION/RUNNING` 且无活跃执行 → `EXECUTION/DONE`；`EXECUTION/PENDING|DONE` 且有活跃执行 → `EXECUTION/RUNNING`）

- [ ] **Step 1: 写失败测试**

```java
package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.execution.ExecutionStatus;
import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.identity.SystemRole;
import com.yr.perftest.platform.project.PersistentProjectMemberRecord;
import com.yr.perftest.platform.project.PersistentProjectRecord;
import com.yr.perftest.platform.project.PersistentProjectRepository;
import com.yr.perftest.platform.project.PersistentProjectMemberRepository;
import com.yr.perftest.platform.project.ProjectRole;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRepository;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.TaskPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-doc-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PlanDocumentServiceTest {

    private static final HumanPrincipal OWNER = new HumanPrincipal("owner", java.util.Set.of(SystemRole.PROJECT_MEMBER));
    private static final HumanPrincipal OTHER_MEMBER = new HumanPrincipal("member-b", java.util.Set.of(SystemRole.PROJECT_MEMBER));

    @Autowired
    private PlanDocumentService documentService;
    @Autowired
    private PersistentTaskPlanRepository planRepository;
    @Autowired
    private PersistentProjectRepository projectRepository;
    @Autowired
    private PersistentProjectMemberRepository memberRepository;
    @Autowired
    private PersistentScenarioExecutionRepository executionRepository;

    private long planId;

    @BeforeEach
    void setUp() {
        PersistentProjectRecord project = projectRepository.save(
                new PersistentProjectRecord("P1", "项目一", "", "owner"));
        memberRepository.save(new PersistentProjectMemberRecord(project.getId(), "owner", ProjectRole.OWNER));
        memberRepository.save(new PersistentProjectMemberRecord(project.getId(), "member-b", ProjectRole.MEMBER));
        PersistentTaskPlanRecord plan = planRepository.save(
                new PersistentTaskPlanRecord(project.getId(), "计划一", null, "owner"));
        plan.updateBody("## 一、背景\n\n初始内容\n");
        planId = planRepository.save(plan).getId();
    }

    @Test
    void updateMarkdownWithFreshBaseSucceedsAndBumpsRevision() {
        TaskPlan updated = documentService.updateMarkdown(planId, 2, "## 一、背景\n\n新内容\n", OWNER);
        assertThat(updated.revision()).isEqualTo(3);
        assertThat(updated.body()).contains("新内容");
    }

    @Test
    void staleBaseThrowsConflictWithServerMarkdown() {
        try {
            documentService.updateMarkdown(planId, 1, "## 一、背景\n\n旧基线\n", OWNER);
            throw new AssertionError("expected PlanRevisionConflictException");
        } catch (PlanRevisionConflictException conflict) {
            assertThat(conflict.getCurrentRevision()).isEqualTo(2);
            assertThat(conflict.getServerMarkdown()).contains("初始内容");
        }
    }

    @Test
    void editOutsideDraftPhaseRejected() {
        PersistentTaskPlanRecord plan = planRepository.findById(planId).orElseThrow();
        plan.forceState(PlanPhase.REVIEW, PlanStatus.PENDING);
        planRepository.save(plan);
        assertThatThrownBy(() -> documentService.updateMarkdown(planId, 2, "x", OWNER))
                .isInstanceOf(PlanStateException.class);
    }

    @Test
    void memberOtherThanOwnerCannotEdit() {
        assertThatThrownBy(() -> documentService.updateMarkdown(planId, 2, "x", OTHER_MEMBER))
                .isInstanceOf(PlanAccessDeniedException.class);
    }

    @Test
    void backfillIsSystemWriteNoBaseRevisionAndIdempotent() {
        documentService.backfillExecutionRecord(planId, "场景A", 1001L, "- 2026-09-04 10:00 · 50 并发 · SUCCESS · 吞吐 100 TPS");
        documentService.backfillExecutionRecord(planId, "场景A", 1001L, "- 重复");
        TaskPlan plan = documentService.getDocument(planId);
        assertThat(plan.body()).contains("<!-- backfill:execution:1001 -->");
        assertThat(plan.body()).contains("吞吐 100 TPS");
        assertThat(plan.body()).doesNotContain("- 重复");
        assertThat(plan.revision()).isEqualTo(3); // 初建 +1，第二次幂等不 bump
    }

    @Test
    void lazyCorrectionDemotesRunningToDoneWhenNoActiveExecution() {
        PersistentTaskPlanRecord plan = planRepository.findById(planId).orElseThrow();
        plan.forceState(PlanPhase.EXECUTION, PlanStatus.RUNNING);
        planRepository.save(plan);
        TaskPlan corrected = documentService.getDocument(planId);
        assertThat(corrected.phase()).isEqualTo(PlanPhase.EXECUTION);
        assertThat(corrected.status()).isEqualTo(PlanStatus.DONE);
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `export JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ && ./gradlew :backend:test --tests "com.yr.perftest.platform.task.plandoc.PlanDocumentServiceTest"`
Expected: 编译失败。

- [ ] **Step 3: 实现**

批注实体与仓库：

```java
package com.yr.perftest.platform.task.plandoc;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "plan_comments")
public class PersistentPlanCommentRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long planId;

    @Column(nullable = false, length = 80)
    private String author;

    @Lob
    @Column(nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanCommentKind kind;

    @Column(nullable = false)
    private Instant createdAt;

    protected PersistentPlanCommentRecord() {
    }

    public PersistentPlanCommentRecord(Long planId, String author, String content, PlanCommentKind kind) {
        this.planId = planId;
        this.author = author;
        this.content = content;
        this.kind = kind;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getPlanId() { return planId; }
    public String getAuthor() { return author; }
    public String getContent() { return content; }
    public PlanCommentKind getKind() { return kind; }
    public Instant getCreatedAt() { return createdAt; }
}
```

```java
package com.yr.perftest.platform.task.plandoc;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PersistentPlanCommentRepository extends JpaRepository<PersistentPlanCommentRecord, Long> {
    List<PersistentPlanCommentRecord> findAllByPlanIdOrderByIdAsc(Long planId);

    void deleteAllByPlanId(Long planId);
}
```

`PersistentScenarioExecutionRepository` 追加：

```java
long countByScenarioIdInAndStatusIn(java.util.List<Long> scenarioIds,
        java.util.List<com.yr.perftest.platform.execution.ExecutionStatus> statuses);
```

`PlanDocumentService` 完整实现：

```java
package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.execution.ExecutionStatus;
import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.project.ProjectAccessResolver;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRepository;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskScenarioRepository;
import com.yr.perftest.platform.task.TaskPlan;
import com.yr.perftest.platform.task.TaskPlanService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 文档原文读写（唯一数据源）、revision 乐观并发、系统回填、执行态惰性纠偏。 */
@Service
public class PlanDocumentService {
    private final PersistentTaskPlanRepository planRepository;
    private final PersistentTaskScenarioRepository scenarioRepository;
    private final PersistentScenarioExecutionRepository executionRepository;
    private final TaskPlanService planService;
    private final ProjectAccessResolver accessResolver;

    public PlanDocumentService(
            PersistentTaskPlanRepository planRepository,
            PersistentTaskScenarioRepository scenarioRepository,
            PersistentScenarioExecutionRepository executionRepository,
            TaskPlanService planService,
            ProjectAccessResolver accessResolver
    ) {
        this.planRepository = planRepository;
        this.scenarioRepository = scenarioRepository;
        this.executionRepository = executionRepository;
        this.planService = planService;
        this.accessResolver = accessResolver;
    }

    @Transactional
    public TaskPlan getDocument(long planId) {
        correctExecutionState(planId);
        return planService.getPlan(planId);
    }

    @Transactional
    public TaskPlan updateMarkdown(long planId, long baseRevision, String markdown, HumanPrincipal actor) {
        PersistentTaskPlanRecord plan = requirePlan(planId);
        requireEditAllowed(plan, actor);
        if (plan.getRevision() != baseRevision) {
            throw new PlanRevisionConflictException(
                    "PLAN_REVISION_CONFLICT：计划文档已被修改（当前 revision=" + plan.getRevision()
                            + "，提交基于 revision=" + baseRevision + "）",
                    plan.getRevision(),
                    plan.getBody());
        }
        plan.updateBody(markdown == null ? "" : markdown);
        return planService.getPlan(planId);
    }

    /** 系统回填：不校验 baseRevision、不受阶段限制，但 revision+1（设计 §8.2）。 */
    @Transactional
    public void backfillExecutionRecord(long planId, String scenarioName, long executionId, String entryLine) {
        PersistentTaskPlanRecord plan = requirePlan(planId);
        String base = plan.getBody() == null ? "" : plan.getBody();
        String updated = PlanMarkdownSupport.appendExecutionRecord(base, scenarioName, executionId, entryLine);
        if (updated.equals(base)) {
            return; // 幂等：标记已存在，不动 revision
        }
        plan.updateBody(updated);
    }

    @Transactional
    public void correctExecutionState(long planId) {
        PersistentTaskPlanRecord plan = requirePlan(planId);
        if (plan.getPhase() != PlanPhase.EXECUTION) {
            return;
        }
        boolean active = hasActiveExecution(planId);
        if (plan.getStatus() == PlanStatus.RUNNING && !active) {
            plan.transitionTo(PlanPhase.EXECUTION, PlanStatus.DONE);
        } else if ((plan.getStatus() == PlanStatus.PENDING || plan.getStatus() == PlanStatus.DONE) && active) {
            plan.transitionTo(PlanPhase.EXECUTION, PlanStatus.RUNNING);
        }
    }

    public boolean hasActiveExecution(long planId) {
        List<Long> scenarioIds = scenarioRepository.findAllByPlanIdOrderBySortOrderAscIdAsc(planId).stream()
                .map(com.yr.perftest.platform.task.PersistentTaskScenarioRecord::getId)
                .toList();
        if (scenarioIds.isEmpty()) {
            return false;
        }
        return executionRepository.countByScenarioIdInAndStatusIn(scenarioIds,
                List.of(ExecutionStatus.QUEUED, ExecutionStatus.RUNNING, ExecutionStatus.STOPPING)) > 0;
    }

    public PersistentTaskPlanRecord requirePlan(long planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new PlanValidationException("PLAN_INVALID：task plan does not exist"));
    }

    private void requireEditAllowed(PersistentTaskPlanRecord plan, HumanPrincipal actor) {
        ProjectAccessResolver.PlanActorRole role = accessResolver.resolve(plan.getProjectId(), actor, plan.getCreatedBy());
        if (!PlanAccess.compute(role, plan.getPhase(), plan.getStatus(), true).get("EDIT")) {
            if (plan.getPhase() != PlanPhase.DRAFT) {
                throw new PlanStateException("PLAN_STATE：文档仅草稿阶段可编辑（当前 "
                        + plan.getPhase() + "/" + plan.getStatus() + "）",
                        plan.getPhase(), plan.getStatus(), List.of("WITHDRAW", "BACK_TO_DRAFT"));
            }
            throw new PlanAccessDeniedException("PLAN_ACCESS_DENIED：仅负责人/项目 OWNER/系统管理员可编辑文档");
        }
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `export JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ && ./gradlew :backend:test --tests "com.yr.perftest.platform.task.plandoc.PlanDocumentServiceTest"`
Expected: PASS（6 个用例）。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/yr/perftest/platform/task backend/src/test/java/com/yr/perftest/platform/task/plandoc
git commit -m "feat：P0-1 Task4 文档服务——原文读写/409 冲突体/系统回填幂等/执行态惰性纠偏 + 批注实体"
```

---

### Task 5: PlanWorkflowService —— 状态机流转与批注

**Files:**
- Create: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanWorkflowService.java`
- Test: `backend/src/test/java/com/yr/perftest/platform/task/plandoc/PlanWorkflowServiceTest.java`

**Interfaces:**
- Consumes: Task 1-4 全部（实体方法、`PlanAccess`、`ProjectAccessResolver`、`PersistentPlanCommentRepository`、`PlanDocumentService.hasActiveExecution`）。
- Produces（后续任务依赖的精确签名）：
  - `record CommentView(long id, long planId, String author, String content, PlanCommentKind kind, java.time.Instant createdAt)`（PlanWorkflowService 内嵌 public record）
  - `void submit(long planId, HumanPrincipal actor, String comment)` / `startReview(long, HumanPrincipal)` / `approve(long, HumanPrincipal, String comment)` / `reject(long, HumanPrincipal, String comment)` / `withdraw(long, HumanPrincipal)` / `backToDraft(long, HumanPrincipal)` / `startExecution(long, HumanPrincipal)` / `toReport(long, HumanPrincipal)`
  - `List<CommentView> listComments(long planId)` / `CommentView addComment(long planId, HumanPrincipal actor, String content)` / `void deleteComment(long planId, long commentId, HumanPrincipal actor)`
  - `void systemComment(long planId, String content)`（内部+Task 9/11 复用）
  - `PersistentTaskPlanRecord requireState(long planId, PlanPhase phase, PlanStatus... allowedStatuses)`

- [ ] **Step 1: 写失败测试**

```java
package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.identity.SystemRole;
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

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-workflow-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PlanWorkflowServiceTest {

    private static final HumanPrincipal OWNER = new HumanPrincipal("owner", Set.of(SystemRole.PROJECT_MEMBER));
    private static final HumanPrincipal REVIEWER = new HumanPrincipal("reviewer", Set.of(SystemRole.PROJECT_MEMBER));

    @Autowired
    private PlanWorkflowService workflow;
    @Autowired
    private PersistentTaskPlanRepository planRepository;
    @Autowired
    private PersistentProjectRepository projectRepository;
    @Autowired
    private PersistentProjectMemberRepository memberRepository;

    private long planId;

    @BeforeEach
    void setUp() {
        PersistentProjectRecord project = projectRepository.save(
                new PersistentProjectRecord("P1", "项目一", "", "owner"));
        memberRepository.save(new PersistentProjectMemberRecord(project.getId(), "owner", ProjectRole.OWNER));
        memberRepository.save(new PersistentProjectMemberRecord(project.getId(), "reviewer", ProjectRole.MEMBER));
        planId = planRepository.save(
                new PersistentTaskPlanRecord(project.getId(), "计划一", null, "owner")).getId();
    }

    private PlanPhase phase() {
        return planRepository.findById(planId).orElseThrow().getPhase();
    }

    private PlanStatus status() {
        return planRepository.findById(planId).orElseThrow().getStatus();
    }

    @Test
    void fullHappyPathDraftToReportPending() {
        workflow.submit(planId, OWNER, "请评审");
        assertThat(phase()).isEqualTo(PlanPhase.REVIEW);
        assertThat(status()).isEqualTo(PlanStatus.PENDING);
        workflow.startReview(planId, REVIEWER);
        workflow.approve(planId, REVIEWER, "同意");
        assertThat(status()).isEqualTo(PlanStatus.APPROVED);
        workflow.startExecution(planId, REVIEWER);
        assertThat(phase()).isEqualTo(PlanPhase.EXECUTION);
        assertThat(status()).isEqualTo(PlanStatus.PENDING);
        planRepository.findById(planId).orElseThrow().forceState(PlanPhase.EXECUTION, PlanStatus.DONE);
        workflow.toReport(planId, REVIEWER);
        assertThat(phase()).isEqualTo(PlanPhase.REPORT);
        assertThat(status()).isEqualTo(PlanStatus.PENDING);
    }

    @Test
    void submitRejectedForNonOwner() {
        assertThatThrownBy(() -> workflow.submit(planId, REVIEWER, null))
                .isInstanceOf(PlanAccessDeniedException.class);
    }

    @Test
    void rejectRequiresComment() {
        workflow.submit(planId, OWNER, null);
        workflow.startReview(planId, REVIEWER);
        assertThatThrownBy(() -> workflow.reject(planId, REVIEWER, " "))
                .isInstanceOf(PlanValidationException.class);
        workflow.reject(planId, REVIEWER, "指标口径不清");
        assertThat(phase()).isEqualTo(PlanPhase.DRAFT);
    }

    @Test
    void illegalTransitionThrowsPlanState() {
        assertThatThrownBy(() -> workflow.approve(planId, REVIEWER, null))
                .isInstanceOf(PlanStateException.class)
                .hasMessageContaining("PLAN_STATE");
    }

    @Test
    void withdrawReturnsToDraftAndWritesSystemComments() {
        workflow.submit(planId, OWNER, null);
        workflow.startReview(planId, REVIEWER);
        workflow.withdraw(planId, OWNER);
        assertThat(phase()).isEqualTo(PlanPhase.DRAFT);
        assertThat(workflow.listComments(planId))
                .anySatisfy(c -> {
                    assertThat(c.kind()).isEqualTo(PlanCommentKind.SYSTEM);
                    assertThat(c.content()).contains("撤回");
                });
    }

    @Test
    void backToDraftBlockedAfterExecutionExists() {
        workflow.submit(planId, OWNER, null);
        workflow.startReview(planId, REVIEWER);
        workflow.approve(planId, REVIEWER, null);
        workflow.startExecution(planId, REVIEWER);
        // 有执行历史（hasAnyExecution=true 路径）：先造一条终态执行
        // —— 本测试无场景执行表依赖，直接用 forceState 模拟"曾有执行"不成立；
        //    backToDraft 的 hasAnyExecution 判定用 executionRepository 存在性，无场景即无执行，应允许退回
        workflow.backToDraft(planId, OWNER);
        assertThat(phase()).isEqualTo(PlanPhase.DRAFT);
    }

    @Test
    void reviewCommentLifecycle() {
        PlanWorkflowService.CommentView comment = workflow.addComment(planId, REVIEWER, "第二章表格补口径");
        assertThat(comment.kind()).isEqualTo(PlanCommentKind.REVIEW);
        workflow.deleteComment(planId, comment.id(), REVIEWER);
        assertThat(workflow.listComments(planId)).noneMatch(c -> c.id() == comment.id());
        PlanWorkflowService.CommentView system = workflow.addComment(planId, OWNER, "成员批注");
        assertThatThrownBy(() -> workflow.deleteComment(planId, system.id(), REVIEWER)) // 非作者且非负责人
                .isInstanceOf(PlanAccessDeniedException.class);
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `export JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ && ./gradlew :backend:test --tests "com.yr.perftest.platform.task.plandoc.PlanWorkflowServiceTest"`
Expected: 编译失败。

- [ ] **Step 3: 实现（完整类；报告/发布/模板/分享/precheck 的方法在 Task 6/8/10/11 追加到同类）**

```java
package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.project.ProjectAccessResolver;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRepository;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.PersistentTaskScenarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** 计划状态机流转与批注（设计 §4/§6）。报告/发布/模板/分享/预检分任务追加。 */
@Service
public class PlanWorkflowService {

    public record CommentView(long id, long planId, String author, String content, PlanCommentKind kind, Instant createdAt) {
    }

    private final PersistentTaskPlanRepository planRepository;
    private final PersistentTaskScenarioRepository scenarioRepository;
    private final PersistentScenarioExecutionRepository executionRepository;
    private final PersistentPlanCommentRepository commentRepository;
    private final ProjectAccessResolver accessResolver;

    public PlanWorkflowService(
            PersistentTaskPlanRepository planRepository,
            PersistentTaskScenarioRepository scenarioRepository,
            PersistentScenarioExecutionRepository executionRepository,
            PersistentPlanCommentRepository commentRepository,
            ProjectAccessResolver accessResolver
    ) {
        this.planRepository = planRepository;
        this.scenarioRepository = scenarioRepository;
        this.executionRepository = executionRepository;
        this.commentRepository = commentRepository;
        this.accessResolver = accessResolver;
    }

    @Transactional
    public void submit(long planId, HumanPrincipal actor, String comment) {
        PersistentTaskPlanRecord plan = requireActor(planId, actor, "SUBMIT");
        plan.transitionTo(PlanPhase.REVIEW, PlanStatus.PENDING);
        systemComment(planId, actor.username() + " 提交评审");
        if (comment != null && !comment.isBlank()) {
            commentRepository.save(new PersistentPlanCommentRecord(planId, actor.username(), comment.trim(), PlanCommentKind.REVIEW));
        }
    }

    @Transactional
    public void startReview(long planId, HumanPrincipal actor) {
        PersistentTaskPlanRecord plan = requireActor(planId, actor, "START_REVIEW");
        plan.transitionTo(PlanPhase.REVIEW, PlanStatus.IN_REVIEW);
        systemComment(planId, actor.username() + " 开始评审");
    }

    @Transactional
    public void approve(long planId, HumanPrincipal actor, String comment) {
        PersistentTaskPlanRecord plan = requireActor(planId, actor, "APPROVE");
        plan.transitionTo(PlanPhase.REVIEW, PlanStatus.APPROVED);
        systemComment(planId, "评审通过（审批人：" + actor.username() + "）");
        if (comment != null && !comment.isBlank()) {
            commentRepository.save(new PersistentPlanCommentRecord(planId, actor.username(), comment.trim(), PlanCommentKind.REVIEW));
        }
    }

    @Transactional
    public void reject(long planId, HumanPrincipal actor, String comment) {
        if (comment == null || comment.isBlank()) {
            throw new PlanValidationException("PLAN_INVALID：驳回必须附批注");
        }
        PersistentTaskPlanRecord plan = requireActor(planId, actor, "REJECT");
        plan.transitionTo(PlanPhase.DRAFT, PlanStatus.DRAFT);
        commentRepository.save(new PersistentPlanCommentRecord(planId, actor.username(), comment.trim(), PlanCommentKind.REVIEW));
        systemComment(planId, actor.username() + " 驳回，退回草稿");
    }

    @Transactional
    public void withdraw(long planId, HumanPrincipal actor) {
        PersistentTaskPlanRecord plan = requireActor(planId, actor, "WITHDRAW");
        plan.transitionTo(PlanPhase.DRAFT, PlanStatus.DRAFT);
        systemComment(planId, actor.username() + " 撤回评审，退回草稿");
    }

    @Transactional
    public void backToDraft(long planId, HumanPrincipal actor) {
        PersistentTaskPlanRecord plan = requireActor(planId, actor, "BACK_TO_DRAFT");
        if (hasAnyExecution(planId)) {
            throw new PlanStateException("PLAN_STATE：已产生执行，不可退回草稿（当前 "
                    + plan.getPhase() + "/" + plan.getStatus() + "）",
                    plan.getPhase(), plan.getStatus(), List.of("TO_REPORT", "GENERATE_REPORT"));
        }
        plan.transitionTo(PlanPhase.DRAFT, PlanStatus.DRAFT);
        systemComment(planId, actor.username() + " 退回草稿");
    }

    @Transactional
    public void startExecution(long planId, HumanPrincipal actor) {
        PersistentTaskPlanRecord plan = requireActor(planId, actor, "START_EXECUTION");
        plan.transitionTo(PlanPhase.EXECUTION, PlanStatus.PENDING);
        systemComment(planId, actor.username() + " 进入执行阶段");
    }

    @Transactional
    public void toReport(long planId, HumanPrincipal actor) {
        PersistentTaskPlanRecord plan = requireActor(planId, actor, "TO_REPORT");
        plan.transitionTo(PlanPhase.REPORT, PlanStatus.PENDING);
        systemComment(planId, actor.username() + " 进入报告阶段");
    }

    @Transactional(readOnly = true)
    public List<CommentView> listComments(long planId) {
        return commentRepository.findAllByPlanIdOrderByIdAsc(planId).stream()
                .map(c -> new CommentView(c.getId(), c.getPlanId(), c.getAuthor(), c.getContent(), c.getKind(), c.getCreatedAt()))
                .toList();
    }

    @Transactional
    public CommentView addComment(long planId, HumanPrincipal actor, String content) {
        if (content == null || content.isBlank()) {
            throw new PlanValidationException("PLAN_INVALID：批注内容不能为空");
        }
        PersistentTaskPlanRecord plan = requireActor(planId, actor, "COMMENT");
        PersistentPlanCommentRecord saved = commentRepository.save(
                new PersistentPlanCommentRecord(planId, actor.username(), content.trim(), PlanCommentKind.REVIEW));
        return new CommentView(saved.getId(), saved.getPlanId(), saved.getAuthor(), saved.getContent(), saved.getKind(), saved.getCreatedAt());
    }

    @Transactional
    public void deleteComment(long planId, long commentId, HumanPrincipal actor) {
        PersistentTaskPlanRecord plan = requirePlan(planId);
        PersistentPlanCommentRecord comment = commentRepository.findById(commentId)
                .filter(c -> c.getPlanId() == planId)
                .orElseThrow(() -> new PlanValidationException("PLAN_INVALID：批注不存在"));
        if (comment.getKind() == PlanCommentKind.SYSTEM) {
            throw new PlanValidationException("PLAN_INVALID：系统批注不可删除");
        }
        ProjectAccessResolver.PlanActorRole role = accessResolver.resolve(plan.getProjectId(), actor, plan.getCreatedBy());
        boolean ownerLike = role == ProjectAccessResolver.PlanActorRole.SYSTEM_ADMIN
                || role == ProjectAccessResolver.PlanActorRole.PROJECT_OWNER
                || role == ProjectAccessResolver.PlanActorRole.PLAN_OWNER;
        if (!ownerLike && !comment.getAuthor().equals(actor.username())) {
            throw new PlanAccessDeniedException("PLAN_ACCESS_DENIED：仅批注作者/负责人/项目 OWNER/系统管理员可删除批注");
        }
        commentRepository.delete(comment);
    }

    @Transactional
    public void systemComment(long planId, String content) {
        commentRepository.save(new PersistentPlanCommentRecord(planId, "system", content, PlanCommentKind.SYSTEM));
    }

    public boolean hasAnyExecution(long planId) {
        return scenarioRepository.findAllByPlanIdOrderBySortOrderAscIdAsc(planId).stream()
                .anyMatch(scenario -> executionRepository.existsByScenarioId(scenario.getId()));
    }

    public PersistentTaskPlanRecord requirePlan(long planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new PlanValidationException("PLAN_INVALID：task plan does not exist"));
    }

    /** 校验动作权限并返回计划记录；非法状态时异常附允许动作。 */
    private PersistentTaskPlanRecord requireActor(long planId, HumanPrincipal actor, String action) {
        PersistentTaskPlanRecord plan = requirePlan(planId);
        if (actor == null) {
            throw new PlanAccessDeniedException("PLAN_ACCESS_DENIED：未登录");
        }
        ProjectAccessResolver.PlanActorRole role = accessResolver.resolve(plan.getProjectId(), actor, plan.getCreatedBy());
        java.util.Map<String, Boolean> permissions = PlanAccess.compute(role, plan.getPhase(), plan.getStatus(), hasAnyExecution(planId));
        if (!Boolean.TRUE.equals(permissions.get(action))) {
            if (role == ProjectAccessResolver.PlanActorRole.NONE) {
                throw new PlanAccessDeniedException("PLAN_ACCESS_DENIED：非项目成员");
            }
            throw new PlanStateException("PLAN_STATE：当前状态不允许「" + action + "」（当前 "
                    + plan.getPhase() + "/" + plan.getStatus() + "，允许："
                    + allowedActions(permissions) + "）",
                    plan.getPhase(), plan.getStatus(), allowedActions(permissions));
        }
        return plan;
    }

    private List<String> allowedActions(java.util.Map<String, Boolean> permissions) {
        return permissions.entrySet().stream().filter(java.util.Map.Entry::getValue).map(java.util.Map.Entry::getKey).toList();
    }
}
```

`hasAnyExecution` 用到 `executionRepository.existsByScenarioId(Long)`——该仓库目前没有，**在 `PersistentScenarioExecutionRepository` 追加**：
```java
boolean existsByScenarioId(Long scenarioId);
```

- [ ] **Step 4: 跑测试确认通过**

Run: `export JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ && ./gradlew :backend:test --tests "com.yr.perftest.platform.task.plandoc.PlanWorkflowServiceTest"`
Expected: PASS（7 个用例）。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/yr/perftest/platform/task backend/src/test/java/com/yr/perftest/platform/task/plandoc
git commit -m "feat：P0-1 Task5 状态机流转与批注——8 个流转动作、权限与非法状态 409、SYSTEM 批注留痕"
```

---

### Task 6: 模板体系 —— 实体、内置 seed、CRUD、创建计划带模板

**Files:**
- Create: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PersistentPlanTemplateRecord.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PersistentPlanTemplateRepository.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PrecheckSettings.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanTemplateSeeder.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanWorkflowService.java`（追加模板 CRUD 方法）
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanDocumentService.java`（追加 `renderInitialBody`）
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/TaskPlanService.java`（createPlan 增加 templateId）
- Modify: `backend/src/main/java/com/yr/perftest/platform/api/TaskPlanController.java:69-85`（createPlan 调用点传 `request.templateId()`）
- Modify: `docs/database/mysql-schema.sql`（plan_templates 表）
- Test: `backend/src/test/java/com/yr/perftest/platform/task/plandoc/PlanTemplateTest.java`

**Interfaces:**
- Consumes: Task 2 `renderTemplate`/`parseChecklistItems`、Task 5 `PlanWorkflowService` 构造器。
- Produces:
  - `record PrecheckSettings(boolean enabled, List<String> items)`，静态 `PrecheckSettings.DEFAULT_ITEMS = List.of("指标已定义","场景已配置","脚本已关联","环境就绪","数据就绪","人员到位","接口人明确")` 与 `PrecheckSettings disabled()`
  - `PlanWorkflowService`：`List<PersistentPlanTemplateRecord> listTemplates(long projectId)` / `PersistentPlanTemplateRecord createTemplate(long projectId, HumanPrincipal actor, String name, String description, String content)` / `updateTemplate(long templateId, HumanPrincipal actor, String name, String description, String content)` / `deleteTemplate(long templateId, HumanPrincipal actor)`（builtin 不可改删；管理仅项目 OWNER/系统 ADMIN）
  - `PlanDocumentService.renderInitialBody(Long templateId, String planName)` → String（模板缺失返回 null）
  - `TaskPlanService.createPlan(long projectId, String name, String remark, Long controllerNodeId, List<Long> workerIds, List<Long> monitorIds, String createdBy, Long templateId)` —— 旧 7 参签名改为委托新 8 参方法（存量调用零破坏）

- [ ] **Step 1: 写失败测试**

```java
package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.identity.SystemRole;
import com.yr.perftest.platform.project.PersistentProjectMemberRecord;
import com.yr.perftest.platform.project.PersistentProjectMemberRepository;
import com.yr.perftest.platform.project.PersistentProjectRecord;
import com.yr.perftest.platform.project.PersistentProjectRepository;
import com.yr.perftest.platform.project.ProjectRole;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.TaskPlan;
import com.yr.perftest.platform.task.TaskPlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-template-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PlanTemplateTest {

    private static final HumanPrincipal OWNER = new HumanPrincipal("owner", Set.of(SystemRole.PROJECT_MEMBER));
    private static final HumanPrincipal MEMBER = new HumanPrincipal("member-b", Set.of(SystemRole.PROJECT_MEMBER));

    @Autowired
    private PlanWorkflowService workflow;
    @Autowired
    private TaskPlanService planService;
    @Autowired
    private PersistentTaskPlanRepository planRepository;
    @Autowired
    private PersistentProjectRepository projectRepository;
    @Autowired
    private PersistentProjectMemberRepository memberRepository;

    private long projectId;

    @BeforeEach
    void setUp() {
        PersistentProjectRecord project = projectRepository.save(
                new PersistentProjectRecord("P1", "项目一", "", "owner"));
        projectId = project.getId();
        memberRepository.save(new PersistentProjectMemberRecord(projectId, "owner", ProjectRole.OWNER));
        memberRepository.save(new PersistentProjectMemberRecord(projectId, "member-b", ProjectRole.MEMBER));
    }

    @Test
    void builtinTemplateSeededAndUntouchable() {
        var templates = workflow.listTemplates(projectId);
        assertThat(templates).anyMatch(t -> t.getName().equals("通用压测计划") && t.isBuiltin());
        Long builtinId = templates.stream().filter(t -> t.isBuiltin()).findFirst().orElseThrow().getId();
        assertThatThrownBy(() -> workflow.updateTemplate(builtinId, OWNER, "改", null, "x"))
                .isInstanceOf(PlanValidationException.class);
        assertThatThrownBy(() -> workflow.deleteTemplate(builtinId, OWNER))
                .isInstanceOf(PlanValidationException.class);
    }

    @Test
    void templateManagementRestrictedToOwner() {
        assertThatThrownBy(() -> workflow.createTemplate(projectId, MEMBER, "t", null, "# 内容"))
                .isInstanceOf(PlanAccessDeniedException.class);
        var created = workflow.createTemplate(projectId, OWNER, "项目模板", "描述", "## 一、背景\n\n{{planName}}\n");
        assertThat(workflow.listTemplates(projectId)).anyMatch(t -> t.getName().equals("项目模板"));
        workflow.updateTemplate(created.getId(), OWNER, "项目模板2", null, "## 一、背景\n\n改\n");
        workflow.deleteTemplate(created.getId(), OWNER);
        assertThat(workflow.listTemplates(projectId)).noneMatch(t -> t.getName().equals("项目模板2"));
    }

    @Test
    void createPlanWithTemplateRendersBodyAndDefaultPrecheck() {
        Long templateId = workflow.listTemplates(projectId).stream()
                .filter(t -> t.getName().equals("通用压测计划")).findFirst().orElseThrow().getId();
        TaskPlan plan = planService.createPlan(projectId, "零售3.1 压测", null, null, null, null, "owner", templateId);
        assertThat(plan.body()).contains("# 零售3.1 压测 性能测试计划");
        assertThat(plan.body()).contains("## 二、测试目的与指标");
        assertThat(plan.body()).contains("## 十一、结论");
        assertThat(plan.body()).contains("指标已定义（自动）");
        assertThat(plan.revision()).isEqualTo(1);
        // 默认 precheck：disabled + 默认清单（入口准则条目）
        com.yr.perftest.platform.task.PersistentTaskPlanRecord raw = planRepository.findById(plan.id()).orElseThrow();
        assertThat(raw.getPrecheckJson()).contains("\"enabled\":false");
        assertThat(raw.getPrecheckJson()).contains("指标已定义");
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `export JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ && ./gradlew :backend:test --tests "com.yr.perftest.platform.task.plandoc.PlanTemplateTest"`
Expected: 编译失败。

- [ ] **Step 3: 实现**

实体与仓库：

```java
package com.yr.perftest.platform.task.plandoc;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "plan_templates")
public class PersistentPlanTemplateRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id")
    private Long projectId; // NULL = 内置

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 1000)
    private String description;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private boolean builtin;

    @Column(nullable = false, length = 80)
    private String createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected PersistentPlanTemplateRecord() {
    }

    public PersistentPlanTemplateRecord(Long projectId, String name, String description, String content, boolean builtin, String createdBy) {
        this.projectId = projectId;
        this.name = name;
        this.description = description;
        this.content = content;
        this.builtin = builtin;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getContent() { return content; }
    public boolean isBuiltin() { return builtin; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(String name, String description, String content) {
        this.name = name;
        this.description = description;
        this.content = content;
        this.updatedAt = Instant.now();
    }
}
```

```java
package com.yr.perftest.platform.task.plandoc;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PersistentPlanTemplateRepository extends JpaRepository<PersistentPlanTemplateRecord, Long> {
    @Query("select t from PersistentPlanTemplateRecord t where t.projectId is null or t.projectId = :projectId order by t.builtin desc, t.id asc")
    List<PersistentPlanTemplateRecord> findAllVisible(@Param("projectId") Long projectId);

    Optional<PersistentPlanTemplateRecord> findFirstByBuiltinTrueOrderByIdAsc();
}
```

```java
package com.yr.perftest.platform.task.plandoc;

import java.util.List;

/** 环境检查执行设置（非文档内容，不进评审不进 revision；设计 §10.2）。 */
public record PrecheckSettings(boolean enabled, List<String> items) {
    public static final List<String> DEFAULT_ITEMS =
            List.of("指标已定义", "场景已配置", "脚本已关联", "环境就绪", "数据就绪", "人员到位", "接口人明确");

    public static PrecheckSettings disabled() {
        return new PrecheckSettings(false, DEFAULT_ITEMS);
    }
}
```

内置模板内容（`PlanTemplateSeeder` 内常量，完整 11 章节；这是全部"受约束章节"的格式合同，前后端解析都依赖它）：

```java
package com.yr.perftest.platform.task.plandoc;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 内置模板 seed：存在即跳过，不可编辑删除（设计 §7.1）。 */
@Configuration
public class PlanTemplateSeeder {

    public static final String BUILTIN_TEMPLATE = """
            # {{planName}} 性能测试计划

            ## 一、背景

            （简述被测系统、本次压测的业务背景与动因。）

            ## 二、测试目的与指标

            | 交易 | 指标 | 目标值 | 口径 |
            |---|---|---|---|
            | （示例）查询交易 | TPS | ≥ 200 | 5 分钟均值 |
            | （示例）查询交易 | P95 | ≤ 300 ms | 5 分钟均值 |
            | （示例）查询交易 | 错误率 | ≤ 0.5% | 全量样本 |

            ## 三、测试范围

            ### 范围内交易

            | 交易名称 | 交易配比 | 备注 |
            |---|---|---|
            | （示例）登录 | 30% | |

            ### 范围外清单

            - （列出明确不测的交易及原因）

            ## 四、测试资源

            ### 人员

            | 角色 | 姓名 | 职责 |
            |---|---|---|
            | 测试负责 |  | 计划与结论 |
            | 执行 |  | 场景执行与观察 |

            ### 环境部署信息

            | 地址 | 模块 | 配置/版本 |
            |---|---|---|
            |  |  |  |

            ### 执行节点与监控目标

            - 执行节点：
            - 监控目标：

            ### 时间窗口

            - 计划执行时间：

            ## 五、测试约束

            ### 入口准则

            - [ ] 指标已定义（自动）
            - [ ] 场景已配置（自动）
            - [ ] 脚本已关联（自动）
            - [ ] 环境就绪（人工）
            - [ ] 数据就绪（人工）
            - [ ] 人员到位（人工）
            - [ ] 接口人明确（人工）

            ### 出口准则

            - [ ] 全部场景按计划执行完成（人工）
            - [ ] 指标达成表已确认（人工）
            - [ ] 风险与建议已记录（人工）

            ## 六、测试策略

            （叙述：压测模型、数据准备策略、监控与观察点。）

            ## 七、场景设计

            （场景块由平台按场景实体生成并回写；示例结构如下，勿手改标记行。）

            ### S1 示例场景 · SINGLE_TXN

            **场景目的**：（填写）

            **测试方法**：（自由编辑，实体同步不触碰此处）

            **交易范围**：（自由编辑，实体同步不触碰此处）

            **场景设置**（由场景执行配置生成，勿手改）：

            | 用户数 | 持续时长 | 加载方式 | 退出方式 |
            |---|---|---|---|
            | 50 | 300 秒 | 匀速加载 30 秒 | 同时退出 |

            #### 执行记录

            ## 八、风险与预案

            （列出主要风险与应对。）

            ## 九、排期与协作

            | 环节 | 时间 | 负责人 |
            |---|---|---|
            | 计划评审 |  |  |
            | 脚本编写 |  |  |
            | 执行与观察 |  |  |
            | 报告与发布 |  |  |

            ## 十、附录

            （参考资料、术语等。）

            ## 十一、结论

            ### 指标达成表

            | 指标 | 目标 | 实际结果 | 状态 |
            |---|---|---|---|
            | （示例）查询交易 TPS | ≥ 200 | 待执行 | 待判定 |

            ### 风险与建议

            （发布前填写。）

            **总体结论**：（发布时填写）
            """;

    @Bean
    public ApplicationRunner planTemplateSeed(PersistentPlanTemplateRepository repository) {
        return args -> {
            if (repository.findFirstByBuiltinTrueOrderByIdAsc().isEmpty()) {
                repository.save(new PersistentPlanTemplateRecord(
                        null, "通用压测计划", "内置通用压测计划模板（11 章节固定结构）",
                        BUILTIN_TEMPLATE, true, "system"));
            }
        };
    }
}
```

`PlanWorkflowService` 追加（构造器新增 `PersistentPlanTemplateRepository templateRepository` 与 `com.fasterxml.jackson.databind.ObjectMapper objectMapper` 两个依赖；后续任务在此基础上继续追加，见各任务说明）：

> 设计 §12.1 里"创建扩展：可选 `{templateId, goals, scope, resources, criteria}`"中的 `goals/scope/...` 参数已被设计修订 5（Markdown 原文唯一数据源、删除结构化 JSON 列）取代——本计划只实现 `templateId`。

```java
    @Transactional(readOnly = true)
    public List<PersistentPlanTemplateRecord> listTemplates(long projectId) {
        return templateRepository.findAllVisible(projectId);
    }

    @Transactional
    public PersistentPlanTemplateRecord createTemplate(long projectId, HumanPrincipal actor, String name, String description, String content) {
        requireTemplateManager(projectId, actor);
        if (name == null || name.isBlank() || content == null || content.isBlank()) {
            throw new PlanValidationException("PLAN_INVALID：模板名称与内容不能为空");
        }
        return templateRepository.save(new PersistentPlanTemplateRecord(projectId, name.trim(), description, content, false, actor.username()));
    }

    @Transactional
    public PersistentPlanTemplateRecord updateTemplate(long templateId, HumanPrincipal actor, String name, String description, String content) {
        PersistentPlanTemplateRecord template = templateRepository.findById(templateId)
                .orElseThrow(() -> new PlanValidationException("PLAN_INVALID：模板不存在"));
        if (template.isBuiltin()) {
            throw new PlanValidationException("PLAN_INVALID：内置模板不可编辑");
        }
        requireTemplateManager(template.getProjectId(), actor);
        template.update(name.trim(), description, content);
        return template;
    }

    @Transactional
    public void deleteTemplate(long templateId, HumanPrincipal actor) {
        PersistentPlanTemplateRecord template = templateRepository.findById(templateId)
                .orElseThrow(() -> new PlanValidationException("PLAN_INVALID：模板不存在"));
        if (template.isBuiltin()) {
            throw new PlanValidationException("PLAN_INVALID：内置模板不可删除");
        }
        requireTemplateManager(template.getProjectId(), actor);
        templateRepository.delete(template);
    }

    private void requireTemplateManager(Long projectId, HumanPrincipal actor) {
        if (projectId == null || actor == null
                || !accessResolver.resolve(projectId, actor, null).equals(ProjectAccessResolver.PlanActorRole.PROJECT_OWNER)
                && accessResolver.resolve(projectId, actor, null) != ProjectAccessResolver.PlanActorRole.SYSTEM_ADMIN) {
            throw new PlanAccessDeniedException("PLAN_ACCESS_DENIED：仅项目 OWNER/系统管理员可管理模板");
        }
    }
```

（以下两个方法体落入 `TaskPlanService`，不进 `PlanDocumentService`——后者依赖 `TaskPlanService`，反向注入会成环。）`TaskPlanService` 追加（构造器加 `PersistentPlanTemplateRepository templateRepository` 与 `com.fasterxml.jackson.databind.ObjectMapper objectMapper`）：

```java
    /** 模板渲染初始正文；无模板返回 null（存量计划兼容）。 */
    public String renderInitialBody(Long templateId, String planName) {
        PersistentPlanTemplateRecord template = templateId != null
                ? templateRepository.findById(templateId).orElse(null)
                : templateRepository.findFirstByBuiltinTrueOrderByIdAsc().orElse(null);
        if (template == null) {
            return null;
        }
        return PlanMarkdownSupport.renderTemplate(template.getContent(), planName);
    }

    /** 从渲染后的正文解析入口准则条目 → 默认 precheck 设置（disabled）。 */
    public String defaultPrecheckJson(String body) {
        List<String> items = PlanMarkdownSupport.parseChecklistItems(
                PlanMarkdownSupport.extractSection(body == null ? "" : body, "五、测试约束"));
        List<String> effective = items.isEmpty() ? PrecheckSettings.DEFAULT_ITEMS : items;
        try {
            return objectMapper.writeValueAsString(new PrecheckSettings(false, effective));
        } catch (Exception exception) {
            throw new PlanValidationException("PLAN_INVALID：precheck 设置序列化失败");
        }
    }
```

`TaskPlanService`：`createPlan` 改为 8 参并在 save 后设置 body/precheck（构造器加 `PlanDocumentService`? 会循环——`PlanDocumentService` 构造依赖 `TaskPlanService`。**解法**：这两段渲染逻辑不进 `PlanDocumentService`，直接放进 `TaskPlanService`，构造器加 `PersistentPlanTemplateRepository` + `ObjectMapper`）：

```java
    @Transactional
    public TaskPlan createPlan(
            long projectId, String name, String remark,
            Long defaultControllerNodeId, List<Long> defaultWorkerNodeIds, List<Long> defaultMonitorTargetIds,
            String createdBy, Long templateId
    ) {
        validateProject(projectId);
        validateName(name);
        PersistentTaskPlanRecord plan = planRepository.save(new PersistentTaskPlanRecord(projectId, name.trim(), remark, createdBy));
        plan.updateProfile(name, remark, defaultControllerNodeId,
                taskJson.writeLongList(defaultWorkerNodeIds), taskJson.writeLongList(defaultMonitorTargetIds));
        String body = renderInitialBody(templateId, name.trim());
        if (body != null) {
            plan.updateBody(body); // revision 1→2？不行——首版正文必须是 revision=1
        }
        return toPlan(planRepository.save(plan));
    }
```

**修正**：`updateBody` 会把 revision 从 1 bump 到 2，违反"初始 revision=1"。给实体加一个只在创建语境使用的方法（Task 1 已有的字段直接赋值不行——封装）。在 `PersistentTaskPlanRecord` 追加：

```java
    /** 仅创建时初始化正文：不 bump revision（首版 revision=1）。 */
    public void initializeBody(String body) {
        this.body = body;
    }
```

`TaskPlanService.createPlan` 内改为 `plan.initializeBody(body);` 并接着 `plan.initializePrecheck(defaultPrecheckJson)`——同理加：

```java
    public void initializePrecheck(String precheckJson) {
        this.precheckJson = precheckJson;
    }
```

`renderInitialBody/defaultPrecheckJson` 作为 `TaskPlanService` 私有方法落地（把上面 `PlanDocumentService` 追加段里的两个方法体移入 `TaskPlanService`，依赖 `PersistentPlanTemplateRepository` + `ObjectMapper` 注入其构造器；`PlanDocumentService` 不加这两个方法——其构造器保持 Task 4 结束时的 5 依赖）。`TaskPlanController.createPlan` 调用点补 `request.templateId()`，`CreateTaskPlanRequest` record 加 `Long templateId` 字段。

`docs/database/mysql-schema.sql` 在 task_scenarios 表后追加（设计 §11.3）：

```sql
-- ============================================================
-- 06a. 计划模板
-- 说明: 计划文档模板；project_id 为 NULL 表示内置模板
-- ============================================================
DROP TABLE IF EXISTS `plan_templates`;
CREATE TABLE `plan_templates` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `project_id`  BIGINT                COMMENT '所属项目ID（NULL=内置）',
    `name`        VARCHAR(160) NOT NULL COMMENT '模板名称',
    `description` VARCHAR(1000)         COMMENT '模板描述',
    `content`     LONGTEXT     NOT NULL COMMENT '模板 Markdown 全文',
    `builtin`     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否内置',
    `created_by`  VARCHAR(80)  NOT NULL COMMENT '创建者',
    `created_at`  DATETIME(3)  NOT NULL COMMENT '创建时间',
    `updated_at`  DATETIME(3)  NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_plan_templates_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='计划模板表';
```

- [ ] **Step 4: 跑测试确认通过**

Run: `export JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ && ./gradlew :backend:test --tests "com.yr.perftest.platform.task.plandoc.PlanTemplateTest" && ./gradlew :backend:test`
Expected: PASS（3 个用例）+ 全量回归通过。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/yr/perftest/platform/task backend/src/main/java/com/yr/perftest/platform/api/TaskPlanController.java docs/database/mysql-schema.sql backend/src/test/java/com/yr/perftest/platform/task/plandoc
git commit -m "feat：P0-1 Task6 模板体系——内置 11 章节模板 seed、项目模板 CRUD、创建计划渲染正文与默认执行设置"
```

---

### Task 7: 业务化场景 —— 脚本可空、purpose/testType、文档场景块同步、关联脚本

**Files:**
- Create: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanScenarioDocSync.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/TaskScenarioService.java`（create/update 签名 + 联动回写 + 删除回写）
- Modify: `backend/src/main/java/com/yr/perftest/platform/api/TaskPlanController.java:115-159,289-312`（请求 record 扩展 + 关联脚本端点）
- Test: `backend/src/test/java/com/yr/perftest/platform/task/plandoc/PlanScenarioDocSyncTest.java`

**Interfaces:**
- Consumes: Task 2 `upsertScenarioFacts/removeScenarioBlock`、Task 1 实体字段、`ScenarioThreadGroupConfigSupport`（`readStored(String)`、`groupPresetsBySortOrder(List)` 已存在）。
- Produces:
  - `PlanScenarioDocSync.syncPlanScenarios(long planId)`：按 `scenarioRepository.findAllByPlanIdOrderBySortOrderAscIdAsc(planId)` 重写七章节全部场景块（每场景 `upsertScenarioFacts`；文档中多出的块**不**自动删——删除走 `onScenarioDeleted`；body 为 null 时仅确保章节存在并写入）
  - `PlanScenarioDocSync.onScenarioDeleted(long planId, String scenarioName)`：`removeScenarioBlock` + `plan.updateBody`
  - 场景块生成规则（`generatedBlockOf(PersistentTaskScenarioRecord scenario, List<ScenarioThreadGroupConfig> configs)`）：
    - 标题行 `### S{sortOrder+1} {name} · {testType == null ? "UNSPECIFIED" : testType}`
    - `**场景目的**：{purpose == null ? "（待填写）" : purpose}`
    - 自由文本占位两行（已有块时不动原文）
    - `**场景设置**（由场景执行配置生成，勿手改）：` + 表头 `| 用户数 | 持续时长 | 加载方式 | 退出方式 |` + 按 `groupPresetsBySortOrder` 分档：每档一行 `| {Σthreads} | {maxDuration 秒 or "不限"} | {rampUp==0?"同时加载":"匀速加载 "+rampUp+" 秒"} | 同时退出 |`
  - `TaskScenarioService.createScenario(long planId, Long scriptVersionId, String name, String purpose, TestType testType, Map<String,String> jmeterProperties, List<ScenarioThreadGroupConfig> threadGroupConfigs, Long controllerNodeId, List<Long> workerNodeIds, List<Long> monitorTargetIds)`（scriptVersionId 可空，非空才校验脚本归属）
  - `TaskScenarioService.updateScenario(long scenarioId, String name, Long scriptVersionId, String purpose, TestType testType, ...)`（改名时先取旧名再回写）
  - `TaskScenarioService.bindScript(long scenarioId, long scriptVersionId)`：校验脚本属于同项目；计划须处于 REVIEW/APPROVED 或（EXECUTION|REPORT 且非 RUNNING）——评审通过后才允许关联（设计 §3.4/§4.5）

- [ ] **Step 1: 写失败测试**

```java
package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.PersistentTaskScenarioRecord;
import com.yr.perftest.platform.task.PersistentTaskScenarioRepository;
import com.yr.perftest.platform.task.ScenarioThreadGroupConfig;
import com.yr.perftest.platform.task.ScenarioThreadGroupConfigSupport;
import com.yr.perftest.platform.task.TestType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-scenario-sync-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PlanScenarioDocSyncTest {

    @Autowired
    private PlanScenarioDocSync docSync;
    @Autowired
    private PersistentTaskPlanRepository planRepository;
    @Autowired
    private PersistentTaskScenarioRepository scenarioRepository;
    @Autowired
    private ScenarioThreadGroupConfigSupport configSupport;

    private long planId;

    @BeforeEach
    void setUp() {
        PersistentTaskPlanRecord plan = planRepository.save(new PersistentTaskPlanRecord(1L, "计划", null, "owner"));
        plan.updateBody("## 七、场景设计\n\n### S1 登录 · BENCHMARK\n\n**场景目的**：旧目的\n\n"
                + "**测试方法**：自由文本保留验证\n\n**场景设置**（由场景执行配置生成，勿手改）：\n\n"
                + "| 用户数 | 持续时长 | 加载方式 | 退出方式 |\n|---|---|---|---|\n| 10 | 60 秒 | 同时加载 | 同时退出 |\n\n#### 执行记录\n");
        planId = planRepository.save(plan).getId();
    }

    private PersistentTaskScenarioRecord addScenario(String name, int sortOrder, int threads, int rampUp, int duration) {
        PersistentTaskScenarioRecord scenario = scenarioRepository.save(
                new PersistentTaskScenarioRecord(planId, null, name, sortOrder));
        scenario.updateBusinessFields("验证" + name, TestType.SINGLE_TXN);
        scenarioRepository.save(scenario);
        return scenario;
    }

    @Test
    void syncWritesScenarioFactsAndKeepsFreeTextAndRecords() {
        addScenario("登录", 0, 50, 30, 300);
        docSync.syncPlanScenarios(planId);
        String body = planRepository.findById(planId).orElseThrow().getBody();
        assertThat(body).contains("### S1 登录 · SINGLE_TXN");           // 标题/类型更新
        assertThat(body).contains("**场景目的**：验证登录");               // 目的更新
        assertThat(body).contains("| 50 | 300 秒 | 匀速加载 30 秒 | 同时退出 |"); // 设置表更新
        assertThat(body).contains("**测试方法**：自由文本保留验证");        // 自由文本保留
        assertThat(body).doesNotContain("旧目的");
    }

    @Test
    void syncAppendsBlockForNewScenario() {
        addScenario("转账", 0, 30, 0, 1800);
        docSync.syncPlanScenarios(planId);
        String body = planRepository.findById(planId).orElseThrow().getBody();
        assertThat(body).contains("### S1 转账 · SINGLE_TXN");
        assertThat(body).contains("| 30 | 1800 秒 | 同时加载 | 同时退出 |");
        assertThat(body).contains("#### 执行记录");
    }

    @Test
    void deletedScenarioBlockRemoved() {
        addScenario("查询", 0, 10, 0, 60);
        docSync.syncPlanScenarios(planId);
        docSync.onScenarioDeleted(planId, "查询");
        String body = planRepository.findById(planId).orElseThrow().getBody();
        assertThat(body).doesNotContain("查询");
    }
}
```

改名行为合同：upsert 按**场景名**匹配块，`updateScenario` 改名时先以旧名 `onScenarioDeleted` 再 sync（服务层实现，见 Step 3）。对应用例（追加进同一测试类）：

```java
    @Test
    void renamedScenarioReplacesOldBlock() {
        PersistentTaskScenarioRecord scenario = addScenario("登录", 0, 20, 0, 120);
        docSync.syncPlanScenarios(planId); // 建块：S1 登录
        scenarioRepository.save(scenario);
        // 模拟改名流程：service 层先 remove 旧名再 sync（updateScenario 内实现）
        docSync.onScenarioDeleted(planId, "登录");
        scenarioRepository.findById(scenario.getId()).orElseThrow().updateProfile("新登录", null, "{}", null, null, null, null);
        docSync.syncPlanScenarios(planId);
        String body = planRepository.findById(planId).orElseThrow().getBody();
        assertThat(body).doesNotContain("### S1 登录");
        assertThat(body).contains("S1 新登录");
    }
```

- [ ] **Step 2: 跑测试确认失败**

Run: `export JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ && ./gradlew :backend:test --tests "com.yr.perftest.platform.task.plandoc.PlanScenarioDocSyncTest"`
Expected: 编译失败。

- [ ] **Step 3: 实现**

```java
package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskScenarioRecord;
import com.yr.perftest.platform.task.PersistentTaskScenarioRepository;
import com.yr.perftest.platform.task.ScenarioThreadGroupConfig;
import com.yr.perftest.platform.task.ScenarioThreadGroupConfigSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 场景实体 → 文档七章节回写：只动"事实"（标题/目的/设置表），保留自由文本与执行记录。 */
@Service
public class PlanScenarioDocSync {
    private final PersistentTaskPlanRepository planRepository;
    private final PersistentTaskScenarioRepository scenarioRepository;
    private final ScenarioThreadGroupConfigSupport configSupport;

    public PlanScenarioDocSync(PersistentTaskPlanRepository planRepository,
                               PersistentTaskScenarioRepository scenarioRepository,
                               ScenarioThreadGroupConfigSupport configSupport) {
        this.planRepository = planRepository;
        this.scenarioRepository = scenarioRepository;
        this.configSupport = configSupport;
    }

    @Transactional
    public void syncPlanScenarios(long planId) {
        PersistentTaskPlanRecord plan = planRepository.findById(planId).orElseThrow();
        String base = plan.getBody() == null ? "" : plan.getBody();
        String body = base;
        for (PersistentTaskScenarioRecord scenario : scenarioRepository.findAllByPlanIdOrderBySortOrderAscIdAsc(planId)) {
            List<ScenarioThreadGroupConfig> configs = configSupport.readStored(scenario.getThreadGroupConfigsJson());
            body = PlanMarkdownSupport.upsertScenarioFacts(body, scenario.getName(), generatedBlockOf(scenario, configs));
        }
        if (!body.equals(base)) {
            plan.updateBody(body);
        }
    }

    @Transactional
    public void onScenarioDeleted(long planId, String scenarioName) {
        PersistentTaskPlanRecord plan = planRepository.findById(planId).orElseThrow();
        String base = plan.getBody();
        if (base == null) {
            return;
        }
        String body = PlanMarkdownSupport.removeScenarioBlock(base, scenarioName);
        if (!body.equals(base)) {
            plan.updateBody(body);
        }
    }

    private String generatedBlockOf(PersistentTaskScenarioRecord scenario, List<ScenarioThreadGroupConfig> configs) {
        String type = scenario.getTestType() == null ? "UNSPECIFIED" : scenario.getTestType().name();
        StringBuilder block = new StringBuilder()
                .append("### S").append(scenario.getSortOrder() + 1).append(' ')
                .append(scenario.getName()).append(" · ").append(type).append("\n\n")
                .append("**场景目的**：").append(scenario.getPurpose() == null || scenario.getPurpose().isBlank()
                        ? "（待填写）" : scenario.getPurpose().trim()).append("\n\n")
                .append("**测试方法**：（自由编辑，实体同步不触碰此处）\n\n")
                .append("**交易范围**：（自由编辑，实体同步不触碰此处）\n\n")
                .append("**场景设置**（由场景执行配置生成，勿手改）：\n\n")
                .append("| 用户数 | 持续时长 | 加载方式 | 退出方式 |\n|---|---|---|---|\n");
        for (List<ScenarioThreadGroupConfig> preset : configSupport.groupPresetsBySortOrder(configs)) {
            int threads = preset.stream().mapToInt(ScenarioThreadGroupConfig::threads).sum();
            int duration = preset.stream().mapToInt(ScenarioThreadGroupConfig::duration).max().orElse(0);
            int rampUp = preset.get(0).rampUp();
            block.append("| ").append(threads)
                    .append(" | ").append(duration <= 0 ? "不限" : duration + " 秒")
                    .append(" | ").append(rampUp == 0 ? "同时加载" : "匀速加载 " + rampUp + " 秒")
                    .append(" | 同时退出 |\n");
        }
        return block.toString();
    }
}
```

（若 `ScenarioThreadGroupConfig` 的访问器不是 `threads()/rampUp()/duration()`，以 `backend/src/main/java/com/yr/perftest/platform/task/ScenarioThreadGroupConfig.java` 实际 record 组件名为准同义替换，**不改语义**。）

`TaskScenarioService` 改造要点（构造器加 `PlanScenarioDocSync docSync` 与 `PersistentTaskPlanRepository planRepository`——后者已存在）：

1. `createScenario` 新签名（`Long scriptVersionId` + `String purpose` + `TestType testType`）；`scriptVersionId != null` 才 `validateScript`；建后 `scenario.updateBusinessFields(purpose, testType)` + `docSync.syncPlanScenarios(planId)`。
2. `updateScenario` 新签名同上；**改名流程**：先 `String oldName = scenario.getName()`，应用变更后：若 `!oldName.equals(newName)` → `docSync.onScenarioDeleted(planId, oldName)`；然后 `docSync.syncPlanScenarios(planId)`。
3. `deleteScenario`：删除实体后 `docSync.onScenarioDeleted(plan.getPlanId? scenario.getPlanId(), name)`。
4. 新方法：

```java
    @Transactional
    public TaskScenario bindScript(long scenarioId, long scriptVersionId) {
        PersistentTaskScenarioRecord scenario = requireScenario(scenarioId);
        PersistentTaskPlanRecord plan = requirePlan(scenario.getPlanId());
        boolean afterApproval = (plan.getPhase() == com.yr.perftest.platform.task.plandoc.PlanPhase.REVIEW
                && plan.getStatus() == com.yr.perftest.platform.task.plandoc.PlanStatus.APPROVED)
                || ((plan.getPhase() == com.yr.perftest.platform.task.plandoc.PlanPhase.EXECUTION
                || plan.getPhase() == com.yr.perftest.platform.task.plandoc.PlanPhase.REPORT)
                && plan.getStatus() != com.yr.perftest.platform.task.plandoc.PlanStatus.RUNNING);
        if (!afterApproval) {
            throw new com.yr.perftest.platform.task.plandoc.PlanStateException(
                    "PLAN_STATE：评审通过后才可关联脚本（当前 " + plan.getPhase() + "/" + plan.getStatus() + "）",
                    plan.getPhase(), plan.getStatus(), java.util.List.of("APPROVE", "START_EXECUTION"));
        }
        validateScript(plan.getProjectId(), scriptVersionId);
        scenario.bindScript(scriptVersionId);
        return toScenario(scenario);
    }
```

5. `TaskPlanController`：
   - `CreateScenarioRequest.scriptVersionId` 改 `Long`（去 `@NotNull`），加 `String purpose` / `TestType testType`；`UpdateScenarioRequest` 同步加两字段；两个调用点透传。
   - `toScenario` 视图 record `TaskScenario` 加 `purpose/testType` 组件（`task/TaskScenario.java`，构造点在 `TaskScenarioService.toScenario` 一处 + 测试如有引用一并补 null）。
   - 新端点：

```java
    @org.springframework.web.bind.annotation.PostMapping("/scenarios/{scenarioId}/script")
    public TaskScenario bindScript(@PathVariable long scenarioId, @RequestBody BindScriptRequest request) {
        return scenarioService.bindScript(scenarioId, request.scriptVersionId());
    }

    public record BindScriptRequest(@NotNull Long scriptVersionId) {
    }
```

- [ ] **Step 4: 跑测试确认通过 + 全量回归**

Run: `export JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ && ./gradlew :backend:test --tests "com.yr.perftest.platform.task.plandoc.PlanScenarioDocSyncTest" && ./gradlew :backend:test`
Expected: PASS（4 个用例）+ 回归通过（`createScenario` 旧调用点已同步改签名）。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/yr/perftest/platform/task backend/src/test/java/com/yr/perftest/platform/task/plandoc
git commit -m "feat：P0-1 Task7 业务化场景——脚本可空后置关联、purpose/testType、场景事实回写文档保留自由文本"
```

---

### Task 8: 执行门禁 + 环境检查（首执行触发、可跳过）挂唯一 seam

**Files:**
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanWorkflowService.java`（追加 gate 与 precheck 方法）
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/ExecutionControlService.java:45-65`（start 前置门禁 + 启动置 RUNNING）
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/PersistentTaskPlanRecord.java`（追加 `updatePrecheckJson`）
- Modify: 现有受影响测试的 setUp（见 Step 4 列表）
- Test: `backend/src/test/java/com/yr/perftest/platform/task/plandoc/PlanExecutionGateTest.java`

**Interfaces:**
- Consumes: Task 5 `PlanWorkflowService.requirePlan/systemComment/hasAnyExecution`、Task 4 `PlanDocumentService.hasActiveExecution`、Task 2 `PlanMarkdownSupport.extractSection/parseChecklistItems`、Task 6 `PrecheckSettings`。
- Produces（`PlanWorkflowService` 追加）：
  - `long assertExecutionAllowed(long scenarioId)` → planId；失败抛 `PlanStateException`（阶段不符）/`PlanValidationException`（未关联脚本）/`PlanPrecheckFailedException`（首执行 precheck 未过）
  - `void onExecutionStarted(long planId)`：`EXECUTION/PENDING|DONE`→`RUNNING`；`REPORT/*`→`EXECUTION/RUNNING`（报告作废）
  - `record PrecheckReport(boolean ok, List<String> failures, List<String> autoPassed)`
  - `PrecheckReport runPrecheck(long planId, boolean writeBackChecklist)`：评估清单（自动项核验、人工项未勾=未过）；`writeBackChecklist=true` 时把自动通过项回写 `- [x]` 到入口准则
  - `void precheckSkip(long planId, HumanPrincipal actor)`：置 `precheckExecutedAt=now` + SYSTEM 批注
  - `PrecheckSettings getPrecheckSettings(long planId)` / `void updatePrecheckSettings(long planId, HumanPrincipal actor, PrecheckSettings settings)`（不 bump revision）
- Produces（实体）：`PersistentTaskPlanRecord.updatePrecheckJson(String json)`（只改 precheckJson+updatedAt）

- [ ] **Step 1: 写失败测试**

```java
package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.identity.SystemRole;
import com.yr.perftest.platform.project.PersistentProjectMemberRecord;
import com.yr.perftest.platform.project.PersistentProjectMemberRepository;
import com.yr.perftest.platform.project.PersistentProjectRecord;
import com.yr.perftest.platform.project.PersistentProjectRepository;
import com.yr.perftest.platform.project.ProjectRole;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.PersistentTaskScenarioRecord;
import com.yr.perftest.platform.task.PersistentTaskScenarioRepository;
import com.yr.perftest.platform.task.TestType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-gate-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PlanExecutionGateTest {

    private static final HumanPrincipal OWNER = new HumanPrincipal("owner", Set.of(SystemRole.PROJECT_MEMBER));
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private PlanWorkflowService workflow;
    @Autowired
    private PersistentTaskPlanRepository planRepository;
    @Autowired
    private PersistentTaskScenarioRepository scenarioRepository;
    @Autowired
    private PersistentProjectRepository projectRepository;
    @Autowired
    private PersistentProjectMemberRepository memberRepository;

    private long planId;
    private long scenarioId;

    @BeforeEach
    void setUp() throws Exception {
        PersistentProjectRecord project = projectRepository.save(new PersistentProjectRecord("P1", "项目一", "", "owner"));
        memberRepository.save(new PersistentProjectMemberRecord(project.getId(), "owner", ProjectRole.OWNER));
        PersistentTaskPlanRecord plan = planRepository.save(
                new PersistentTaskPlanRecord(project.getId(), "计划", null, "owner"));
        plan.updateBody("## 二、测试目的与指标\n\n| 交易 | 指标 | 目标值 | 口径 |\n|---|---|---|---|\n| 查询 | TPS | 200 | 均值 |\n\n"
                + "## 五、测试约束\n\n### 入口准则\n\n- [ ] 指标已定义（自动）\n- [ ] 环境就绪（人工）\n\n## 七、场景设计\n");
        plan.forceState(PlanPhase.EXECUTION, PlanStatus.PENDING);
        planId = planRepository.save(plan).getId();
        PersistentTaskScenarioRecord scenario = scenarioRepository.save(
                new PersistentTaskScenarioRecord(planId, null, "场景A", 0));
        scenario.updateBusinessFields("目的", TestType.SINGLE_TXN);
        scenarioId = scenarioRepository.save(scenario).getId();
    }

    private void enablePrecheck() throws Exception {
        workflow.updatePrecheckSettings(planId, OWNER,
                new PrecheckSettings(true, List.of("指标已定义", "环境就绪")));
    }

    @Test
    void gateRejectsWhenPlanNotPastReview() {
        planRepository.findById(planId).orElseThrow().forceState(PlanPhase.REVIEW, PlanStatus.IN_REVIEW);
        assertThatThrownBy(() -> workflow.assertExecutionAllowed(scenarioId))
                .isInstanceOf(PlanStateException.class)
                .hasMessageContaining("请先通过评审并进入执行阶段");
    }

    @Test
    void gateRejectsUnboundScript() {
        assertThatThrownBy(() -> workflow.assertExecutionAllowed(scenarioId))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("未关联脚本");
    }

    @Test
    void gatePassesAndSkipsPrecheckWhenDisabled() {
        scenarioRepository.findById(scenarioId).orElseThrow().bindScript(9L);
        long resolved = workflow.assertExecutionAllowed(scenarioId);
        assertThat(resolved).isEqualTo(planId);
        assertThat(planRepository.findById(planId).orElseThrow().getPrecheckExecutedAt()).isNull();
    }

    @Test
    void firstExecutionWithPrecheckEnabledRunsItAndBlocksOnManualItems() {
        scenarioRepository.findById(scenarioId).orElseThrow().bindScript(9L);
        enablePrecheck();
        assertThatThrownBy(() -> workflow.assertExecutionAllowed(scenarioId))
                .isInstanceOf(PlanPrecheckFailedException.class)
                .hasMessageContaining("环境就绪");
        // 跳过后放行且不再重跑
        workflow.precheckSkip(planId, OWNER);
        workflow.assertExecutionAllowed(scenarioId);
        assertThat(workflow.listComments(planId)).anySatisfy(c -> {
            assertThat(c.kind()).isEqualTo(PlanCommentKind.SYSTEM);
            assertThat(c.content()).contains("跳过环境检查");
        });
    }

    @Test
    void onExecutionStartedResetsReportPhaseAndRuns() {
        planRepository.findById(planId).orElseThrow().forceState(PlanPhase.REPORT, PlanStatus.DONE);
        workflow.onExecutionStarted(planId);
        PersistentTaskPlanRecord plan = planRepository.findById(planId).orElseThrow();
        assertThat(plan.getPhase()).isEqualTo(PlanPhase.EXECUTION);
        assertThat(plan.getStatus()).isEqualTo(PlanStatus.RUNNING);
        planRepository.save(plan).forceState(PlanPhase.EXECUTION, PlanStatus.DONE);
        workflow.onExecutionStarted(planId);
        assertThat(planRepository.findById(planId).orElseThrow().getStatus()).isEqualTo(PlanStatus.RUNNING);
    }

    @Test
    void precheckSettingsUpdateDoesNotBumpRevision() throws Exception {
        int before = planRepository.findById(planId).orElseThrow().getRevision();
        enablePrecheck();
        assertThat(planRepository.findById(planId).orElseThrow().getRevision()).isEqualTo(before);
        assertThat(workflow.getPrecheckSettings(planId).enabled()).isTrue();
    }

    @Test
    void manualPrecheckRunWritesBackAutoChecks() {
        scenarioRepository.findById(scenarioId).orElseThrow().bindScript(9L);
        workflow.updatePrecheckSettings(planId, OWNER, new PrecheckSettings(true, List.of("指标已定义", "场景已配置")));
        PlanWorkflowService.PrecheckReport report = workflow.runPrecheck(planId, true);
        assertThat(report.ok()).isTrue();
        assertThat(report.autoPassed()).containsExactly("指标已定义", "场景已配置");
        // 指标已定义=自动通过 → 回写勾选（revision+1，系统回填语义）
        String constraints = PlanMarkdownSupport.extractSection(
                planRepository.findById(planId).orElseThrow().getBody(), "五、测试约束");
        assertThat(constraints).contains("- [x] 指标已定义（自动）");
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `export JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ && ./gradlew :backend:test --tests "com.yr.perftest.platform.task.plandoc.PlanExecutionGateTest"`
Expected: 编译失败。

- [ ] **Step 3: 实现**

`PersistentTaskPlanRecord` 追加：

```java
    public void updatePrecheckJson(String precheckJson) {
        this.precheckJson = precheckJson;
        this.updatedAt = Instant.now();
    }
```

`PlanWorkflowService` 追加（构造器追加 `PlanDocumentService documentService`；`ObjectMapper` 已在 Task 6 注入。`PlanDocumentService` 不依赖 `PlanWorkflowService`，无循环））：

```java
    public record PrecheckReport(boolean ok, List<String> failures, List<String> autoPassed) {
    }

    /** 执行门禁（设计 §10.1）：阶段 + 脚本 + 首执行环境检查。返回 planId。 */
    @Transactional
    public long assertExecutionAllowed(long scenarioId) {
        PersistentTaskScenarioRecord scenario = scenarioRepository.findById(scenarioId)
                .orElseThrow(() -> new PlanValidationException("PLAN_INVALID：scenario does not exist"));
        PersistentTaskPlanRecord plan = requirePlan(scenario.getPlanId());
        boolean phaseOk = plan.getPhase() == PlanPhase.EXECUTION
                || (plan.getPhase() == PlanPhase.REPORT && plan.getStatus() != PlanStatus.GENERATING);
        if (!phaseOk) {
            throw new PlanStateException("PLAN_STATE：请先通过评审并进入执行阶段（当前 "
                    + plan.getPhase() + "/" + plan.getStatus() + "）",
                    plan.getPhase(), plan.getStatus(), List.of("SUBMIT", "START_REVIEW", "APPROVE", "START_EXECUTION"));
        }
        if (scenario.getScriptVersionId() == null) {
            throw new PlanValidationException("PLAN_INVALID：场景「" + scenario.getName() + "」未关联脚本，无法执行");
        }
        PrecheckSettings settings = getPrecheckSettings(plan.getId());
        if (settings.enabled() && plan.getPrecheckExecutedAt() == null) {
            PrecheckReport report = runPrecheck(plan.getId(), true);
            if (!report.ok()) {
                throw new PlanPrecheckFailedException("PLAN_PRECHECK_FAILED：环境检查未通过——"
                        + String.join("；", report.failures()), report.failures());
            }
            plan.markPrecheckExecuted(Instant.now());
        }
        return plan.getId();
    }

    @Transactional
    public void onExecutionStarted(long planId) {
        PersistentTaskPlanRecord plan = requirePlan(planId);
        if (plan.getPhase() == PlanPhase.REPORT) {
            plan.transitionTo(PlanPhase.EXECUTION, PlanStatus.RUNNING); // 报告作废（设计 §4.3）
        } else if (plan.getPhase() == PlanPhase.EXECUTION
                && (plan.getStatus() == PlanStatus.PENDING || plan.getStatus() == PlanStatus.DONE)) {
            plan.transitionTo(PlanPhase.EXECUTION, PlanStatus.RUNNING);
        }
    }

    /** 评估检测清单：自动项核验；人工项视为未确认=失败项（设计 §10.3）。 */
    @Transactional
    public PrecheckReport runPrecheck(long planId, boolean writeBackChecklist) {
        PersistentTaskPlanRecord plan = requirePlan(planId);
        PrecheckSettings settings = getPrecheckSettings(planId);
        List<String> failures = new java.util.ArrayList<>();
        List<String> autoPassed = new java.util.ArrayList<>();
        String body = plan.getBody() == null ? "" : plan.getBody();
        List<PersistentTaskScenarioRecord> scenarios = scenarioRepository.findAllByPlanIdOrderBySortOrderAscIdAsc(planId);
        for (String item : settings.items()) {
            String plain = item.replaceAll("（.*?）$", "").trim();
            boolean auto;
            boolean pass;
            switch (plain) {
                case "指标已定义" -> {
                    auto = true;
                    pass = PlanMarkdownSupport.extractSection(body, "二、测试目的与指标") != null
                            && PlanMarkdownSupport.extractSection(body, "二、测试目的与指标").contains("|---");
                }
                case "场景已配置" -> {
                    auto = true;
                    pass = !scenarios.isEmpty();
                }
                case "脚本已关联" -> {
                    auto = true;
                    pass = scenarios.stream().allMatch(s -> s.getScriptVersionId() != null);
                }
                default -> {
                    auto = false;
                    pass = false;
                }
            }
            if (auto && pass) {
                autoPassed.add(plain);
            } else {
                failures.add(auto ? plain + "（自动核验未通过）" : plain + "（待人工确认）");
            }
        }
        if (writeBackChecklist && !autoPassed.isEmpty()) {
            writeBackEntryChecklist(plan, body, autoPassed);
        }
        return new PrecheckReport(failures.isEmpty(), List.copyOf(failures), List.copyOf(autoPassed));
    }

    /** 自动通过项回写入口准则勾选（系统回填：revision+1）。 */
    private void writeBackEntryChecklist(PersistentTaskPlanRecord plan, String body, List<String> autoPassed) {
        String constraints = PlanMarkdownSupport.extractSection(body, "五、测试约束");
        if (constraints == null) {
            return;
        }
        String updated = constraints;
        for (String item : autoPassed) {
            updated = updated.replace("- [ ] " + item + "（自动）", "- [x] " + item + "（自动）");
        }
        if (!updated.equals(constraints)) {
            plan.updateBody(PlanMarkdownSupport.replaceSection(body, "五、测试约束", updated));
        }
    }

    @Transactional
    public void precheckSkip(long planId, HumanPrincipal actor) {
        PersistentTaskPlanRecord plan = requirePlan(planId);
        plan.markPrecheckExecuted(Instant.now());
        systemComment(planId, "跳过环境检查继续执行（操作人：" + (actor == null ? "?" : actor.username()) + "）");
    }

    @Transactional(readOnly = true)
    public PrecheckSettings getPrecheckSettings(long planId) {
        PersistentTaskPlanRecord plan = requirePlan(planId);
        if (plan.getPrecheckJson() == null || plan.getPrecheckJson().isBlank()) {
            return PrecheckSettings.disabled();
        }
        try {
            PrecheckSettings parsed = objectMapper.readValue(plan.getPrecheckJson(), PrecheckSettings.class);
            return parsed.items() == null ? new PrecheckSettings(parsed.enabled(), PrecheckSettings.DEFAULT_ITEMS) : parsed;
        } catch (Exception exception) {
            return PrecheckSettings.disabled();
        }
    }

    @Transactional
    public void updatePrecheckSettings(long planId, HumanPrincipal actor, PrecheckSettings settings) {
        PersistentTaskPlanRecord plan = requirePlan(planId);
        requireActor(planId, actor, "PRECHECK_RUN"); // 成员级动作，沿用权限矩阵
        if (settings == null || settings.items() == null) {
            throw new PlanValidationException("PLAN_INVALID：precheck 设置不合法");
        }
        try {
            plan.updatePrecheckJson(objectMapper.writeValueAsString(settings));
        } catch (Exception exception) {
            throw new PlanValidationException("PLAN_INVALID：precheck 设置序列化失败");
        }
    }
```

`ExecutionControlService.start` 集成（构造器追加 `PlanWorkflowService planWorkflowService`；方法体首尾插入）：

```java
    @Transactional
    public StartOutcome start(StartCommand command, String idempotencyKey) {
        long planId = planWorkflowService.assertExecutionAllowed(command.scenarioId()); // 门禁+首执行环境检查（设计 §10.1/§10.2）
        String requestHash = RequestHashing.sha256(
                // ……原有 4 行不动……
        );
        IdempotencyService.IdempotentExecution result = idempotencyService.execute(
                // ……原有调用不动……
        );
        ScenarioExecution execution = executionQueryService.getExecution(result.executionId());
        planWorkflowService.onExecutionStarted(planId); // 置 EXECUTION/RUNNING + 报告作废
        audit(result.executionId(), "START", result.replayed());
        return new StartOutcome(execution.id(), execution.status(), result.replayed());
    }
```

- [ ] **Step 4: 订正受门禁影响的存量测试**

Run: `export JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ && ./gradlew :backend:test 2>&1 | grep -E "FAILED|PlanState" | head -20`
Expected: 凡直接造计划+场景就 `start/triggerExecution` 的测试因计划默认 DRAFT 报 `PlanStateException`。

修法（统一补两行）：在创建 `PersistentTaskPlanRecord` 后加
```java
plan.forceState(com.yr.perftest.platform.task.plandoc.PlanPhase.EXECUTION,
        com.yr.perftest.platform.task.plandoc.PlanStatus.PENDING);
```
逐个文件确认并修改（grep 定位）：`task/ExecutionControlServiceTest.java`（setUp）、`api/UiExecutionControlApiTest.java`、`api/PlatformApiBehaviorTest.java`、`agent/AgentExecutionControlApiTest.java`、`agent/AgentAggregateApiTest.java`、`seed/CaptureAnalysisExecutionTest.java` 等所有命中项；若某测试断言"草稿可执行"的旧语义，**按新门禁改断言**（草稿执行 → 409），不改门禁。
再跑全量：`./gradlew :backend:test` → BUILD SUCCESSFUL。

- [ ] **Step 5: 跑本任务测试确认通过**

Run: `export JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ && ./gradlew :backend:test --tests "com.yr.perftest.platform.task.plandoc.PlanExecutionGateTest"`
Expected: PASS（7 个用例）。

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/yr/perftest/platform backend/src/test/java
git commit -m "feat：P0-1 Task8 执行门禁与环境检查挂唯一 seam——阶段/脚本校验、首执行自动 precheck、跳过留痕、报告作废"
```

---

### Task 9: 执行终态联动 —— 事件、回填、快捷执行

**Files:**
- Create: `backend/src/main/java/com/yr/perftest/platform/task/ExecutionLifecycleEvent.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanExecutionLifecycleListener.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanQuickExecuteService.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/execution/distributed/DistributedJmeterExecutionRunner.java:411-450`（三个 mark* 发事件；构造器注入 `ApplicationEventPublisher`）
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/ExecutionControlService.cancel`（afterCommit 发事件）
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanWorkflowService.java`（追加 `onExecutionTerminal`）
- Test: `backend/src/test/java/com/yr/perftest/platform/task/plandoc/PlanExecutionLifecycleTest.java`

**Interfaces:**
- Consumes: Task 8 门禁/`onExecutionStarted`、Task 4 `backfillExecutionRecord`、`ExecutionQueryService.getExecution/getResult`（构造 entry 行）、`TaskScenarioService.createScenario`、`ExecutionControlService.start`。
- Produces:
  - `record ExecutionLifecycleEvent(long executionId, ExecutionStatus terminalStatus)`（task 包）
  - `PlanWorkflowService.onExecutionTerminal(long executionId)`：回填场景块条目（幂等）+ 无活跃执行且 `EXECUTION/RUNNING` → `DONE`
  - `PlanExecutionLifecycleListener.onExecutionTerminal(ExecutionLifecycleEvent)`：`@EventListener`，内部 try/catch 吞异常只打 WARN（回填失败不得影响执行主流程）
  - `PlanQuickExecuteService.record QuickExecuteResult(long planId, long scenarioId, long executionId)` + `QuickExecuteResult quickExecute(long scriptVersionId, HumanPrincipal actor)`
  - 回填条目格式（固定，设计 §8.1）：`- {yyyy-MM-dd HH:mm 系统时区} · {threads} 并发 · {status} · 吞吐 {%.1f} TPS · P95 {%d} ms · 错误率 {%.2f}%%`

- [ ] **Step 1: 写失败测试**

```java
package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.execution.ExecutionStatus;
import com.yr.perftest.platform.project.PersistentProjectRecord;
import com.yr.perftest.platform.project.PersistentProjectRepository;
import com.yr.perftest.platform.script.PersistentScriptRecord;
import com.yr.perftest.platform.script.PersistentScriptRecordRepository;
import com.yr.perftest.platform.script.PersistentScriptVersionRecord;
import com.yr.perftest.platform.script.PersistentScriptVersionRepository;
import com.yr.perftest.platform.task.ExecutionLifecycleEvent;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRecord;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRepository;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.PersistentTaskScenarioRecord;
import com.yr.perftest.platform.task.PersistentTaskScenarioRepository;
import com.yr.perftest.platform.task.TestType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-lifecycle-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PlanExecutionLifecycleTest {

    private static final String CONFIG_JSON =
            "{\"threads\":50,\"rampUp\":0,\"duration\":300,\"loops\":1,\"jmeterProperties\":{},\"mode\":\"DISTRIBUTED\",\"controllerNodeId\":1,\"workerNodeIds\":[1],\"monitorTargetIds\":[]}";

    @Autowired
    private PlanWorkflowService workflow;
    @Autowired
    private PlanExecutionLifecycleListener listener;
    @Autowired
    private PersistentTaskPlanRepository planRepository;
    @Autowired
    private PersistentTaskScenarioRepository scenarioRepository;
    @Autowired
    private PersistentScenarioExecutionRepository executionRepository;
    @Autowired
    private PersistentProjectRepository projectRepository;

    private long planId;
    private long scenarioId;

    @BeforeEach
    void setUp() {
        PersistentProjectRecord project = projectRepository.save(new PersistentProjectRecord("P1", "项目一", "", "owner"));
        PersistentTaskPlanRecord plan = planRepository.save(
                new PersistentTaskPlanRecord(project.getId(), "计划", null, "owner"));
        plan.updateBody("## 七、场景设计\n\n### S1 场景A · SINGLE_TXN\n\n**场景目的**：p\n\n"
                + "**场景设置**（由场景执行配置生成，勿手改）：\n\n| 用户数 | 持续时长 | 加载方式 | 退出方式 |\n|---|---|---|---|\n| 50 | 300 秒 | 同时加载 | 同时退出 |\n\n#### 执行记录\n");
        plan.forceState(PlanPhase.EXECUTION, PlanStatus.RUNNING);
        planId = planRepository.save(plan).getId();
        PersistentTaskScenarioRecord scenario = scenarioRepository.save(
                new PersistentTaskScenarioRecord(planId, 9L, "场景A", 0));
        scenario.updateBusinessFields("p", TestType.SINGLE_TXN);
        scenarioId = scenarioRepository.save(scenario).getId();
    }

    @Test
    void terminalEventBackfillsScenarioBlockAndMarksDone() {
        PersistentScenarioExecutionRecord execution = executionRepository.save(
                new PersistentScenarioExecutionRecord(scenarioId, CONFIG_JSON));
        execution.markRunning("result.jtl", "jmeter.log");
        execution.markSuccess(0);
        executionRepository.save(execution);

        listener.onExecutionTerminal(new ExecutionLifecycleEvent(execution.getId(), ExecutionStatus.SUCCESS));

        String body = planRepository.findById(planId).orElseThrow().getBody();
        assertThat(body).contains("<!-- backfill:execution:" + execution.getId() + " -->");
        assertThat(body).contains("· SUCCESS ·");
        assertThat(body).contains("50 并发");
        PersistentTaskPlanRecord plan = planRepository.findById(planId).orElseThrow();
        assertThat(plan.getStatus()).isEqualTo(PlanStatus.DONE);
        assertThat(plan.getPhase()).isEqualTo(PlanPhase.EXECUTION);
    }

    @Test
    void terminalEventIsIdempotent() {
        PersistentScenarioExecutionRecord execution = executionRepository.save(
                new PersistentScenarioExecutionRecord(scenarioId, CONFIG_JSON));
        execution.markRunning("result.jtl", "jmeter.log");
        execution.markSuccess(0);
        executionRepository.save(execution);
        listener.onExecutionTerminal(new ExecutionLifecycleEvent(execution.getId(), ExecutionStatus.SUCCESS));
        int revisionAfterFirst = planRepository.findById(planId).orElseThrow().getRevision();
        int markers = planRepository.findById(planId).orElseThrow().getBody()
                .split("<!-- backfill:execution:" + execution.getId() + " -->", -1).length - 1;
        listener.onExecutionTerminal(new ExecutionLifecycleEvent(execution.getId(), ExecutionStatus.SUCCESS));
        assertThat(markers).isEqualTo(1);
        assertThat(planRepository.findById(planId).orElseThrow().getRevision()).isEqualTo(revisionAfterFirst);
    }

    @Test
    void stillActiveStaysRunning() {
        PersistentScenarioExecutionRecord done = executionRepository.save(
                new PersistentScenarioExecutionRecord(scenarioId, CONFIG_JSON));
        done.markRunning("a.jtl", "a.log");
        done.markSuccess(0);
        executionRepository.save(done);
        PersistentScenarioExecutionRecord active = executionRepository.save(
                new PersistentScenarioExecutionRecord(scenarioId, CONFIG_JSON));
        active.markRunning("b.jtl", "b.log");
        executionRepository.save(active);

        listener.onExecutionTerminal(new ExecutionLifecycleEvent(done.getId(), ExecutionStatus.SUCCESS));
        assertThat(planRepository.findById(planId).orElseThrow().getStatus()).isEqualTo(PlanStatus.RUNNING);
    }
}
```

（脚本仓库类名以 `script/` 包实际为准——若为 `PersistentScriptRepository` 则同义替换 import，不改断言。快捷执行的服务级测试在 Task 12 的 API 测试里走 MockMvc 全链路覆盖，此处不依赖脚本表。）

- [ ] **Step 2: 跑测试确认失败**

Run: `export JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ && ./gradlew :backend:test --tests "com.yr.perftest.platform.task.plandoc.PlanExecutionLifecycleTest"`
Expected: 编译失败。

- [ ] **Step 3: 实现**

```java
package com.yr.perftest.platform.task;

import com.yr.perftest.platform.execution.ExecutionStatus;

/** 场景执行进入终态（SUCCESS/FAILED/CANCELLED/INTERRUPTED）后发布。 */
public record ExecutionLifecycleEvent(long executionId, ExecutionStatus terminalStatus) {
}
```

`PlanWorkflowService.onExecutionTerminal`（追加；构造器追加依赖 `PlanDocumentService documentService`、`ExecutionQueryService executionQueryService`、`com.fasterxml.jackson.databind.ObjectMapper objectMapper`——objectMapper Task 8 已加，不重复）：

```java
    /** 终态联动：回填执行摘要 + 全部结束时置 DONE（设计 §4.3/§8）。 */
    @Transactional
    public void onExecutionTerminal(long executionId) {
        PersistentScenarioExecutionRecord execution = executionRepository.findById(executionId).orElse(null);
        if (execution == null) {
            return;
        }
        PersistentTaskScenarioRecord scenario = scenarioRepository.findById(execution.getScenarioId()).orElse(null);
        if (scenario == null) {
            return;
        }
        PersistentTaskPlanRecord plan = requirePlan(scenario.getPlanId());
        String entryLine = buildEntryLine(execution);
        documentService.backfillExecutionRecord(plan.getId(), scenario.getName(), executionId, entryLine);
        if (plan.getPhase() == PlanPhase.EXECUTION && plan.getStatus() == PlanStatus.RUNNING
                && !documentService.hasActiveExecution(plan.getId())) {
            plan.transitionTo(PlanPhase.EXECUTION, PlanStatus.DONE);
        }
    }

    private String buildEntryLine(PersistentScenarioExecutionRecord execution) {
        int threads = 0;
        try {
            com.fasterxml.jackson.databind.JsonNode config = objectMapper.readTree(execution.getConfigJson());
            threads = config.path("threads").asInt(0);
        } catch (Exception ignored) {
        }
        long p95 = 0;
        double throughput = 0d;
        double errorRate = 0d;
        try {
            com.yr.perftest.platform.execution.TaskExecutionResult.Summary summary =
                    executionQueryService.getResult(execution.getId()).summary();
            if (summary != null) {
                p95 = summary.p95();
                throughput = summary.throughput();
                errorRate = summary.errorRate();
            }
        } catch (Exception ignored) {
        }
        String endedAt = execution.getEndTime() == null ? "-"
                : java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(java.time.ZoneId.systemDefault()).format(execution.getEndTime());
        return String.format(java.util.Locale.ROOT,
                "- %s · %d 并发 · %s · 吞吐 %.1f TPS · P95 %d ms · 错误率 %.2f%%",
                endedAt, threads, execution.getStatus(), throughput, p95, errorRate);
    }
```

（实体 getter 已核对：`getConfigJson()/getEndTime()`；`TaskExecutionResult.Summary` 组件名以 `execution/TaskExecutionResult.java` 实际为准，前端镜像为 samples/throughput/avgRt/p95/errorRate。）

```java
package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.task.ExecutionLifecycleEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 终态事件 → 计划状态与文档回填。回填失败只告警，不影响执行主流程。 */
@Component
public class PlanExecutionLifecycleListener {
    private static final Logger log = LoggerFactory.getLogger(PlanExecutionLifecycleListener.class);

    private final PlanWorkflowService workflowService;

    public PlanExecutionLifecycleListener(PlanWorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @EventListener
    public void onExecutionTerminal(ExecutionLifecycleEvent event) {
        try {
            workflowService.onExecutionTerminal(event.executionId());
        } catch (Exception exception) {
            log.warn("plan backfill failed for execution {}: {}", event.executionId(), exception.getMessage());
        }
    }
}
```

`DistributedJmeterExecutionRunner`：构造器注入 `org.springframework.context.ApplicationEventPublisher applicationEventPublisher`；三个私有方法 `markSuccess/markFailed/markInterrupted`（当前 411/423/438 行）在 **save 之后** 各追加一行：

```java
        applicationEventPublisher.publishEvent(new com.yr.perftest.platform.task.ExecutionLifecycleEvent(executionId,
                executionRepository.findById(executionId).map(e -> e.getStatus()).orElse(statusJustMarked)));
```

其中 `statusJustMarked` 分别为 `ExecutionStatus.SUCCESS/FAILED/INTERRUPTED`（直接用常量，不必回读库）：

```java
        applicationEventPublisher.publishEvent(new com.yr.perftest.platform.task.ExecutionLifecycleEvent(
                executionId, com.yr.perftest.platform.execution.ExecutionStatus.SUCCESS)); // markFailed→FAILED，markInterrupted→INTERRUPTED
```

`ExecutionControlService.cancel`：既有 afterCommit 块（auxScriptLifecycle 之后）追加：

```java
                        applicationEventPublisher.publishEvent(new com.yr.perftest.platform.task.ExecutionLifecycleEvent(
                                executionId, com.yr.perftest.platform.execution.ExecutionStatus.CANCELLED));
```

（`ExecutionControlService` 构造器同样注入 `ApplicationEventPublisher`。runner 线程无外层事务，save 即提交，直发安全；cancel 在事务内，走 afterCommit 保证监听器读到 CANCELLED。）

```java
package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.script.PersistentScriptVersionRecord;
import com.yr.perftest.platform.script.PersistentScriptVersionRepository;
import com.yr.perftest.platform.task.ExecutionControlService;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.TaskPlanService;
import com.yr.perftest.platform.task.TaskScenarioService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 快捷执行：单事务内 建计划(EXECUTION/PENDING)→系统批注→建场景(带脚本)→start（设计 §10.4）。 */
@Service
public class PlanQuickExecuteService {

    public record QuickExecuteResult(long planId, long scenarioId, long executionId) {
    }

    private final PersistentScriptVersionRepository scriptVersionRepository;
    private final PersistentTaskPlanRepository planRepository;
    private final TaskPlanService planService;
    private final TaskScenarioService scenarioService;
    private final ExecutionControlService executionControlService;
    private final PlanWorkflowService workflowService;

    public PlanQuickExecuteService(
            PersistentScriptVersionRepository scriptVersionRepository,
            PersistentTaskPlanRepository planRepository,
            TaskPlanService planService,
            TaskScenarioService scenarioService,
            ExecutionControlService executionControlService,
            PlanWorkflowService workflowService
    ) {
        this.scriptVersionRepository = scriptVersionRepository;
        this.planRepository = planRepository;
        this.planService = planService;
        this.scenarioService = scenarioService;
        this.executionControlService = executionControlService;
        this.workflowService = workflowService;
    }

    @Transactional
    public QuickExecuteResult quickExecute(long scriptVersionId, HumanPrincipal actor) {
        PersistentScriptVersionRecord script = scriptVersionRepository.findById(scriptVersionId)
                .orElseThrow(() -> new PlanValidationException("PLAN_INVALID：脚本版本不存在"));
        String username = actor == null ? "admin" : actor.username();
        String planName = scriptDisplayName(script) + " / 即时执行";
        com.yr.perftest.platform.task.TaskPlan plan = planService.createPlan(
                script.getProjectId(), planName, "从脚本列表直接执行", null, null, null, username, null);
        PersistentTaskPlanRecord raw = planRepository.findById(plan.id()).orElseThrow();
        raw.forceState(PlanPhase.EXECUTION, PlanStatus.PENDING);
        planRepository.save(raw);
        workflowService.systemComment(plan.id(), "快捷执行自动通过评审（操作人：" + username + "）");
        com.yr.perftest.platform.task.TaskScenario scenario = scenarioService.createScenario(
                plan.id(), scriptVersionId, scriptDisplayName(script), null, null, null, null, null, null, null);
        ExecutionControlService.StartOutcome outcome = executionControlService.start(
                new ExecutionControlService.StartCommand(scenario.id(), null, null, null), null);
        return new QuickExecuteResult(plan.id(), scenario.id(), outcome.executionId());
    }

    private String scriptDisplayName(PersistentScriptVersionRecord script) {
        return script.getOriginalFilename() != null ? script.getOriginalFilename() : "脚本-" + script.getId();
    }
}
```

（`PersistentScriptVersionRecord` 实际字段：`getProjectId()`、`getOriginalFilename()`——显示名用 originalFilename 去扩展名亦可，语义不变：计划名 = 脚本名 + " / 即时执行"。）

- [ ] **Step 4: 跑测试确认通过 + 全量回归**

Run: `export JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ && ./gradlew :backend:test --tests "com.yr.perftest.platform.task.plandoc.PlanExecutionLifecycleTest" && ./gradlew :backend:test`
Expected: PASS（3 个用例）+ 回归通过。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/yr/perftest/platform backend/src/test/java
git commit -m "feat：P0-1 Task9 执行终态联动——事件驱动回填场景块与 DONE 判定、快捷执行单事务四步"
```

---

### Task 10: 发布快照实体 + 只读分享链接 + 公开访问放行

**Files:**
- Create: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PersistentPlanPublishSnapshotRecord.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PersistentPlanPublishSnapshotRepository.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PersistentPlanShareTokenRecord.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PersistentPlanShareTokenRepository.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanWorkflowService.java`（分享 CRUD + 公开读取）
- Modify: `backend/src/main/java/com/yr/perftest/platform/config/SecurityConfiguration.java:45-51`（放行 `/api/share/**`）
- Modify: `docs/database/mysql-schema.sql`（两张新表）
- Test: `backend/src/test/java/com/yr/perftest/platform/task/plandoc/PlanShareTest.java`

**Interfaces:**
- Consumes: Task 5 `requireActor` 权限路径、`PlanPhase/PlanStatus`。
- Produces:
  - `PersistentPlanPublishSnapshotRecord(planId, revision, publishedBy, publishedAt, docJson, scenarioJson, summaryJson)`；repo `findAllByPlanIdOrderByRevisionDesc(Long)`、`deleteAllByPlanId(Long)`；表约束 `unique(planId, revision)`
  - `PersistentPlanShareTokenRecord(planId, token, expiresAt, createdBy)` + `revoke()`；repo `findByToken(String)`、`findAllByPlanIdOrderByIdDesc(Long)`、`deleteAllByPlanId(Long)`
  - `PlanWorkflowService`：
    - `record ShareView(long id, long planId, String token, Instant expiresAt, Instant revokedAt, String createdBy, Instant createdAt)`
    - `record SharedPlanView(String name, String body, Instant publishedAt)`
    - `ShareView createShare(long planId, HumanPrincipal actor, Integer expiresInDays)`（仅 PUBLISHED + ownerLike；默认 30 天）
    - `List<ShareView> listShares(long planId, HumanPrincipal actor)`
    - `void revokeShare(long planId, long tokenId, HumanPrincipal actor)`
    - `SharedPlanView getSharedPlan(String token)`（过期/撤销/非发布 → `PlanValidationException("SHARE_NOT_FOUND：…")`，HTTP 层转 404）
    - `PersistentPlanPublishSnapshotRecord buildPublishSnapshot(PersistentTaskPlanRecord plan, String publishedBy)`（Task 11 publish 调用；事务内 save）

- [ ] **Step 1: 写失败测试**

```java
package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.identity.SystemRole;
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

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-share-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PlanShareTest {

    private static final HumanPrincipal OWNER = new HumanPrincipal("owner", Set.of(SystemRole.PROJECT_MEMBER));
    private static final HumanPrincipal MEMBER = new HumanPrincipal("member-b", Set.of(SystemRole.PROJECT_MEMBER));

    @Autowired
    private PlanWorkflowService workflow;
    @Autowired
    private PersistentTaskPlanRepository planRepository;
    @Autowired
    private PersistentProjectRepository projectRepository;
    @Autowired
    private PersistentProjectMemberRepository memberRepository;
    @Autowired
    private PersistentPlanShareTokenRepository shareTokenRepository;

    private long planId;

    @BeforeEach
    void setUp() {
        PersistentProjectRecord project = projectRepository.save(new PersistentProjectRecord("P1", "项目一", "", "owner"));
        memberRepository.save(new PersistentProjectMemberRecord(project.getId(), "owner", ProjectRole.OWNER));
        memberRepository.save(new PersistentProjectMemberRecord(project.getId(), "member-b", ProjectRole.MEMBER));
        PersistentTaskPlanRecord plan = planRepository.save(
                new PersistentTaskPlanRecord(project.getId(), "已发布计划", null, "owner"));
        plan.updateBody("## 一、背景\n\n结论内容\n");
        plan.applyPublish(Instant.now());
        planId = planRepository.save(plan).getId();
    }

    @Test
    void shareCreationRestrictedToPublishedPlanAndOwner() {
        assertThatThrownBy(() -> workflow.createShare(planId, MEMBER, null))
                .isInstanceOf(PlanAccessDeniedException.class);
        PlanWorkflowService.ShareView share = workflow.createShare(planId, OWNER, 7);
        assertThat(share.token()).hasSize(36);
        PlanWorkflowService.SharedPlanView view = workflow.getSharedPlan(share.token());
        assertThat(view.name()).isEqualTo("已发布计划");
        assertThat(view.body()).contains("结论内容");
    }

    @Test
    void unpublishedPlanCannotShare() {
        PersistentTaskPlanRecord plan = planRepository.findById(planId).orElseThrow();
        plan.forceState(PlanPhase.REPORT, PlanStatus.DONE);
        planRepository.save(plan);
        assertThatThrownBy(() -> workflow.createShare(planId, OWNER, null))
                .isInstanceOf(PlanStateException.class);
    }

    @Test
    void revokedAndExpiredTokensReturnShareNotFound() {
        PlanWorkflowService.ShareView share = workflow.createShare(planId, OWNER, null);
        workflow.revokeShare(planId, share.id(), OWNER);
        assertThatThrownBy(() -> workflow.getSharedPlan(share.token()))
                .hasMessageContaining("SHARE_NOT_FOUND");

        PlanWorkflowService.ShareView expiring = workflow.createShare(planId, OWNER, 1);
        PersistentPlanShareTokenRecord raw = shareTokenRepository.findByToken(expiring.token()).orElseThrow();
        shareTokenRepository.save(raw); // 过期由 expiresAt 判定；把时间改到过去：
        java.lang.reflect.Field f;
        try {
            f = PersistentPlanShareTokenRecord.class.getDeclaredField("expiresAt");
            f.setAccessible(true);
            f.set(raw, Instant.now().minusSeconds(60));
        } catch (Exception ignored) {
        }
        shareTokenRepository.save(raw);
        assertThatThrownBy(() -> workflow.getSharedPlan(expiring.token()))
                .hasMessageContaining("SHARE_NOT_FOUND");
    }
}
```

（反射改时间是丑习惯——**直接给实体加包内可见的测试钩子更好**：`void expireForTest(Instant at)`。执行时用这个方法替换反射段。）

- [ ] **Step 2: 跑测试确认失败**

Run: `export JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ && ./gradlew :backend:test --tests "com.yr.perftest.platform.task.plandoc.PlanShareTest"`
Expected: 编译失败。

- [ ] **Step 3: 实现**

快照实体（含唯一约束）：

```java
package com.yr.perftest.platform.task.plandoc;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "plan_publish_snapshots", uniqueConstraints = @UniqueConstraint(columnNames = {"planId", "revision"}))
public class PersistentPlanPublishSnapshotRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long planId;

    @Column(nullable = false)
    private int revision;

    @Column(nullable = false, length = 80)
    private String publishedBy;

    @Column(nullable = false)
    private Instant publishedAt;

    @Lob
    @Column(nullable = false)
    private String docJson;

    @Lob
    @Column(nullable = false)
    private String scenarioJson;

    @Lob
    private String summaryJson;

    protected PersistentPlanPublishSnapshotRecord() {
    }

    public PersistentPlanPublishSnapshotRecord(Long planId, int revision, String publishedBy,
                                               Instant publishedAt, String docJson, String scenarioJson, String summaryJson) {
        this.planId = planId;
        this.revision = revision;
        this.publishedBy = publishedBy;
        this.publishedAt = publishedAt;
        this.docJson = docJson;
        this.scenarioJson = scenarioJson;
        this.summaryJson = summaryJson;
    }

    public Long getId() { return id; }
    public Long getPlanId() { return planId; }
    public int getRevision() { return revision; }
    public String getPublishedBy() { return publishedBy; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getDocJson() { return docJson; }
    public String getScenarioJson() { return scenarioJson; }
    public String getSummaryJson() { return summaryJson; }
}
```

```java
package com.yr.perftest.platform.task.plandoc;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PersistentPlanPublishSnapshotRepository extends JpaRepository<PersistentPlanPublishSnapshotRecord, Long> {
    List<PersistentPlanPublishSnapshotRecord> findAllByPlanIdOrderByRevisionDesc(Long planId);

    void deleteAllByPlanId(Long planId);
}
```

分享 token 实体（`token` 唯一）：

```java
package com.yr.perftest.platform.task.plandoc;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "plan_share_tokens", uniqueConstraints = @UniqueConstraint(columnNames = {"token"}))
public class PersistentPlanShareTokenRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long planId;

    @Column(nullable = false, length = 64)
    private String token;

    private Instant expiresAt;

    private Instant revokedAt;

    @Column(nullable = false, length = 80)
    private String createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    protected PersistentPlanShareTokenRecord() {
    }

    public PersistentPlanShareTokenRecord(Long planId, String token, Instant expiresAt, String createdBy) {
        this.planId = planId;
        this.token = token;
        this.expiresAt = expiresAt;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getPlanId() { return planId; }
    public String getToken() { return token; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }

    public void revoke() {
        this.revokedAt = Instant.now();
    }

    void expireForTest(Instant at) {
        this.expiresAt = at;
    }
}
```

```java
package com.yr.perftest.platform.task.plandoc;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PersistentPlanShareTokenRepository extends JpaRepository<PersistentPlanShareTokenRecord, Long> {
    Optional<PersistentPlanShareTokenRecord> findByToken(String token);

    List<PersistentPlanShareTokenRecord> findAllByPlanIdOrderByIdDesc(Long planId);

    void deleteAllByPlanId(Long planId);
}
```

`PlanWorkflowService` 追加（构造器追加 `PersistentPlanShareTokenRepository shareTokenRepository`、`PersistentPlanPublishSnapshotRepository snapshotRepository`、`ScenarioThreadGroupConfigSupport configSupport`——最后一个 Task 11 快照用，本任务先不加，Task 11 再加）：

```java
    public record ShareView(long id, long planId, String token, Instant expiresAt, Instant revokedAt, String createdBy, Instant createdAt) {
    }

    public record SharedPlanView(String name, String body, Instant publishedAt) {
    }

    @Transactional
    public ShareView createShare(long planId, HumanPrincipal actor, Integer expiresInDays) {
        PersistentTaskPlanRecord plan = requireActor(planId, actor, "SHARE");
        int days = expiresInDays == null || expiresInDays <= 0 ? 30 : expiresInDays;
        PersistentPlanShareTokenRecord saved = shareTokenRepository.save(new PersistentPlanShareTokenRecord(
                planId, java.util.UUID.randomUUID().toString(),
                Instant.now().plus(java.time.Duration.ofDays(days)), actor.username()));
        return toShareView(saved);
    }

    @Transactional(readOnly = true)
    public List<ShareView> listShares(long planId, HumanPrincipal actor) {
        requireActor(planId, actor, "SHARE");
        return shareTokenRepository.findAllByPlanIdOrderByIdDesc(planId).stream().map(this::toShareView).toList();
    }

    @Transactional
    public void revokeShare(long planId, long tokenId, HumanPrincipal actor) {
        requireActor(planId, actor, "SHARE");
        PersistentPlanShareTokenRecord token = shareTokenRepository.findById(tokenId)
                .filter(t -> t.getPlanId() == planId)
                .orElseThrow(() -> new PlanValidationException("SHARE_NOT_FOUND：分享链接不存在"));
        token.revoke();
    }

    @Transactional(readOnly = true)
    public SharedPlanView getSharedPlan(String token) {
        PersistentPlanShareTokenRecord record = shareTokenRepository.findByToken(token == null ? "" : token)
                .orElseThrow(() -> new PlanValidationException("SHARE_NOT_FOUND：分享链接不存在"));
        if (record.getRevokedAt() != null
                || (record.getExpiresAt() != null && record.getExpiresAt().isBefore(Instant.now()))) {
            throw new PlanValidationException("SHARE_NOT_FOUND：分享链接不存在");
        }
        PersistentTaskPlanRecord plan = requirePlan(record.getPlanId());
        if (plan.getPhase() != PlanPhase.PUBLISH) {
            throw new PlanValidationException("SHARE_NOT_FOUND：分享链接不存在");
        }
        return new SharedPlanView(plan.getName(), plan.getBody(), plan.getPublishedAt());
    }

    private ShareView toShareView(PersistentPlanShareTokenRecord record) {
        return new ShareView(record.getId(), record.getPlanId(), record.getToken(),
                record.getExpiresAt(), record.getRevokedAt(), record.getCreatedBy(), record.getCreatedAt());
    }
```

注意：`SHARE` 权限键含 `phase == PUBLISH` 前置（Task 3 矩阵已如此），`requireActor(planId, actor, "SHARE")` 对未发布计划抛 `PlanStateException` ✓（`unpublishedPlanCannotShare` 用例即验证此路径）。

`SecurityConfiguration` permitAll 列表追加一行（`"/api/share/**"` 加进现有 `requestMatchers(...)` 字符串组）：

```java
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/share/**",
                                "/v3/api-docs",
                                ...
```

`docs/database/mysql-schema.sql` 追加两表（对齐设计 §11.3，列定义按上文实体）：

```sql
-- ============================================================
-- 06b. 计划发布快照 / 计划分享链接
-- ============================================================
DROP TABLE IF EXISTS `plan_publish_snapshots`;
CREATE TABLE `plan_publish_snapshots` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `plan_id`       BIGINT       NOT NULL COMMENT '计划ID',
    `revision`      INT          NOT NULL COMMENT '发布时修订号',
    `published_by`  VARCHAR(80)  NOT NULL COMMENT '发布人',
    `published_at`  DATETIME(3)  NOT NULL COMMENT '发布时间',
    `doc_json`      LONGTEXT     NOT NULL COMMENT '发布时文档全文',
    `scenario_json` LONGTEXT     NOT NULL COMMENT '发布时场景列表（类型/设置/脚本版本）',
    `summary_json`  LONGTEXT              COMMENT '各场景最近成功执行摘要',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plan_snapshot` (`plan_id`, `revision`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='计划发布快照表';

DROP TABLE IF EXISTS `plan_share_tokens`;
CREATE TABLE `plan_share_tokens` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `plan_id`     BIGINT       NOT NULL COMMENT '计划ID',
    `token`       VARCHAR(64)  NOT NULL COMMENT '分享令牌',
    `expires_at`  DATETIME(3)           COMMENT '过期时间（空=永久）',
    `revoked_at`  DATETIME(3)           COMMENT '撤销时间',
    `created_by`  VARCHAR(80)  NOT NULL COMMENT '创建人',
    `created_at`  DATETIME(3)  NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_share_token` (`token`),
    KEY `idx_share_plan` (`plan_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='计划分享链接表';
```

- [ ] **Step 4: 跑测试确认通过**

Run: `export JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ && ./gradlew :backend:test --tests "com.yr.perftest.platform.task.plandoc.PlanShareTest"`
Expected: PASS（3 个用例）。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/yr/perftest/platform docs/database/mysql-schema.sql backend/src/test/java
git commit -m "feat：P0-1 Task10 发布快照与只读分享——token 实体、创建/撤销/过期判定、/api/share/** 放行"
```

---

### Task 11: 报告生成、发布终态与新修订（结论回填 + 快照固化）

**Files:**
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanWorkflowService.java`（generateReport/publish/newRevision/buildPublishSnapshot）
- Test: `backend/src/test/java/com/yr/perftest/platform/task/plandoc/PlanReportPublishTest.java`

**Interfaces:**
- Consumes: Task 9 `onExecutionTerminal/buildEntryLine` 的数据路径（`ExecutionQueryService.getResult`）、Task 10 `PersistentPlanPublishSnapshotRepository`、`ScenarioThreadGroupConfigSupport`。
- Produces:
  - `TaskPlan generateReport(long planId, HumanPrincipal actor)`：`REPORT/PENDING|DONE`→`REPORT/DONE`；写 `<!-- backfill:report -->` 执行结果总览块（整块替换幂等）+ 达成表"实际结果"列按场景名匹配填充
  - `TaskPlan publish(long planId, HumanPrincipal actor, String conclusion)`：校验（REPORT/DONE、无活跃执行、conclusion 非空）→ 结论写入 `**总体结论**：` → `applyPublish(now)` → `buildPublishSnapshot` → SYSTEM 批注 → 返回视图
  - `TaskPlan newRevision(long planId, HumanPrincipal actor)`：`PUBLISH`→DRAFT（`applyNewRevision`）+ SYSTEM 批注
  - `record SnapshotView(long id, int revision, String publishedBy, Instant publishedAt)` + `List<SnapshotView> listSnapshots(long planId, HumanPrincipal actor)`

- [ ] **Step 1: 写失败测试**

```java
package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.identity.SystemRole;
import com.yr.perftest.platform.project.PersistentProjectMemberRecord;
import com.yr.perftest.platform.project.PersistentProjectMemberRepository;
import com.yr.perftest.platform.project.PersistentProjectRecord;
import com.yr.perftest.platform.project.PersistentProjectRepository;
import com.yr.perftest.platform.project.ProjectRole;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.TaskPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-publish-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PlanReportPublishTest {

    private static final HumanPrincipal OWNER = new HumanPrincipal("owner", Set.of(SystemRole.PROJECT_MEMBER));

    @Autowired
    private PlanWorkflowService workflow;
    @Autowired
    private PersistentTaskPlanRepository planRepository;
    @Autowired
    private PersistentProjectRepository projectRepository;
    @Autowired
    private PersistentProjectMemberRepository memberRepository;
    @Autowired
    private PersistentPlanPublishSnapshotRepository snapshotRepository;
    @Autowired
    private PlanDocumentService documentService;

    private long planId;

    @BeforeEach
    void setUp() {
        PersistentProjectRecord project = projectRepository.save(new PersistentProjectRecord("P1", "项目一", "", "owner"));
        memberRepository.save(new PersistentProjectMemberRecord(project.getId(), "owner", ProjectRole.OWNER));
        PersistentTaskPlanRecord plan = planRepository.save(
                new PersistentTaskPlanRecord(project.getId(), "计划", null, "owner"));
        plan.updateBody("""
                ## 七、场景设计

                ### S1 登录 · SINGLE_TXN

                **场景目的**：p

                #### 执行记录

                ## 十一、结论

                ### 指标达成表

                | 指标 | 目标 | 实际结果 | 状态 |
                |---|---|---|---|
                | 登录 TPS | ≥ 200 | 待执行 | 待判定 |

                **总体结论**：（发布时填写）
                """);
        plan.forceState(PlanPhase.REPORT, PlanStatus.PENDING);
        planId = planRepository.save(plan).getId();
    }

    @Test
    void generateReportWritesOverviewAndActualColumn() {
        TaskPlan plan = workflow.generateReport(planId, OWNER);
        assertThat(plan.status()).isEqualTo(PlanStatus.DONE);
        String body = planRepository.findById(planId).orElseThrow().getBody();
        assertThat(body).contains("<!-- backfill:report -->");
        assertThat(body).contains("#### 执行结果总览");
        assertThat(body).contains("| 登录 TPS | ≥ 200 | 待执行 | 待判定 |"); // 无执行时实际列不被改写
        // 再生成：整块替换，不重复堆叠
        workflow.generateReport(planId, OWNER);
        String again = planRepository.findById(planId).orElseThrow().getBody();
        assertThat(again.split("<!-- backfill:report -->", -1).length - 1).isEqualTo(1);
        assertThat(again.split("#### 执行结果总览", -1).length - 1).isEqualTo(1);
    }

    @Test
    void publishRequiresConclusionAndWritesItAndSnapshot() {
        workflow.generateReport(planId, OWNER);
        assertThatThrownBy(() -> workflow.publish(planId, OWNER, " "))
                .isInstanceOf(PlanValidationException.class);
        TaskPlan published = workflow.publish(planId, OWNER, "核心指标全部达成，可上线。");
        assertThat(published.phase()).isEqualTo(PlanPhase.PUBLISH);
        assertThat(published.status()).isEqualTo(PlanStatus.PUBLISHED);
        String body = planRepository.findById(planId).orElseThrow().getBody();
        assertThat(body).contains("**总体结论**：核心指标全部达成，可上线。");
        var snapshots = workflow.listSnapshots(planId, OWNER);
        assertThat(snapshots).hasSize(1);
        assertThat(snapshotRepository.findAllByPlanIdOrderByRevisionDesc(planId).get(0).getDocJson()).contains("总体结论");
        // 冻结：编辑被拒
        assertThatThrownBy(() -> documentService.updateMarkdown(planId, published.revision(), "x", OWNER))
                .isInstanceOf(PlanStateException.class);
    }

    @Test
    void newRevisionResetsToDraftAndBumps() {
        workflow.generateReport(planId, OWNER);
        TaskPlan published = workflow.publish(planId, OWNER, "结论");
        int revision = published.revision();
        TaskPlan next = workflow.newRevision(planId, OWNER);
        assertThat(next.phase()).isEqualTo(PlanPhase.DRAFT);
        assertThat(next.status()).isEqualTo(PlanStatus.DRAFT);
        assertThat(next.revision()).isEqualTo(revision + 1);
        assertThat(planRepository.findById(planId).orElseThrow().getPrecheckExecutedAt()).isNull();
        assertThat(snapshotRepository.findAllByPlanIdOrderByRevisionDesc(planId)).hasSize(1); // 旧快照保留
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `export JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ && ./gradlew :backend:test --tests "com.yr.perftest.platform.task.plandoc.PlanReportPublishTest"`
Expected: 编译失败。

- [ ] **Step 3: 实现（PlanWorkflowService 追加）**

```java
    public record SnapshotView(long id, int revision, String publishedBy, Instant publishedAt) {
    }

    @Transactional
    public TaskPlan generateReport(long planId, HumanPrincipal actor) {
        PersistentTaskPlanRecord plan = requireActor(planId, actor, "GENERATE_REPORT");
        plan.transitionTo(PlanPhase.REPORT, PlanStatus.GENERATING); // 瞬态（设计 §4.1）
        String body = plan.getBody() == null ? "" : plan.getBody();
        body = upsertReportOverview(body, buildScenarioOverviews(planId));
        body = fillConclusionActualColumn(body, latestScenarioSummaries(planId));
        plan.updateBody(body);
        plan.transitionTo(PlanPhase.REPORT, PlanStatus.DONE);
        systemComment(planId, "生成报告（revision=" + plan.getRevision() + "）");
        return planService.getPlan(planId);
    }

    @Transactional
    public TaskPlan publish(long planId, HumanPrincipal actor, String conclusion) {
        PersistentTaskPlanRecord plan = requireActor(planId, actor, "PUBLISH");
        if (conclusion == null || conclusion.isBlank()) {
            throw new PlanValidationException("PLAN_INVALID：发布必须填写总体结论");
        }
        if (documentService.hasActiveExecution(planId)) {
            throw new PlanStateException("PLAN_STATE：存在活跃执行，不可发布",
                    plan.getPhase(), plan.getStatus(), List.of("GENERATE_REPORT"));
        }
        String body = plan.getBody() == null ? "" : plan.getBody();
        String line = "**总体结论**：" + conclusion.trim();
        if (body.contains("**总体结论**：")) {
            int start = body.indexOf("**总体结论**：");
            int end = body.indexOf('\n', start);
            body = end < 0 ? body.substring(0, start) + line : body.substring(0, start) + line + body.substring(end);
        } else {
            body = PlanMarkdownSupport.ensureSection(body, "十一、结论",
                    PlanMarkdownSupport.extractSection(body, "十一、结论") + "\n" + line + "\n");
        }
        plan.updateBody(body);
        Instant now = Instant.now();
        buildPublishSnapshot(plan, actor.username(), now);
        plan.applyPublish(now);
        systemComment(planId, "已发布（revision=" + plan.getRevision() + "，发布人：" + actor.username() + "）");
        return planService.getPlan(planId);
    }

    @Transactional
    public TaskPlan newRevision(long planId, HumanPrincipal actor) {
        PersistentTaskPlanRecord plan = requireActor(planId, actor, "NEW_REVISION");
        plan.applyNewRevision();
        systemComment(planId, actor.username() + " 发起新修订（revision=" + plan.getRevision() + "）");
        return planService.getPlan(planId);
    }

    @Transactional(readOnly = true)
    public List<SnapshotView> listSnapshots(long planId, HumanPrincipal actor) {
        requireActor(planId, actor, "SHARE"); // 发布域只读，沿用 owner 级动作门槛
        return snapshotRepository.findAllByPlanIdOrderByRevisionDesc(planId).stream()
                .map(s -> new SnapshotView(s.getId(), s.getRevision(), s.getPublishedBy(), s.getPublishedAt()))
                .toList();
    }

    @Transactional
    public PersistentPlanPublishSnapshotRecord buildPublishSnapshot(PersistentTaskPlanRecord plan, String publishedBy, Instant at) {
        List<PersistentTaskScenarioRecord> scenarios = scenarioRepository.findAllByPlanIdOrderBySortOrderAscIdAsc(plan.getId());
        String scenarioJson = writeJsonOrEmpty(scenarios.stream().map(s -> java.util.Map.of(
                "id", s.getId(),
                "name", s.getName(),
                "testType", s.getTestType() == null ? "" : s.getTestType().name(),
                "purpose", s.getPurpose() == null ? "" : s.getPurpose(),
                "scriptVersionId", s.getScriptVersionId() == null ? 0L : s.getScriptVersionId(),
                "threadGroupConfigs", configSupport.readStored(s.getThreadGroupConfigsJson())
        )).toList());
        String summaryJson = writeJsonOrEmpty(latestScenarioSummaries(plan.getId()));
        return snapshotRepository.save(new PersistentPlanPublishSnapshotRecord(
                plan.getId(), plan.getRevision(), publishedBy, at,
                writeJsonOrEmpty(java.util.Map.of("body", plan.getBody() == null ? "" : plan.getBody())),
                scenarioJson, summaryJson));
    }

    /** 每场景最近一次终态执行的摘要行（发布快照 summaryJson 与达成表填充共用）。 */
    private List<java.util.Map<String, String>> latestScenarioSummaries(long planId) {
        List<java.util.Map<String, String>> result = new java.util.ArrayList<>();
        for (PersistentTaskScenarioRecord scenario : scenarioRepository.findAllByPlanIdOrderBySortOrderAscIdAsc(planId)) {
            executionRepository.findFirstByScenarioIdOrderByIdDesc(scenario.getId()).ifPresent(execution -> {
                String line = buildEntryLine(execution);
                result.add(java.util.Map.of(
                        "scenarioName", scenario.getName(),
                        "status", execution.getStatus().name(),
                        "summary", line.substring(2)));
            });
        }
        return result;
    }

    private String buildScenarioOverviews(long planId) {
        StringBuilder block = new StringBuilder();
        for (java.util.Map<String, String> summary : latestScenarioSummaries(planId)) {
            block.append("- ").append(summary.get("scenarioName")).append(" · ")
                    .append(summary.get("status")).append(" · ").append(summary.get("summary")).append('\n');
        }
        if (block.isEmpty()) {
            block.append("- （暂无执行记录）\n");
        }
        return block.toString();
    }

    /** 幂等替换报告总览块：标记行起到下一标题行为止。 */
    private String upsertReportOverview(String body, String overviewLines) {
        String timestamp = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(java.time.ZoneId.systemDefault()).format(Instant.now());
        String block = "<!-- backfill:report -->\n#### 执行结果总览（生成于 " + timestamp + "）\n" + overviewLines;
        int marker = body.indexOf("<!-- backfill:report -->");
        if (marker < 0) {
            String conclusion = PlanMarkdownSupport.extractSection(body, "十一、结论");
            if (conclusion == null) {
                return PlanMarkdownSupport.ensureSection(body, "十一、结论", "\n" + block);
            }
            return PlanMarkdownSupport.replaceSection(body, "十一、结论", conclusion + block);
        }
        int end = body.length();
        for (String line : body.substring(marker).split("\n", -1)) {
            if (line.startsWith("#### ") && body.indexOf(line, marker) > marker + 10) {
                end = body.indexOf(line, marker);
                break;
            }
        }
        // 块尾 = 标记起点之后第一个非块标题的下一个标题（### / ## / ####）行；实现按行扫描：
        String[] lines = body.substring(marker).split("\n", -1);
        int offset = marker;
        boolean sawHeading = false;
        for (int i = 0; i < lines.length; i++) {
            if (i == 0 || (lines[i].startsWith("#### 执行结果总览") && i == 1)) {
                offset += lines[i].length() + 1;
                continue;
            }
            if (sawHeading && (lines[i].startsWith("#") || lines[i].startsWith("**总体结论**"))) {
                end = offset;
                break;
            }
            if (lines[i].startsWith("#### 执行结果总览")) {
                sawHeading = true;
            }
            offset += lines[i].length() + 1;
        }
        return body.substring(0, marker) + block + body.substring(Math.min(end, body.length()));
    }

    /** 达成表"实际结果"列：指标列包含场景名的行填入该场景最近摘要；无执行填"暂无执行"。 */
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
        for (String line : conclusion.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("|") && !trimmed.startsWith("|---") && !trimmed.contains("实际结果")) {
                String firstCell = trimmed.substring(1, trimmed.indexOf('|', 1)).trim();
                String matched = knownScenarios.stream().filter(firstCell::contains).findFirst().orElse(null);
                if (matched != null) {
                    String summary = summaries.stream()
                            .filter(s -> s.get("scenarioName").equals(matched)).findFirst().orElseThrow().get("summary");
                    line = replaceTableRowCell(line, 2, summary);
                }
            }
            updated.append(line).append('\n');
        }
        return PlanMarkdownSupport.replaceSection(body, "十一、结论", updated.toString());
    }

    /** 替换 Markdown 表格行第 index 个数据单元格（0 基）。 */
    private String replaceTableRowCell(String row, int cellIndex, String newValue) {
        String[] cells = row.split("\\|", -1);
        // cells[0] 为行首 | 前的空串；数据单元格从 cells[1] 开始
        int target = cellIndex + 1;
        if (target >= cells.length - 1) {
            return row;
        }
        cells[target] = " " + newValue + " ";
        return String.join("|", cells);
    }

    private String writeJsonOrEmpty(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return "{}";
        }
    }
```

实现备注：
1. `publish` 顺序固定：校验 → 写结论（`updateBody`，revision+1）→ `buildPublishSnapshot`（此时 `getRevision()` 即发布终值，快照存终值不加 1）→ `applyPublish(now)` → SYSTEM 批注。
2. `upsertReportOverview` 的行扫描实现保留一个简单不变量：`marker` 之前内容与"下一个 `**总体结论**` 或下一个 `## `/`### ` 标题"之后内容不动，中间整块替换；以测试断言（块恰好出现一次）为准。
3. `PlanWorkflowService` 构造器在本任务追加 `TaskPlanService planService` 与 `ScenarioThreadGroupConfigSupport configSupport`、`PersistentPlanPublishSnapshotRepository snapshotRepository`（`shareTokenRepository` Task 10 已加）。

- [ ] **Step 4: 跑测试确认通过 + 全量回归**

Run: `export JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ && ./gradlew :backend:test --tests "com.yr.perftest.platform.task.plandoc.PlanReportPublishTest" && ./gradlew :backend:test`
Expected: PASS（3 个用例）+ 回归通过。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/yr/perftest/platform backend/src/test/java
git commit -m "feat：P0-1 Task11 报告生成与发布终态——结果总览回填、达成表实际列、发布快照、新修订重置"
```

---

### Task 12: REST 面 —— PlanDocumentController、错误映射、端点迁移与级联删除

**Files:**
- Create: `backend/src/main/java/com/yr/perftest/platform/api/PlanDocumentController.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/api/PlanErrorBody.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/api/PlatformExceptionHandler.java`（注册 5 个 plan 异常）
- Modify: `backend/src/main/java/com/yr/perftest/platform/api/TaskPlanController.java`（**移除** `getPlan/updatePlan/deletePlan` 三个项级端点——迁移至 PlanDocumentController；`createPlan` 身份改 principal 优先、`X-User` 兜底；新增 quick-execute）
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/TaskPlanService.java`（`updatePlan` 加 DRAFT 校验；`deletePlan` 级联三张新表）
- Test: `backend/src/test/java/com/yr/perftest/platform/api/PlanDocumentApiTest.java`

**Interfaces:**
- Consumes: Task 4-11 全部服务面（`PlanDocumentService.getDocument/updateMarkdown`、`PlanWorkflowService` 12 流转+批注+模板+分享+快照+precheck、`PlanQuickExecuteService.quickExecute`、`TaskPlanService.updatePlan/deletePlan`）、`ProjectAccessResolver`、`report/ReportDataService.aggregateByPlan`。
- Produces（前端 Task 13 的对接合同）：
  - `GET /api/task-plans/{planId}` → `{ plan: TaskPlan, permissions: Record<string,boolean> }`（`PlanResponse(TaskPlan plan, Map<String,Boolean> permissions)`）
  - `PUT /api/task-plans/{planId}/document` body `{baseRevision: number, markdown: string}` → `TaskPlan`；409 冲突体 `{code, message, currentRevision, serverMarkdown}`
  - `POST /api/task-plans/{planId}/{submit|start-review|approve|reject|withdraw|back-to-draft|start-execution|to-report|generate-report|new-revision}` body 可选 `{comment}`；`publish` body `{conclusion}` 必填；均返回 `PlanResponse`
  - `POST /api/task-plans/{planId}/precheck-run` → `{ok, failures[], autoPassed[]}`；`POST .../precheck-skip` → `PlanResponse`；`PUT .../precheck-settings` body `{enabled, items[]}`
  - `GET|POST /api/task-plans/{planId}/comments`（POST body `{content}`）、`DELETE .../comments/{commentId}`
  - `GET /api/task-plans/{planId}/snapshots` → `SnapshotView[]`；`GET .../report` → `PlanReportResponse`
  - `GET|POST /api/projects/{projectId}/plan-templates`、`PUT|DELETE /api/plan-templates/{templateId}` → 模板 CRUD（`TemplateRequest {name, description, content}`）
  - `GET|POST /api/task-plans/{planId}/shares`（POST body `{expiresInDays?}`）、`DELETE .../shares/{tokenId}`
  - `GET /api/share/plans/{token}`（匿名）→ `{name, body, publishedAt}`
  - `POST /api/scripts/{scriptVersionId}/quick-execute` → `{planId, scenarioId, executionId}`
  - 错误码见 Global Constraints；`SHARE_NOT_FOUND` 走 404

- [ ] **Step 1: 写失败测试**

```java
package com.yr.perftest.platform.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.plandoc.PlanPhase;
import com.yr.perftest.platform.task.plandoc.PlanStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-api-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PlanDocumentApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PersistentTaskPlanRepository planRepository;

    private String token;
    private long planId;

    @BeforeEach
    void setUp() throws Exception {
        token = AuthTestSupport.loginToken(mockMvc, objectMapper);
        PersistentTaskPlanRecord plan = planRepository.save(new PersistentTaskPlanRecord(1L, "计划A", null, "admin"));
        plan.updateBody("## 一、背景\n\n内容\n");
        planId = planRepository.save(plan).getId();
    }

    private MvcResult transition(String action, String body) throws Exception {
        return mockMvc.perform(post("/api/task-plans/" + planId + "/" + action)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body == null ? "{}" : body))
                .andReturn();
    }

    @Test
    void getPlanReturnsPermissionsAlongside() throws Exception {
        mockMvc.perform(get("/api/task-plans/" + planId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.name").value("计划A"))
                .andExpect(jsonPath("$.plan.phase").value("DRAFT"))
                .andExpect(jsonPath("$.permissions.SUBMIT").value(true))
                .andExpect(jsonPath("$.permissions.PUBLISH").value(false));
    }

    @Test
    void fullTransitionChainOverRest() throws Exception {
        transition("submit", "{\"comment\":\"请评审\"}").getResponse().getStatus();
        transition("start-review", null);
        transition("approve", null);
        transition("start-execution", null);
        mockMvc.perform(get("/api/task-plans/" + planId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.plan.phase").value("EXECUTION"))
                .andExpect(jsonPath("$.plan.status").value("PENDING"));
    }

    @Test
    void illegalTransitionReturns409WithAllowedActions() throws Exception {
        MvcResult result = transition("approve", null);
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("code").asText()).isEqualTo("PLAN_STATE");
        assertThat(body.get("allowedActions").toString()).contains("SUBMIT");
    }

    @Test
    void documentConflictReturns409WithServerMarkdown() throws Exception {
        mockMvc.perform(put("/api/task-plans/" + planId + "/document")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseRevision\":1,\"markdown\":\"## 一、背景\\n\\n甲版\\n\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(2));
        MvcResult conflict = mockMvc.perform(put("/api/task-plans/" + planId + "/document")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseRevision\":1,\"markdown\":\"## 一、背景\\n\\n乙版\\n\"}"))
                .andReturn();
        assertThat(conflict.getResponse().getStatus()).isEqualTo(409);
        JsonNode body = objectMapper.readTree(conflict.getResponse().getContentAsString());
        assertThat(body.get("code").asText()).isEqualTo("PLAN_REVISION_CONFLICT");
        assertThat(body.get("currentRevision").asInt()).isEqualTo(2);
        assertThat(body.get("serverMarkdown").asText()).contains("甲版");
    }

    @Test
    void rejectRequiresCommentOverRest() throws Exception {
        transition("submit", null);
        transition("start-review", null);
        assertThat(transition("reject", "{\"comment\":\"\"}").getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void publishRequiresConclusionOverRest() throws Exception {
        PersistentTaskPlanRecord plan = planRepository.findById(planId).orElseThrow();
        plan.forceState(PlanPhase.REPORT, PlanStatus.DONE);
        planRepository.save(plan);
        assertThat(transition("publish", "{\"conclusion\":\" \"}").getResponse().getStatus()).isEqualTo(400);
        transition("publish", "{\"conclusion\":\"达成，可发布\"}");
        mockMvc.perform(get("/api/task-plans/" + planId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.plan.phase").value("PUBLISH"))
                .andExpect(jsonPath("$.plan.body").value(org.hamcrest.Matchers.containsString("总体结论：达成，可发布")));
    }

    @Test
    void precheckEndpointsRoundTrip() throws Exception {
        mockMvc.perform(put("/api/task-plans/" + planId + "/precheck-settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"items\":[\"指标已定义\",\"场景已配置\"]}"))
                .andExpect(status().isOk());
        MvcResult run = mockMvc.perform(post("/api/task-plans/" + planId + "/precheck-run")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        JsonNode report = objectMapper.readTree(run.getResponse().getContentAsString());
        assertThat(report.get("failures").size()).isGreaterThan(0); // 无场景 → 场景已配置 未过
    }

    @Test
    void commentsRoundTripAndShareNotFound() throws Exception {
        mockMvc.perform(post("/api/task-plans/" + planId + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"补充口径\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/task-plans/" + planId + "/comments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[0].content").value("补充口径"));
        mockMvc.perform(get("/api/share/plans/not-a-token"))
                .andExpect(status().isNotFound());
    }
}
```

（quick-execute 的 MockMvc 全链路涉及脚本文件与节点落盘，放进本测试代价过高——**在 Task 18 的手工验收清单里以真实界面覆盖**；服务级逻辑已在 Task 9 代码路径中由门禁/创建测试覆盖。若执行时发现可低成本造脚本版本（`PersistentScriptVersionRecord` 直存 + storedPath 指向仓库内测试 JMX），可自行补一条 API 用例，不强制。）

- [ ] **Step 2: 跑测试确认失败**

Run: `export JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ && ./gradlew :backend:test --tests "com.yr.perftest.platform.api.PlanDocumentApiTest"`
Expected: 404/405（端点不存在）。

- [ ] **Step 3: 实现**

```java
package com.yr.perftest.platform.api;

public record PlanErrorBody(
        String code,
        String message,
        Integer currentRevision,
        String serverMarkdown,
        String phase,
        String status,
        java.util.List<String> allowedActions
) {
    public static PlanErrorBody of(String code, String message) {
        return new PlanErrorBody(code, message, null, null, null, null, null);
    }
}
```

`PlatformExceptionHandler` 追加（import `com.yr.perftest.platform.task.plandoc.*` 五个异常）：

```java
    @ExceptionHandler(com.yr.perftest.platform.task.plandoc.PlanStateException.class)
    public ResponseEntity<PlanErrorBody> handlePlanState(com.yr.perftest.platform.task.plandoc.PlanStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new PlanErrorBody(
                "PLAN_STATE", exception.getMessage(), null, null,
                exception.getPhase().name(), exception.getStatus().name(), exception.getAllowedActions()));
    }

    @ExceptionHandler(com.yr.perftest.platform.task.plandoc.PlanRevisionConflictException.class)
    public ResponseEntity<PlanErrorBody> handlePlanRevisionConflict(com.yr.perftest.platform.task.plandoc.PlanRevisionConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new PlanErrorBody(
                "PLAN_REVISION_CONFLICT", exception.getMessage(),
                exception.getCurrentRevision(), exception.getServerMarkdown(), null, null, null));
    }

    @ExceptionHandler(com.yr.perftest.platform.task.plandoc.PlanPrecheckFailedException.class)
    public ResponseEntity<PlanErrorBody> handlePlanPrecheck(com.yr.perftest.platform.task.plandoc.PlanPrecheckFailedException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(PlanErrorBody.of("PLAN_PRECHECK_FAILED", exception.getMessage()));
    }

    @ExceptionHandler(com.yr.perftest.platform.task.plandoc.PlanAccessDeniedException.class)
    public ResponseEntity<PlanErrorBody> handlePlanAccessDenied(com.yr.perftest.platform.task.plandoc.PlanAccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(PlanErrorBody.of("PLAN_ACCESS_DENIED", exception.getMessage()));
    }

    @ExceptionHandler(com.yr.perftest.platform.task.plandoc.PlanValidationException.class)
    public ResponseEntity<PlanErrorBody> handlePlanValidation(com.yr.perftest.platform.task.plandoc.PlanValidationException exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage();
        if (message.startsWith("SHARE_NOT_FOUND")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(PlanErrorBody.of("SHARE_NOT_FOUND", message));
        }
        return ResponseEntity.badRequest().body(PlanErrorBody.of("PLAN_INVALID", message));
    }
```

`PlanDocumentController` 完整骨架（每端点一两行委托；`requireHuman()` 取 SecurityContext）：

```java
package com.yr.perftest.platform.api;

import com.yr.perftest.platform.identity.AuthenticationException;
import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.project.ProjectAccessResolver;
import com.yr.perftest.platform.report.PlanReportResponse;
import com.yr.perftest.platform.report.ReportDataService;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.TaskPlan;
import com.yr.perftest.platform.task.TaskPlanService;
import com.yr.perftest.platform.task.plandoc.PlanAccess;
import com.yr.perftest.platform.task.plandoc.PlanDocumentService;
import com.yr.perftest.platform.task.plandoc.PlanQuickExecuteService;
import com.yr.perftest.platform.task.plandoc.PlanWorkflowService;
import com.yr.perftest.platform.task.plandoc.PlanWorkflowService.CommentView;
import com.yr.perftest.platform.task.plandoc.PlanWorkflowService.PrecheckReport;
import com.yr.perftest.platform.task.plandoc.PrecheckSettings;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PlanDocumentController {
    private final PlanDocumentService documentService;
    private final PlanWorkflowService workflowService;
    private final PlanQuickExecuteService quickExecuteService;
    private final TaskPlanService planService;
    private final ProjectAccessResolver accessResolver;
    private final ReportDataService reportDataService;

    public PlanDocumentController(PlanDocumentService documentService,
                                  PlanWorkflowService workflowService,
                                  PlanQuickExecuteService quickExecuteService,
                                  TaskPlanService planService,
                                  ProjectAccessResolver accessResolver,
                                  ReportDataService reportDataService) {
        this.documentService = documentService;
        this.workflowService = workflowService;
        this.quickExecuteService = quickExecuteService;
        this.planService = planService;
        this.accessResolver = accessResolver;
        this.reportDataService = reportDataService;
    }

    public record PlanResponse(TaskPlan plan, Map<String, Boolean> permissions) {
    }

    @GetMapping("/task-plans/{planId}")
    public PlanResponse getPlan(@PathVariable long planId) {
        TaskPlan plan = documentService.getDocument(planId);
        return new PlanResponse(plan, permissionsOf(plan));
    }

    @PutMapping("/task-plans/{planId}/document")
    public TaskPlan updateDocument(@PathVariable long planId, @RequestBody UpdateDocumentRequest request) {
        return documentService.updateMarkdown(planId, request.baseRevision(), request.markdown(), requireHuman());
    }

    @PutMapping("/task-plans/{planId}")
    public TaskPlan updateDefaultConfig(@PathVariable long planId, @RequestBody UpdatePlanConfigRequest request) {
        return planService.updatePlan(planId, request.name(), request.remark(),
                request.controllerNodeId(), request.workerNodeIds(), request.monitorTargetIds());
    }

    @DeleteMapping("/task-plans/{planId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePlan(@PathVariable long planId) {
        TaskPlan plan = planService.getPlan(planId);
        Map<String, Boolean> permissions = permissionsOf(plan);
        if (!Boolean.TRUE.equals(permissions.get("DELETE"))) {
            throw new com.yr.perftest.platform.task.plandoc.PlanAccessDeniedException(
                    "PLAN_ACCESS_DENIED：仅负责人/项目 OWNER/系统管理员可删除计划");
        }
        planService.deletePlan(planId);
    }

    @PostMapping("/task-plans/{planId}/submit")
    public PlanResponse submit(@PathVariable long planId, @RequestBody(required = false) CommentRequest request) {
        workflowService.submit(planId, requireHuman(), request == null ? null : request.comment());
        return getPlan(planId);
    }

    @PostMapping("/task-plans/{planId}/start-review")
    public PlanResponse startReview(@PathVariable long planId) {
        workflowService.startReview(planId, requireHuman());
        return getPlan(planId);
    }

    @PostMapping("/task-plans/{planId}/approve")
    public PlanResponse approve(@PathVariable long planId, @RequestBody(required = false) CommentRequest request) {
        workflowService.approve(planId, requireHuman(), request == null ? null : request.comment());
        return getPlan(planId);
    }

    @PostMapping("/task-plans/{planId}/reject")
    public PlanResponse reject(@PathVariable long planId, @RequestBody CommentRequest request) {
        workflowService.reject(planId, requireHuman(), request.comment());
        return getPlan(planId);
    }

    @PostMapping("/task-plans/{planId}/withdraw")
    public PlanResponse withdraw(@PathVariable long planId) {
        workflowService.withdraw(planId, requireHuman());
        return getPlan(planId);
    }

    @PostMapping("/task-plans/{planId}/back-to-draft")
    public PlanResponse backToDraft(@PathVariable long planId) {
        workflowService.backToDraft(planId, requireHuman());
        return getPlan(planId);
    }

    @PostMapping("/task-plans/{planId}/start-execution")
    public PlanResponse startExecution(@PathVariable long planId) {
        workflowService.startExecution(planId, requireHuman());
        return getPlan(planId);
    }

    @PostMapping("/task-plans/{planId}/to-report")
    public PlanResponse toReport(@PathVariable long planId) {
        workflowService.toReport(planId, requireHuman());
        return getPlan(planId);
    }

    @PostMapping("/task-plans/{planId}/generate-report")
    public PlanResponse generateReport(@PathVariable long planId) {
        workflowService.generateReport(planId, requireHuman());
        return getPlan(planId);
    }

    @PostMapping("/task-plans/{planId}/publish")
    public PlanResponse publish(@PathVariable long planId, @RequestBody PublishRequest request) {
        workflowService.publish(planId, requireHuman(), request.conclusion());
        return getPlan(planId);
    }

    @PostMapping("/task-plans/{planId}/new-revision")
    public PlanResponse newRevision(@PathVariable long planId) {
        workflowService.newRevision(planId, requireHuman());
        return getPlan(planId);
    }

    @PostMapping("/task-plans/{planId}/precheck-run")
    public PrecheckReport precheckRun(@PathVariable long planId) {
        return workflowService.runPrecheck(planId, true);
    }

    @PostMapping("/task-plans/{planId}/precheck-skip")
    public PlanResponse precheckSkip(@PathVariable long planId) {
        workflowService.precheckSkip(planId, requireHuman());
        return getPlan(planId);
    }

    @PutMapping("/task-plans/{planId}/precheck-settings")
    public PlanResponse precheckSettings(@PathVariable long planId, @RequestBody PrecheckSettings settings) {
        workflowService.updatePrecheckSettings(planId, requireHuman(), settings);
        return getPlan(planId);
    }

    @GetMapping("/task-plans/{planId}/comments")
    public List<CommentView> listComments(@PathVariable long planId) {
        requireHuman();
        return workflowService.listComments(planId);
    }

    @PostMapping("/task-plans/{planId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentView addComment(@PathVariable long planId, @RequestBody CommentRequest request) {
        return workflowService.addComment(planId, requireHuman(), request.comment());
    }

    @DeleteMapping("/task-plans/{planId}/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable long planId, @PathVariable long commentId) {
        workflowService.deleteComment(planId, commentId, requireHuman());
    }

    @GetMapping("/task-plans/{planId}/snapshots")
    public List<PlanWorkflowService.SnapshotView> listSnapshots(@PathVariable long planId) {
        return workflowService.listSnapshots(planId, requireHuman());
    }

    @GetMapping("/task-plans/{planId}/report")
    public PlanReportResponse report(@PathVariable long planId) {
        return reportDataService.aggregateByPlan(planId);
    }

    @PostMapping("/task-plans/{planId}/shares")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanWorkflowService.ShareView createShare(@PathVariable long planId,
                                                     @RequestBody(required = false) ShareRequest request) {
        return workflowService.createShare(planId, requireHuman(), request == null ? null : request.expiresInDays());
    }

    @GetMapping("/task-plans/{planId}/shares")
    public List<PlanWorkflowService.ShareView> listShares(@PathVariable long planId) {
        return workflowService.listShares(planId, requireHuman());
    }

    @DeleteMapping("/task-plans/{planId}/shares/{tokenId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeShare(@PathVariable long planId, @PathVariable long tokenId) {
        workflowService.revokeShare(planId, tokenId, requireHuman());
    }

    @GetMapping("/share/plans/{token}")
    public PlanWorkflowService.SharedPlanView sharedPlan(@PathVariable String token) {
        return workflowService.getSharedPlan(token);
    }

    @PostMapping("/scripts/{scriptVersionId}/quick-execute")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanQuickExecuteService.QuickExecuteResult quickExecute(@PathVariable long scriptVersionId) {
        return quickExecuteService.quickExecute(scriptVersionId, requireHuman());
    }

    private Map<String, Boolean> permissionsOf(TaskPlan plan) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        HumanPrincipal principal = authentication != null
                && authentication.getPrincipal() instanceof HumanPrincipal human ? human : null;
        if (principal == null) {
            return Map.of();
        }
        ProjectAccessResolver.PlanActorRole role = accessResolver.resolve(plan.projectId(), principal, plan.createdBy());
        return PlanAccess.compute(role, plan.phase(), plan.status(), workflowService.hasAnyExecution(plan.id()));
    }

    private HumanPrincipal requireHuman() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof HumanPrincipal human) {
            return human;
        }
        throw new AuthenticationException("login required");
    }

    public record UpdateDocumentRequest(long baseRevision, String markdown) {
    }

    public record UpdatePlanConfigRequest(String name, String remark, Long controllerNodeId,
                                          List<Long> workerNodeIds, List<Long> monitorTargetIds) {
    }

    public record CommentRequest(String comment) {
    }

    public record PublishRequest(String conclusion) {
    }

    public record ShareRequest(Integer expiresInDays) {
    }
}
```

`PlanDocumentController` 追加模板四端点（实现 §12.2 模板 API；`TemplateRequest` 为其嵌套 record）：

```java
    @GetMapping("/projects/{projectId}/plan-templates")
    public List<com.yr.perftest.platform.task.plandoc.PersistentPlanTemplateRecord> listTemplates(@PathVariable long projectId) {
        requireHuman();
        return workflowService.listTemplates(projectId);
    }

    @PostMapping("/projects/{projectId}/plan-templates")
    @ResponseStatus(HttpStatus.CREATED)
    public com.yr.perftest.platform.task.plandoc.PersistentPlanTemplateRecord createTemplate(
            @PathVariable long projectId, @RequestBody TemplateRequest request) {
        return workflowService.createTemplate(projectId, requireHuman(), request.name(), request.description(), request.content());
    }

    @PutMapping("/plan-templates/{templateId}")
    public com.yr.perftest.platform.task.plandoc.PersistentPlanTemplateRecord updateTemplate(
            @PathVariable long templateId, @RequestBody TemplateRequest request) {
        return workflowService.updateTemplate(templateId, requireHuman(), request.name(), request.description(), request.content());
    }

    @DeleteMapping("/plan-templates/{templateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTemplate(@PathVariable long templateId) {
        workflowService.deleteTemplate(templateId, requireHuman());
    }

    public record TemplateRequest(String name, String description, String content) {
    }
```

`TaskPlanController`：
1. **删除** `getPlan/updatePlan/deletePlan` 三个方法与 `UpdateTaskPlanRequest`（迁移完成，避免路径二义）。
2. `createPlan` 身份改为 principal 优先：方法签名加 `Authentication authentication` 参数（`org.springframework.security.core.annotation.AuthenticationPrincipal` 不适合匿名场景，直接注入 `Authentication`），service 调用前解析：

```java
    @PostMapping("/projects/{projectId}/task-plans")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskPlan createPlan(
            @PathVariable long projectId,
            @Valid @RequestBody CreateTaskPlanRequest request,
            @RequestHeader(name = "X-User", defaultValue = "admin") String createdBy
    ) {
        String actor = createdBy;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof HumanPrincipal human) {
            actor = human.username();
        }
        return planService.createPlan(projectId, request.name(), request.remark(),
                request.controllerNodeId(), request.workerNodeIds(), request.monitorTargetIds(),
                actor, request.templateId());
    }
```

`CreateTaskPlanRequest` 已在 Task 6 加过 `templateId`。

`TaskPlanService`：
1. `updatePlan` 首行加：

```java
        if (plan.getPhase() != com.yr.perftest.platform.task.plandoc.PlanPhase.DRAFT) {
            throw new com.yr.perftest.platform.task.plandoc.PlanStateException(
                    "PLAN_STATE：默认执行配置仅草稿阶段可修改（当前 " + plan.getPhase() + "/" + plan.getStatus() + "）",
                    plan.getPhase(), plan.getStatus(), java.util.List.of("WITHDRAW", "BACK_TO_DRAFT"));
        }
```

2. `deletePlan` 级联（构造器追加 `PersistentPlanCommentRepository commentRepository`、`PersistentPlanPublishSnapshotRepository snapshotRepository`、`PersistentPlanShareTokenRepository shareTokenRepository`）：

```java
    @Transactional
    public void deletePlan(long planId) {
        PersistentTaskPlanRecord plan = planRepository.findById(planId)
                .orElseThrow(() -> new ExecutionValidationException("task plan does not exist"));
        scenarioRepository.findAllByPlanIdOrderBySortOrderAscIdAsc(plan.getId()).forEach(scenario ->
                executionRepository.deleteAllByScenarioId(scenario.getId()));
        scenarioRepository.deleteAllByPlanId(plan.getId());
        commentRepository.deleteAllByPlanId(plan.getId());
        snapshotRepository.deleteAllByPlanId(plan.getId());
        shareTokenRepository.deleteAllByPlanId(plan.getId());
        planRepository.delete(plan);
    }
```

- [ ] **Step 4: 跑测试确认通过 + 全量回归**

Run: `export JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ && ./gradlew :backend:test --tests "com.yr.perftest.platform.api.PlanDocumentApiTest" && ./gradlew :backend:test`
Expected: PASS（8 个用例）+ 回归通过（注意：若有旧测试调用 `PUT/DELETE /api/task-plans/{id}` 断言旧语义，改为走新校验断言）。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/yr/perftest/platform backend/src/test/java
git commit -m "feat：P0-1 Task12 REST 面——文档/流转/批注/模板/分享/快捷执行端点与错误码映射、级联删除"
```

---

### Task 13: 前端地基 —— 依赖、类型、plan-doc API、markdown 工具

**Files:**
- Modify: `frontend/package.json`（依赖）
- Modify: `frontend/src/types/index.ts:418`（TaskPlan 扩展 + 新类型）
- Create: `frontend/src/api/plan-doc.ts`
- Create: `frontend/src/utils/plan-markdown.ts`

**Interfaces:**
- Consumes: Task 12 的 REST 合同（路径/形状逐一对应）。
- Produces（Task 14-17 全靠这些名字）：
  - `frontend/src/utils/plan-markdown.ts`：`CANONICAL_HEADINGS`、`splitSections(body): Section[]`、`extractSection(body, title): string | null`、`replaceSection(body, title, content): string`、`parseExecutionRecords(body, scenarioName): string[]`、`parseChecklistItems(content): ChecklistItem[]`（`ChecklistItem = { text: string; auto: boolean; checked: boolean }`）、`parseMarkdownTable(content): Table | null`、`parseScenarioBlocks(body): ScenarioBlock[]`（`{ heading, name, testType, purpose, records }`）、`toggleChecklistItem(content, index): string`
  - `frontend/src/api/plan-doc.ts`：下述全部 `*Api` 函数（命名与后端路径一一对应）
  - types：`PlanPhase/PlanStatus/PlanCommentKind/PlanComment/PlanTemplate/PlanShareTokenView/PlanSnapshotView/PrecheckSettings/PlanPermissions`，`TaskPlan` 追加 `phase/status/body/revision/publishedAt/precheckJson/precheckExecutedAt`，`TaskScenario` 追加 `purpose/testType` 且 `scriptVersionId: number | null`

- [ ] **Step 1: 装依赖**

Run: `cd frontend && npm install md-editor-v3 diff && npm install -D @types/diff`
Expected: package.json 出现 `md-editor-v3`、`diff`、devDep `@types/diff`。

- [ ] **Step 2: types 追加（`frontend/src/types/index.ts`）**

```ts
export type PlanPhase = 'DRAFT' | 'REVIEW' | 'EXECUTION' | 'REPORT' | 'PUBLISH';
export type PlanStatus =
  | 'DRAFT' | 'PENDING' | 'IN_REVIEW' | 'APPROVED'
  | 'RUNNING' | 'DONE' | 'GENERATING' | 'PUBLISHED';
export type PlanCommentKind = 'REVIEW' | 'SYSTEM';

export interface PlanComment {
  id: number;
  planId: number;
  author: string;
  content: string;
  kind: PlanCommentKind;
  createdAt: string;
}

export interface PlanTemplate {
  id: number;
  projectId: number | null;
  name: string;
  description: string | null;
  content: string;
  builtin: boolean;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface PlanShareTokenView {
  id: number;
  planId: number;
  token: string;
  expiresAt: string | null;
  revokedAt: string | null;
  createdBy: string;
  createdAt: string;
}

export interface PlanSnapshotView {
  id: number;
  revision: number;
  publishedBy: string;
  publishedAt: string;
}

export interface PrecheckSettings {
  enabled: boolean;
  items: string[];
}

export type PlanPermissions = Record<string, boolean>;

export interface PlanDocumentResponse {
  plan: TaskPlan;
  permissions: PlanPermissions;
}

export interface PrecheckRunReport {
  ok: boolean;
  failures: string[];
  autoPassed: string[];
}
```

`TaskPlan`（418 行处）追加字段（保留既有字段不动）：

```ts
  phase: PlanPhase;
  status: PlanStatus;
  body: string | null;
  revision: number;
  publishedAt: string | null;
  precheckJson: string | null;
  precheckExecutedAt: string | null;
```

`TaskScenario` 的 `scriptVersionId` 类型改 `number | null`，追加 `purpose: string | null; testType: string | null;`。

- [ ] **Step 3: utils/plan-markdown.ts（完整文件；与后端 PlanMarkdownSupport 同约定）**

```ts
export const CANONICAL_HEADINGS = [
  '一、背景', '二、测试目的与指标', '三、测试范围', '四、测试资源', '五、测试约束',
  '六、测试策略', '七、场景设计', '八、风险与预案', '九、排期与协作', '十、附录', '十一、结论',
];

const EXECUTION_RECORD_HEADING = '#### 执行记录';

export interface Section {
  title: string;
  content: string;
  line: number; // 标题行行号（0 基），TOC 滚动定位用
}

export interface ChecklistItem {
  text: string;
  auto: boolean;
  checked: boolean;
}

export interface MarkdownTable {
  header: string[];
  rows: string[][];
}

export interface ScenarioBlock {
  heading: string;
  name: string;
  testType: string;
  purpose: string;
  records: string[];
}

function canonicalTitleOf(line: string): string | null {
  if (!line.startsWith('## ')) return null;
  const text = line.slice(3).trim();
  const exact = CANONICAL_HEADINGS.find((h) => text === h);
  if (exact) return exact;
  for (const heading of CANONICAL_HEADINGS) {
    const numeral = heading.slice(0, heading.indexOf('、') + 1);
    if (numeral !== '十一、' && text.startsWith(numeral)) return heading;
  }
  return null;
}

export function splitSections(body: string | null | undefined): Section[] {
  if (!body) return [];
  const lines = body.split('\n');
  const sections: Section[] = [];
  let current: Section | null = null;
  lines.forEach((line, index) => {
    const title = canonicalTitleOf(line);
    if (title) {
      if (current) sections.push(current);
      current = { title, content: '', line: index };
    } else if (current) {
      current.content += line + '\n';
    }
  });
  if (current) sections.push(current);
  return sections;
}

export function extractSection(body: string | null | undefined, title: string): string | null {
  return splitSections(body).find((s) => s.title === title)?.content ?? null;
}

export function replaceSection(body: string, title: string, newContent: string): string {
  const lines = body.split('\n');
  let start = -1;
  let end = lines.length;
  for (let i = 0; i < lines.length; i++) {
    if (canonicalTitleOf(lines[i]) === title) {
      start = i + 1;
    } else if (start >= 0 && canonicalTitleOf(lines[i])) {
      end = i;
      break;
    }
  }
  if (start < 0) throw new Error(`章节缺失：${title}`);
  const normalized = newContent.endsWith('\n') || newContent === '' ? newContent : newContent + '\n';
  const before = lines.slice(0, start).join('\n') + '\n';
  const after = lines.slice(end).join('\n');
  return before + normalized + (after === '' ? '' : after);
}

export function parseChecklistItems(content: string | null | undefined): ChecklistItem[] {
  if (!content) return [];
  return content
    .split('\n')
    .filter((line) => /^- \[( |x)\] /.test(line.trim()))
    .map((line) => {
      const trimmed = line.trim();
      const checked = trimmed.startsWith('- [x] ');
      const text = trimmed.slice(6).trim();
      const auto = text.endsWith('（自动）') || text.endsWith('(自动)');
      return { text, auto, checked };
    });
}

export function toggleChecklistItem(content: string, index: number): string {
  let cursor = -1;
  return content
    .split('\n')
    .map((line) => {
      if (/^- \[( |x)\] /.test(line.trim())) {
        cursor += 1;
        if (cursor === index) {
          return line.trim().startsWith('- [x] ') ? line.replace('- [x] ', '- [ ] ') : line.replace('- [ ] ', '- [x] ');
        }
      }
      return line;
    })
    .join('\n');
}

export function parseMarkdownTable(content: string | null | undefined): MarkdownTable | null {
  if (!content) return null;
  const rows = content
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line.startsWith('|') && line.endsWith('|'))
    .map((line) => line.slice(1, -1).split('|').map((cell) => cell.trim()));
  if (rows.length < 2) return null;
  return { header: rows[0], rows: rows.slice(1) };
}

export function parseScenarioBlocks(body: string | null | undefined): ScenarioBlock[] {
  const section = extractSection(body, '七、场景设计');
  if (!section) return [];
  const lines = section.split('\n');
  const blocks: ScenarioBlock[] = [];
  let current: { heading: string; lines: string[] } | null = null;
  for (const line of lines) {
    if (line.startsWith('### ')) {
      if (current) blocks.push(toBlock(current));
      current = { heading: line.slice(4).trim(), lines: [] };
    } else if (current) {
      current.lines.push(line);
    }
  }
  if (current) blocks.push(toBlock(current));
  return blocks;
}

function toBlock(raw: { heading: string; lines: string[] }): ScenarioBlock {
  const parts = raw.heading.split(' · ');
  const body = raw.lines.join('\n');
  const purpose = body.match(/\*\*场景目的\*\*：(.*)/)?.[1]?.trim() ?? '';
  const records = body
    .split('\n')
    .filter((line) => line.trim().startsWith('- ') && !line.trim().startsWith('- ['))
    .map((line) => line.trim().slice(2));
  return {
    heading: raw.heading,
    name: (parts[0] ?? '').replace(/^S\d+\s*/, '').trim(),
    testType: parts[1] ?? '',
    purpose,
    records,
  };
}

export function parseExecutionRecords(body: string | null | undefined, scenarioName: string): string[] {
  return parseScenarioBlocks(body).find((b) => b.name === scenarioName)?.records ?? [];
}
```

- [ ] **Step 4: api/plan-doc.ts（完整文件）**

```ts
import type {
  PlanComment,
  PlanDocumentResponse,
  PlanSnapshotView,
  PlanShareTokenView,
  PlanTemplate,
  PrecheckRunReport,
  PrecheckSettings,
  TaskPlan,
} from '../types';
import { request } from './http';

const json = { 'Content-Type': 'application/json' };

export function getPlanDocumentApi(planId: number) {
  return request<PlanDocumentResponse>(`/api/task-plans/${planId}`);
}

export function updatePlanDocumentApi(planId: number, baseRevision: number, markdown: string) {
  return request<TaskPlan>(`/api/task-plans/${planId}/document`, {
    method: 'PUT',
    headers: json,
    body: JSON.stringify({ baseRevision, markdown }),
  });
}

type TransitionAction =
  | 'submit' | 'start-review' | 'approve' | 'reject' | 'withdraw' | 'back-to-draft'
  | 'start-execution' | 'to-report' | 'generate-report' | 'publish' | 'new-revision';

export function transitionPlanApi(planId: number, action: TransitionAction, payload?: { comment?: string; conclusion?: string }) {
  return request<PlanDocumentResponse>(`/api/task-plans/${planId}/${action}`, {
    method: 'POST',
    headers: json,
    body: JSON.stringify(payload ?? {}),
  });
}

export function precheckRunApi(planId: number) {
  return request<PrecheckRunReport>(`/api/task-plans/${planId}/precheck-run`, { method: 'POST' });
}

export function precheckSkipApi(planId: number) {
  return request<PlanDocumentResponse>(`/api/task-plans/${planId}/precheck-skip`, { method: 'POST' });
}

export function updatePrecheckSettingsApi(planId: number, settings: PrecheckSettings) {
  return request<PlanDocumentResponse>(`/api/task-plans/${planId}/precheck-settings`, {
    method: 'PUT',
    headers: json,
    body: JSON.stringify(settings),
  });
}

export function listCommentsApi(planId: number) {
  return request<PlanComment[]>(`/api/task-plans/${planId}/comments`);
}

export function addCommentApi(planId: number, content: string) {
  return request<PlanComment>(`/api/task-plans/${planId}/comments`, {
    method: 'POST',
    headers: json,
    body: JSON.stringify({ content }),
  });
}

export function deleteCommentApi(planId: number, commentId: number) {
  return request<void>(`/api/task-plans/${planId}/comments/${commentId}`, { method: 'DELETE' });
}

export function listPlanTemplatesApi(projectId: number) {
  return request<PlanTemplate[]>(`/api/projects/${projectId}/plan-templates`);
}

export function createPlanTemplateApi(projectId: number, payload: { name: string; description?: string; content: string }) {
  return request<PlanTemplate>(`/api/projects/${projectId}/plan-templates`, {
    method: 'POST',
    headers: json,
    body: JSON.stringify(payload),
  });
}

export function updatePlanTemplateApi(templateId: number, payload: { name: string; description?: string; content: string }) {
  return request<PlanTemplate>(`/api/plan-templates/${templateId}`, {
    method: 'PUT',
    headers: json,
    body: JSON.stringify(payload),
  });
}

export function deletePlanTemplateApi(templateId: number) {
  return request<void>(`/api/plan-templates/${templateId}`, { method: 'DELETE' });
}

export function listSnapshotsApi(planId: number) {
  return request<PlanSnapshotView[]>(`/api/task-plans/${planId}/snapshots`);
}

export function createShareApi(planId: number, expiresInDays?: number) {
  return request<PlanShareTokenView>(`/api/task-plans/${planId}/shares`, {
    method: 'POST',
    headers: json,
    body: JSON.stringify(expiresInDays ? { expiresInDays } : {}),
  });
}

export function listSharesApi(planId: number) {
  return request<PlanShareTokenView[]>(`/api/task-plans/${planId}/shares`);
}

export function revokeShareApi(planId: number, tokenId: number) {
  return request<void>(`/api/task-plans/${planId}/shares/${tokenId}`, { method: 'DELETE' });
}

export function getSharedPlanApi(token: string) {
  return request<{ name: string; body: string | null; publishedAt: string | null }>(`/api/share/plans/${token}`);
}

export function quickExecuteApi(scriptVersionId: number) {
  return request<{ planId: number; scenarioId: number; executionId: number }>(
    `/api/scripts/${scriptVersionId}/quick-execute`, { method: 'POST' });
}

export function bindScenarioScriptApi(scenarioId: number, scriptVersionId: number) {
  return request<unknown>(`/api/scenarios/${scenarioId}/script`, {
    method: 'POST',
    headers: json,
    body: JSON.stringify({ scriptVersionId }),
  });
}
```

（模板四端点（`GET/POST /api/projects/{projectId}/plan-templates`、`PUT/DELETE /api/plan-templates/{id}`）已在 Task 12 的 `PlanDocumentController` 落地，本任务直接对接。）

- [ ] **Step 5: 验证 + Commit**

Run: `cd frontend && npm run build`
Expected: vue-tsc 0 错误（`TaskScenario.scriptVersionId` 变 nullable 可能暴露既有组件的空值假设——逐个以 `?.`/条件渲染修正，不改语义）。

```bash
git add frontend/package.json frontend/package-lock.json frontend/src/types frontend/src/api/plan-doc.ts frontend/src/utils/plan-markdown.ts backend/src/main/java/com/yr/perftest/platform/api/PlanDocumentController.java
git commit -m "feat：P0-1 Task13 前端地基——md-editor-v3/diff 依赖、plan 域类型与 API、markdown 章节工具"
```

---

### Task 14: 计划详情壳重构 —— 步进条 + 四 Tab + 权限动作 + usePlanDoc

**Files:**
- Create: `frontend/src/composables/usePlanDoc.ts`
- Create: `frontend/src/components/task-plans/PlanPhaseStepper.vue`
- Modify: `frontend/src/components/task-plans/TaskPlanDetail.vue`（重写为壳）
- Modify: `frontend/src/components/task-plans/TaskPlanList.vue:28-37`（向 TaskPlanDetail 传 route 参数即可——现状已传 `plan`/`scenarios`，无需改；确认）

**Interfaces:**
- Consumes: Task 13 全部 API/类型；既有 `useTaskPlans`（scenarios 加载、执行入口）。
- Produces:
  - `usePlanDoc()`：`plan: Ref<TaskPlan | null>`、`permissions: Ref<PlanPermissions>`、`comments`、`load(planId)`、`saveDocument(markdown): Promise<'ok' | 'conflict'>`（conflict 时拉新原文并返回标记）、`transition(action, payload?)`、`refresh()`、导出工具函数 `statusLabel(phase, status): string`
  - `PlanPhaseStepper.vue`：props `{ phase: PlanPhase; status: PlanStatus }`
  - `TaskPlanDetail.vue` 新 props 不变（`plan`、`scenarios`），内部自建 `usePlanDoc` 与四 Tab；emits `back`

- [ ] **Step 1: usePlanDoc.ts（完整文件）**

```ts
import { ref } from 'vue';
import { message } from 'ant-design-vue';
import type { PlanComment, PlanPermissions, TaskPlan } from '../types';
import {
  addCommentApi,
  getPlanDocumentApi,
  listCommentsApi,
  transitionPlanApi,
  updatePlanDocumentApi,
} from '../api/plan-doc';

export function statusLabel(phase: string, status: string): string {
  if (phase === 'REVIEW') return { PENDING: '待评审', IN_REVIEW: '评审中', APPROVED: '评审通过' }[status] ?? status;
  if (phase === 'EXECUTION') return { PENDING: '待执行', RUNNING: '执行中', DONE: '执行完成' }[status] ?? status;
  if (phase === 'REPORT') return { PENDING: '待生成', GENERATING: '生成中', DONE: '已生成' }[status] ?? status;
  if (phase === 'PUBLISH') return '已发布';
  return '草稿';
}

export function usePlanDoc() {
  const plan = ref<TaskPlan | null>(null);
  const permissions = ref<PlanPermissions>({});
  const comments = ref<PlanComment[]>([]);
  const loading = ref(false);

  async function load(planId: number) {
    loading.value = true;
    try {
      const response = await getPlanDocumentApi(planId);
      plan.value = response.plan;
      permissions.value = response.permissions;
      comments.value = await listCommentsApi(planId).catch(() => []);
    } finally {
      loading.value = false;
    }
  }

  async function refresh() {
    if (plan.value) await load(plan.value.id);
  }

  /** 保存整篇原文；409 冲突时拉取服务器版并返回 'conflict'（调用方弹三选一）。 */
  async function saveDocument(markdown: string): Promise<'ok' | 'conflict' | 'error'> {
    if (!plan.value) return 'error';
    try {
      const updated = await updatePlanDocumentApi(plan.value.id, plan.value.revision, markdown);
      plan.value = updated;
      message.success('文档已保存');
      return 'ok';
    } catch (error) {
      const text = error instanceof Error ? error.message : '';
      if (text.includes('PLAN_REVISION_CONFLICT')) {
        await load(plan.value.id); // 冲突体里的 serverMarkdown 也可用；这里直接拉最新全文
        return 'conflict';
      }
      message.error(text || '保存失败');
      return 'error';
    }
  }

  async function transition(
    action: Parameters<typeof transitionPlanApi>[1],
    payload?: { comment?: string; conclusion?: string },
    successText = '操作成功',
  ) {
    if (!plan.value) return false;
    try {
      const response = await transitionPlanApi(plan.value.id, action, payload);
      plan.value = response.plan;
      permissions.value = response.permissions;
      message.success(successText);
      return true;
    } catch (error) {
      message.error(error instanceof Error ? error.message : '操作失败');
      return false;
    }
  }

  async function addComment(content: string) {
    if (!plan.value) return;
    await addCommentApi(plan.value.id, content);
    comments.value = await listCommentsApi(plan.value.id);
  }

  return { plan, permissions, comments, loading, load, refresh, saveDocument, transition, addComment };
}
```

- [ ] **Step 2: PlanPhaseStepper.vue（完整文件）**

```vue
<template>
  <div class="plan-phase-stepper">
    <div v-for="(node, index) in nodes" :key="node.phase" class="stepper-node" :class="nodeClass(index)">
      <span class="stepper-dot">{{ index < currentIndex ? '✓' : index + 1 }}</span>
      <span class="stepper-label">{{ node.label }}</span>
      <span v-if="index === currentIndex" class="stepper-status">{{ statusLabel(phase, status) }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { PlanPhase, PlanStatus } from '../../types';
import { statusLabel } from '../../composables/usePlanDoc';

const props = defineProps<{ phase: PlanPhase; status: PlanStatus }>();

const nodes: { phase: PlanPhase; label: string }[] = [
  { phase: 'DRAFT', label: '草稿' },
  { phase: 'REVIEW', label: '评审' },
  { phase: 'EXECUTION', label: '执行' },
  { phase: 'REPORT', label: '报告' },
  { phase: 'PUBLISH', label: '发布' },
];

const currentIndex = computed(() => nodes.findIndex((n) => n.phase === props.phase));

function nodeClass(index: number) {
  return { done: index < currentIndex.value, active: index === currentIndex.value };
}
</script>

<style scoped>
.plan-phase-stepper {
  display: flex;
  gap: 24px;
  padding: 12px 16px;
  border: 1px solid var(--border);
  border-radius: 8px;
  margin-bottom: 16px;
}
.stepper-node { display: flex; align-items: center; gap: 8px; color: var(--muted); }
.stepper-node.done { color: var(--accent, #0b7f8a); }
.stepper-node.active { color: var(--accent, #0b7f8a); font-weight: 600; }
.stepper-dot {
  width: 22px; height: 22px; border-radius: 50%;
  display: inline-flex; align-items: center; justify-content: center;
  border: 1px solid currentColor; font-size: 12px;
}
.stepper-status { font-size: 12px; color: var(--muted); }
</style>
```

- [ ] **Step 3: TaskPlanDetail.vue 重写（完整文件）+ 四个最小 Tab 桩**

本任务先建四个最小可用的 Tab 组件桩（每个只显示 Tab 名，完整代码在下方），Task 15/16 用完整实现整文件替换它们——保证本任务独立可编译、可手工验收，后续任务有明确的替换目标。

```vue
<template>
  <section class="task-detail">
    <div class="page-head">
      <div>
        <h1>{{ doc.plan?.name ?? plan.name }}</h1>
        <p>
          {{ plan.scenarioCount }} 个场景 · 负责人 {{ plan.createdBy }} ·
          文档 revision {{ doc.plan?.revision ?? plan.revision }}
        </p>
      </div>
      <div class="script-assets-actions">
        <a-button v-if="can('EDIT')" @click="openPlanConfig">编辑默认配置</a-button>
        <a-button v-if="can('SUBMIT')" type="primary" @click="submitForReview">提交评审</a-button>
        <a-button v-if="can('WITHDRAW')" @click="doc.transition('withdraw', undefined, '已撤回')">撤回</a-button>
        <a-button v-if="can('BACK_TO_DRAFT')" @click="doc.transition('back-to-draft', undefined, '已退回草稿')">退回草稿</a-button>
      </div>
    </div>

    <PlanPhaseStepper :phase="doc.plan?.phase ?? 'DRAFT'" :status="doc.plan?.status ?? 'DRAFT'" />

    <a-tabs v-model:active-key="activeTab">
      <a-tab-pane key="document" tab="文档">
        <PlanDetailDocument :doc="doc" :plan="doc.plan ?? plan" :scenarios="scenarios" @changed="doc.refresh" />
      </a-tab-pane>
      <a-tab-pane key="review" tab="评审">
        <PlanDetailReview :doc="doc" />
      </a-tab-pane>
      <a-tab-pane key="report" tab="报告">
        <PlanDetailReport :doc="doc" :scenarios="scenarios" />
      </a-tab-pane>
      <a-tab-pane key="publish" tab="发布">
        <PlanDetailPublish :doc="doc" />
      </a-tab-pane>
    </a-tabs>

    <TaskPlanDialog v-model="planDialogVisible" :editing-plan="plan" />
    <ScenarioDialog v-model="scenarioDialogVisible" :plan="doc.plan ?? plan" :editing-scenario="null" />
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import type { TaskPlan, TaskScenario } from '../../types';
import { usePlanDoc } from '../../composables/usePlanDoc';
import PlanPhaseStepper from './PlanPhaseStepper.vue';
import PlanDetailDocument from './PlanDetailDocument.vue';
import PlanDetailReview from './PlanDetailReview.vue';
import PlanDetailReport from './PlanDetailReport.vue';
import PlanDetailPublish from './PlanDetailPublish.vue';
import TaskPlanDialog from './TaskPlanDialog.vue';
import ScenarioDialog from './ScenarioDialog.vue';

const props = defineProps<{ plan: TaskPlan; scenarios: TaskScenario[] }>();
defineEmits<{ (e: 'back'): void }>();

const doc = usePlanDoc();
const activeTab = ref('document');
const planDialogVisible = ref(false);
const scenarioDialogVisible = ref(false);

onMounted(() => void doc.load(props.plan.id));
watch(() => props.plan.id, (id) => void doc.load(id));

function can(action: string) {
  return Boolean(doc.permissions.value[action]);
}

function openPlanConfig() {
  planDialogVisible.value = true;
}

async function submitForReview() {
  await doc.transition('submit', undefined, '已提交评审');
}
</script>
```

Task 14 同步创建四个**最小桩**（每个文件就这么多，Task 15/16 整文件替换）：

```vue
<template>
  <section class="panel"><p>文档（Task 15 实现）</p></section>
</template>
<script setup lang="ts">
import type { usePlanDoc } from '../../composables/usePlanDoc';
defineProps<{ doc: ReturnType<typeof usePlanDoc>; plan: import('../../types').TaskPlan; scenarios: import('../../types').TaskScenario[] }>();
defineEmits<{ (e: 'changed'): void }>();
</script>
```

```vue
<template>
  <section class="panel"><p>评审（Task 16 实现）</p></section>
</template>
<script setup lang="ts">
import type { usePlanDoc } from '../../composables/usePlanDoc';
defineProps<{ doc: ReturnType<typeof usePlanDoc> }>();
</script>
```

```vue
<template>
  <section class="panel"><p>报告（Task 16 实现）</p></section>
</template>
<script setup lang="ts">
import type { usePlanDoc } from '../../composables/usePlanDoc';
defineProps<{ doc: ReturnType<typeof usePlanDoc>; scenarios: import('../../types').TaskScenario[] }>();
</script>
```

```vue
<template>
  <section class="panel"><p>发布（Task 16 实现）</p></section>
</template>
<script setup lang="ts">
import type { usePlanDoc } from '../../composables/usePlanDoc';
defineProps<{ doc: ReturnType<typeof usePlanDoc> }>();
</script>
```

- [ ] **Step 4: 验证 + Commit**

Run: `cd frontend && npm run build`
Expected: 0 错误。手工：`npm run dev` 打开任一计划详情，可见步进条 + 四 Tab（Tab 内容为桩文案）、权限动作条仅草稿显示"提交评审/编辑默认配置"。

```bash
git add frontend/src
git commit -m "feat：P0-1 Task14 计划详情壳——五阶段步进条、四 Tab、usePlanDoc 状态与冲突感知保存"
```

---

### Task 15: 文档 Tab —— Pretty|Markdown 分段控件、TOC、编辑与冲突三选一、执行设置抽屉

**Files:**
- Modify: `frontend/src/components/task-plans/PlanDetailDocument.vue`（替换 Task 14 桩）
- Create: `frontend/src/components/task-plans/PlanConflictDialog.vue`
- Create: `frontend/src/components/task-plans/PlanSectionEditor.vue`（Pretty 受约束章节的章节级编辑弹窗——合同即"章节级替换"）

**Interfaces:**
- Consumes: Task 13 `plan-markdown.ts`、`plan-doc.ts`；Task 14 `usePlanDoc`。
- Produces:
  - `PlanDetailDocument.vue` props `{ doc: ReturnType<typeof usePlanDoc>; plan: TaskPlan; scenarios: TaskScenario[] }`，emits `changed`
  - `PlanConflictDialog.vue`：props `{ open: boolean; serverMarkdown: string; localMarkdown: string }`，emits `update:open`、`resolve(kind: 'keep-server' | 'take-local' | 'manual')`
  - `PlanSectionEditor.vue`：props `{ open: boolean; title: string; content: string }`，emits `update:open`、`save(content: string)`

- [ ] **Step 1: PlanConflictDialog.vue（完整文件）**

```vue
<template>
  <a-modal
    :open="open"
    title="文档冲突：平台版本已被他人修改"
    width="960px"
    :footer="null"
    @cancel="$emit('update:open', false)"
  >
    <p class="conflict-hint">当前 revision 与服务器不一致。请选择处理方式：</p>
    <div class="conflict-columns">
      <div class="conflict-col">
        <h4>平台当前版</h4>
        <div class="conflict-diff">
          <div v-for="(part, index) in diffParts" :key="index" class="diff-line" :class="part.kind">
            {{ part.text || ' ' }}
          </div>
        </div>
      </div>
      <div class="conflict-col">
        <h4>本地版</h4>
        <div class="conflict-text"><pre>{{ localMarkdown }}</pre></div>
      </div>
    </div>
    <div class="conflict-actions">
      <a-button @click="$emit('resolve', 'keep-server')">保留平台版</a-button>
      <a-button @click="$emit('resolve', 'take-local')">采纳本地版</a-button>
      <a-button type="primary" @click="$emit('resolve', 'manual')">手改（以平台版为基底）</a-button>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { diffLines } from 'diff';

const props = defineProps<{ open: boolean; serverMarkdown: string; localMarkdown: string }>();
defineEmits<{ (e: 'update:open', value: boolean): void; (e: 'resolve', kind: 'keep-server' | 'take-local' | 'manual'): void }>();

const diffParts = computed(() =>
  diffLines(props.localMarkdown, props.serverMarkdown).flatMap((part) =>
    part.value
      .replace(/\n$/, '')
      .split('\n')
      .filter((line) => line.trim().length > 0)
      .map((text) => ({ text, kind: part.added ? 'added' : part.removed ? 'removed' : 'same' })),
  ),
);
</script>

<style scoped>
.conflict-hint { color: var(--muted); }
.conflict-columns { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.conflict-col h4 { margin: 0 0 8px; }
.conflict-diff, .conflict-text { height: 360px; overflow: auto; border: 1px solid var(--border); border-radius: 6px; }
.diff-line { font-family: var(--font-data, monospace); font-size: 12px; padding: 0 8px; white-space: pre-wrap; }
.diff-line.added { background: rgba(47, 155, 106, 0.12); }
.diff-line.removed { background: rgba(209, 67, 67, 0.12); }
.conflict-text pre { margin: 0; padding: 8px; font-size: 12px; white-space: pre-wrap; }
.conflict-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 12px; }
</style>
```

- [ ] **Step 2: PlanSectionEditor.vue（完整文件）**

```vue
<template>
  <a-modal
    :open="open"
    :title="`编辑章节：${title}`"
    width="860px"
    ok-text="保存章节"
    :confirm-loading="saving"
    @ok="handleOk"
    @cancel="$emit('update:open', false)"
  >
    <MdEditor v-model="draft" :style="{ height: '420px' }" language="zh-CN" />
  </a-modal>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import MdEditor from 'md-editor-v3';
import 'md-editor-v3/lib/style.css';

const props = defineProps<{ open: boolean; title: string; content: string }>();
const emit = defineEmits<{ (e: 'update:open', value: boolean): void; (e: 'save', content: string): void }>();

const draft = ref('');
const saving = ref(false);

watch(() => props.open, (open) => {
  if (open) draft.value = props.content;
});

function handleOk() {
  saving.value = true;
  emit('save', draft.value);
  saving.value = false;
  emit('update:open', false);
}
</script>
```

- [ ] **Step 3: PlanDetailDocument.vue（替换桩；完整文件）**

```vue
<template>
  <section class="plan-document">
    <div class="doc-toolbar">
      <a-segmented v-model:value="viewMode" :options="['Pretty', 'Markdown']" />
      <div class="doc-toolbar-right">
        <a-button v-if="viewMode === 'Markdown' && canEdit" type="primary" @click="startEdit">
          {{ editing ? '保存' : '编辑' }}
        </a-button>
        <a-button v-if="viewMode === 'Markdown' && editing" @click="cancelEdit">取消</a-button>
        <a-button @click="precheckDrawerOpen = true">执行设置（环境检查）</a-button>
      </div>
    </div>

    <div class="doc-body">
      <aside class="doc-toc">
        <h4>章节导航</h4>
        <a
          v-for="section in sections"
          :key="section.title"
          class="toc-item"
          :class="{ constrained: CONSTRAINED.includes(section.title) }"
          @click="scrollTo(section.line)"
        >{{ section.title }}</a>
      </aside>

      <div class="doc-main">
        <template v-if="viewMode === 'Pretty'">
          <div v-for="section in prettySections" :key="section.title" class="panel pretty-section" :data-section="section.title">
            <div class="pretty-section-head">
              <h3>{{ section.title }}</h3>
              <a-button v-if="canEdit" size="small" @click="openSectionEditor(section.title)">编辑章节</a-button>
            </div>
            <ChecklistView
              v-if="section.title === '五、测试约束'"
              :content="section.content"
              :editable="canEdit"
              @toggle="toggleChecklist(section.content, $event)"
            />
            <ScenarioDesignModule
              v-else-if="section.title === '七、场景设计'"
              :doc-plan="doc"
              :plan="plan"
              :scenarios="scenarios"
              @changed="emit('changed')"
            />
            <MdPreview v-else :model-value="section.content || '（空）'" language="zh-CN" />
          </div>
          <p class="pretty-hint">叙述章节（背景/策略/风险/附录/结论）请切换到 Markdown 视图查看。</p>
        </template>

        <template v-else>
          <MdPreview v-if="!editing" :model-value="plan.body ?? ''" language="zh-CN" />
          <MdEditor v-else v-model="editDraft" :style="{ height: '560px' }" language="zh-CN" />
        </template>
      </div>
    </div>

    <PlanSectionEditor
      v-model:open="sectionEditorOpen"
      :title="editingSectionTitle"
      :content="editingSectionContent"
      @save="saveSection"
    />
    <PlanConflictDialog
      v-model:open="conflictOpen"
      :server-markdown="plan.body ?? ''"
      :local-markdown="conflictLocal"
      @resolve="resolveConflict"
    />

    <a-drawer v-model:open="precheckDrawerOpen" title="执行设置（环境检查）" width="420">
      <p class="drawer-hint">环境检查是测试前的执行动作，不进文档、不参与评审。</p>
      <a-form layout="vertical">
        <a-form-item label="首执行前自动运行环境检查">
          <a-switch v-model:checked="precheck.enabled" :disabled="!canPrecheck" @change="savePrecheck" />
        </a-form-item>
        <a-form-item label="检测清单（每行一项；自动项：指标已定义/场景已配置/脚本已关联）">
          <a-textarea v-model:value="precheckItemsText" :rows="8" :disabled="!canPrecheck" @blur="savePrecheck" />
        </a-form-item>
        <a-form-item v-if="plan.precheckExecutedAt" label="首次运行时间">
          <span>{{ new Date(plan.precheckExecutedAt).toLocaleString() }}</span>
        </a-form-item>
      </a-form>
    </a-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import MdEditor from 'md-editor-v3';
import { MdPreview } from 'md-editor-v3';
import 'md-editor-v3/lib/style.css';
import type { TaskPlan, TaskScenario } from '../../types';
import type { usePlanDoc } from '../../composables/usePlanDoc';
import { extractSection, replaceSection, splitSections, toggleChecklistItem } from '../../utils/plan-markdown';
import { updatePrecheckSettingsApi } from '../../api/plan-doc';
import PlanConflictDialog from './PlanConflictDialog.vue';
import PlanSectionEditor from './PlanSectionEditor.vue';
import ChecklistView from './ChecklistView.vue';
import ScenarioDesignModule from './ScenarioDesignModule.vue';

const props = defineProps<{ doc: ReturnType<typeof usePlanDoc>; plan: TaskPlan; scenarios: TaskScenario[] }>();
const emit = defineEmits<{ (e: 'changed'): void }>();

const CONSTRAINED = ['二、测试目的与指标', '三、测试范围', '四、测试资源', '五、测试约束', '七、场景设计', '九、排期与协作'];

const viewMode = ref<'Pretty' | 'Markdown'>('Pretty');
const editing = ref(false);
const editDraft = ref('');
const conflictLocal = ref('');
const conflictOpen = ref(false);
const sectionEditorOpen = ref(false);
const editingSectionTitle = ref('');
const editingSectionContent = ref('');
const precheckDrawerOpen = ref(false);
const precheck = ref<{ enabled: boolean; items: string[] }>({ enabled: false, items: [] });
const precheckItemsText = ref('');

const sections = computed(() => splitSections(props.plan.body));
const prettySections = computed(() => sections.value.filter((s) => CONSTRAINED.includes(s.title)));
const canEdit = computed(() => Boolean(props.doc.permissions.value.EDIT));
const canPrecheck = computed(() => Boolean(props.doc.permissions.value.PRECHECK_RUN));

watch(() => props.plan.precheckJson, parsePrecheck, { immediate: true });

function parsePrecheck() {
  try {
    const parsed = props.plan.precheckJson ? JSON.parse(props.plan.precheckJson) : { enabled: false, items: [] };
    precheck.value = { enabled: Boolean(parsed.enabled), items: parsed.items ?? [] };
    precheckItemsText.value = precheck.value.items.join('\n');
  } catch {
    precheck.value = { enabled: false, items: [] };
  }
}

async function savePrecheck() {
  const items = precheckItemsText.value.split('\n').map((line) => line.trim()).filter(Boolean);
  precheck.value.items = items;
  try {
    await updatePrecheckSettingsApi(props.plan.id, { enabled: precheck.value.enabled, items });
    message.success('执行设置已保存（不影响文档 revision）');
    emit('changed');
  } catch (error) {
    message.error(error instanceof Error ? error.message : '保存失败');
  }
}

function startEdit() {
  if (editing.value) {
    void submitWholeDocument(editDraft.value);
  } else {
    editDraft.value = props.plan.body ?? '';
    editing.value = true;
  }
}

function cancelEdit() {
  editing.value = false;
  editDraft.value = '';
}

async function submitWholeDocument(markdown: string): Promise<void> {
  const outcome = await props.doc.saveDocument(markdown);
  if (outcome === 'ok') {
    editing.value = false;
    emit('changed');
  } else if (outcome === 'conflict') {
    conflictLocal.value = markdown;
    conflictOpen.value = true;
  }
}

async function resolveConflict(kind: 'keep-server' | 'take-local' | 'manual') {
  conflictOpen.value = false;
  if (kind === 'keep-server') {
    editing.value = false;
    message.info('已保留平台版本');
  } else if (kind === 'take-local') {
    await props.doc.saveDocument(conflictLocal.value); // doc.plan 已刷新，baseRevision 为新值
    editing.value = false;
    emit('changed');
  } else {
    editDraft.value = props.plan.body ?? ''; // 以服务器版为基底手改
    editing.value = true;
    viewMode.value = 'Markdown';
  }
}

function openSectionEditor(title: string) {
  editingSectionTitle.value = title;
  editingSectionContent.value = extractSection(props.plan.body, title) ?? '';
  sectionEditorOpen.value = true;
}

async function saveSection(content: string) {
  const body = props.plan.body ?? '';
  try {
    const next = replaceSection(body, editingSectionTitle.value, content);
    await submitWholeDocument(next);
  } catch (error) {
    message.error(error instanceof Error ? error.message : '章节写回失败');
  }
}

async function toggleChecklist(content: string, index: number) {
  const next = toggleChecklistItem(content, index);
  const body = replaceSection(props.plan.body ?? '', '五、测试约束', next);
  await submitWholeDocument(body);
}

function scrollTo(line: number) {
  const all = splitSections(props.plan.body);
  const target = all.find((s) => s.line === line);
  if (!target) return;
  const el = viewMode.value === 'Pretty'
    ? document.querySelector(`[data-section="${target.title}"]`)
    : null;
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
}
</script>

<style scoped>
.plan-document { display: flex; flex-direction: column; gap: 12px; }
.doc-toolbar { display: flex; justify-content: space-between; align-items: center; }
.doc-body { display: grid; grid-template-columns: 180px 1fr; gap: 16px; }
.doc-toc { border-right: 1px solid var(--border); padding-right: 8px; }
.doc-toc h4 { margin: 4px 0 8px; font-size: 12px; color: var(--muted); }
.toc-item { display: block; padding: 4px 6px; font-size: 13px; color: var(--muted); cursor: pointer; border-radius: 4px; text-decoration: none; }
.toc-item:hover { background: var(--canvas, #f4f6f8); }
.toc-item.constrained { color: var(--ink, inherit); font-weight: 500; }
.pretty-section { margin-bottom: 12px; }
.pretty-section-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.pretty-hint { color: var(--muted); font-size: 12px; }
</style>
```

（`resolveConflict` 的 'take-local' 分支：`doc.saveDocument` 依赖 `doc.plan` 已在冲突时 `load` 刷新——`saveDocument` 返回 'conflict' 只会在再次冲突时发生，此时弹窗内容仍是旧 `conflictLocal`，可接受，用户可再次选择。）

本任务还需 **Create** `frontend/src/components/task-plans/ChecklistView.vue`（完整文件；Task 16 的场景模块复用不了它，独立小组件）：

```vue
<template>
  <div class="checklist">
    <div v-for="(item, index) in items" :key="index" class="checklist-item">
      <a-checkbox
        :checked="item.checked"
        :disabled="!editable"
        @change="$emit('toggle', index)"
      >
        {{ item.text }}
        <a-tag v-if="item.auto" color="cyan" class="checklist-tag">自动核验</a-tag>
      </a-checkbox>
    </div>
    <p v-if="items.length === 0" class="checklist-empty">（空清单）</p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { parseChecklistItems } from '../../utils/plan-markdown';

const props = defineProps<{ content: string; editable: boolean }>();
defineEmits<{ (e: 'toggle', index: number): void }>();

const items = computed(() => parseChecklistItems(props.content));
</script>

<style scoped>
.checklist-item { padding: 2px 0; }
.checklist-tag { margin-left: 6px; }
.checklist-empty { color: var(--muted); }
</style>
```

`ScenarioDesignModule` 本任务先建**同款最小桩**（与 Task 14 桩一致：props `{ docPlan, plan, scenarios }` + emit `changed`），Task 16 替换为完整实现——保证本任务可编译可手工验收（Pretty 场景章节先渲染 `MdPreview` 桩版本）。

- [ ] **Step 4: 验证 + Commit**

Run: `cd frontend && npm run build`
Expected: 0 错误。手工验收（`npm run dev`）：
1. 计划详情 → 文档 Tab：默认 Pretty，仅显示 6 个受约束章节卡；切换 Markdown 显示全部 11 章节渲染。
2. 草稿态点「编辑」→ 编辑器出现 → 改一行保存 → revision+1；再以旧内容制造冲突（开两个标签页各改一次）→ 第二个保存弹三选一，三个按钮行为符合 §5.3。
3. 测试约束章节勾选一项 → 保存后 Markdown 视图对应行变 `[x]`。
4. 执行设置抽屉：开关/清单保存后提示"不影响文档 revision"。

```bash
git add frontend/src
git commit -m "feat：P0-1 Task15 文档 Tab——Pretty|Markdown 分段控件、TOC、章节级写回、冲突三选一、执行设置抽屉"
```

---

### Task 16: 评审/报告/发布 Tab 与场景设计模块

**Files:**
- Modify: `frontend/src/components/task-plans/PlanDetailReview.vue`（替换桩）
- Modify: `frontend/src/components/task-plans/PlanDetailReport.vue`（替换桩）
- Modify: `frontend/src/components/task-plans/PlanDetailPublish.vue`（替换桩）
- Modify: `frontend/src/components/task-plans/ScenarioDesignModule.vue`（替换桩）

**Interfaces:**
- Consumes: Task 13-15 全部；既有 `api/task-plans.ts` 的 `listExecutionsApi`（场景最新执行跳转沿用 `useTaskPlans().openExecution` 由父层传入？——不引入跨层回调，模块内直接 `useRouter().push`）。
- Produces: 四个完整组件（props/emits 与桩一致）。

- [ ] **Step 1: PlanDetailReview.vue（完整文件）**

```vue
<template>
  <section class="panel review-tab">
    <div class="review-actions">
      <a-button v-if="can('START_REVIEW')" type="primary" @click="run('start-review', '已开始评审')">开始评审</a-button>
      <a-button v-if="can('APPROVE')" type="primary" @click="approve">评审通过</a-button>
      <a-button v-if="can('REJECT')" danger @click="reject">驳回</a-button>
      <span v-if="!can('COMMENT') && !can('APPROVE')" class="review-hint">当前阶段批注只读</span>
    </div>

    <div class="review-comment-input" v-if="can('COMMENT')">
      <a-textarea v-model:value="draft" :rows="2" placeholder="添加批注（全文档级）" />
      <a-button type="primary" :disabled="!draft.trim()" @click="submitComment">发批注</a-button>
    </div>

    <a-timeline class="review-timeline">
      <a-timeline-item v-for="comment in doc.comments.value" :key="comment.id" :color="comment.kind === 'SYSTEM' ? 'gray' : 'blue'">
        <div class="comment-head">
          <strong>{{ comment.author }}</strong>
          <a-tag v-if="comment.kind === 'SYSTEM'">系统</a-tag>
          <span class="comment-time">{{ new Date(comment.createdAt).toLocaleString() }}</span>
          <a-button
            v-if="comment.kind === 'REVIEW' && canDelete(comment)"
            type="link" size="small" danger
            @click="remove(comment)"
          >删除</a-button>
        </div>
        <p class="comment-body">{{ comment.content }}</p>
      </a-timeline-item>
    </a-timeline>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { message } from 'ant-design-vue';
import type { PlanComment } from '../../types';
import { useAuth } from '../../composables/useAuth';
import { deleteCommentApi } from '../../api/plan-doc';
import type { usePlanDoc } from '../../composables/usePlanDoc';

const props = defineProps<{ doc: ReturnType<typeof usePlanDoc> }>();

const draft = ref('');
const { currentUser } = useAuth();

function can(action: string) {
  return Boolean(props.doc.permissions.value[action]);
}

function canDelete(comment: PlanComment) {
  return can('DELETE') || comment.author === currentUser.value?.username;
}

async function run(action: 'start-review' | 'withdraw', text: string) {
  await props.doc.transition(action, undefined, text);
}

async function approve() {
  await props.doc.transition('approve', undefined, '评审已通过');
}

async function reject() {
  const comment = window.prompt('驳回原因（必填，将作为批注留存）');
  if (!comment?.trim()) return;
  await props.doc.transition('reject', { comment }, '已驳回，退回草稿');
}

async function submitComment() {
  await props.doc.addComment(draft.value.trim());
  draft.value = '';
}

async function remove(comment: PlanComment) {
  if (!props.doc.plan.value) return;
  await deleteCommentApi(props.doc.plan.value.id, comment.id);
  await props.doc.refresh();
  message.success('批注已删除');
}
</script>

<style scoped>
.review-actions { display: flex; gap: 8px; margin-bottom: 12px; }
.review-hint { color: var(--muted); align-self: center; }
.review-comment-input { display: flex; gap: 8px; align-items: flex-start; margin-bottom: 16px; }
.comment-head { display: flex; gap: 8px; align-items: center; }
.comment-time { color: var(--muted); font-size: 12px; }
.comment-body { margin: 4px 0 0; }
</style>
```

- [ ] **Step 2: PlanDetailReport.vue（完整文件）**

```vue
<template>
  <section class="panel report-tab">
    <div class="report-actions">
      <a-button v-if="can('TO_REPORT')" type="primary" @click="doc.transition('to-report', undefined, '已进入报告阶段')">进入报告</a-button>
      <a-button v-if="can('GENERATE_REPORT')" type="primary" :loading="generating" @click="generate">生成报告</a-button>
      <span class="report-hint">生成 = 聚合执行摘要回填"十一、结论"（达成表实际列 + 结果总览）。</span>
    </div>

    <h3>结论章节预览</h3>
    <MdPreview :model-value="conclusion ?? '（暂无结论章节）'" language="zh-CN" />

    <h3>场景执行概览</h3>
    <a-table
      :columns="columns"
      :data-source="rows"
      :pagination="false"
      row-key="name"
      size="small"
      :locale="{ emptyText: '暂无场景' }"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { MdPreview } from 'md-editor-v3';
import 'md-editor-v3/lib/style.css';
import type { TaskScenario } from '../../types';
import { extractSection, parseExecutionRecords } from '../../utils/plan-markdown';
import type { usePlanDoc } from '../../composables/usePlanDoc';

const props = defineProps<{ doc: ReturnType<typeof usePlanDoc>; scenarios: TaskScenario[] }>();

const generating = ref(false);
const columns = [
  { title: '场景', dataIndex: 'name', key: 'name' },
  { title: '测试类型', dataIndex: 'testType', key: 'testType' },
  { title: '脚本', dataIndex: 'script', key: 'script' },
  { title: '最新执行（回填块解析）', dataIndex: 'latest', key: 'latest' },
];

const conclusion = computed(() => extractSection(props.doc.plan.value?.body, '十一、结论'));
const rows = computed(() =>
  props.scenarios.map((scenario) => {
    const records = parseExecutionRecords(props.doc.plan.value?.body, scenario.name);
    return {
      name: scenario.name,
      testType: scenario.testType ?? '—',
      script: scenario.scriptVersionId ? `#${scenario.scriptVersionId}` : '未关联',
      latest: records[0] ?? '未执行',
    };
  }),
);

function can(action: string) {
  return Boolean(props.doc.permissions.value[action]);
}

async function generate() {
  generating.value = true;
  try {
    await props.doc.transition('generate-report', undefined, '报告已生成');
  } finally {
    generating.value = false;
  }
}
</script>
```


- [ ] **Step 3: PlanDetailPublish.vue（完整文件）**

```vue
<template>
  <section class="panel publish-tab">
    <div v-if="can('PUBLISH')" class="publish-form">
      <h3>发布</h3>
      <p class="publish-hint">前置：报告已生成、无活跃执行；发布将冻结文档并固化快照。</p>
      <a-textarea v-model:value="conclusion" :rows="3" placeholder="总体结论（发布人确认，必填）" />
      <a-button type="primary" :disabled="!conclusion.trim()" @click="publish">发布</a-button>
    </div>
    <a-alert v-else-if="doc.plan.value?.phase === 'PUBLISH'" type="success" show-icon message="该计划已发布（终态）。变更请发起修订。" />

    <div v-if="can('NEW_REVISION')" class="publish-revision">
      <a-button @click="doc.transition('new-revision', undefined, '已发起新修订')">发起新修订</a-button>
    </div>

    <h3>发布快照</h3>
    <a-table :columns="snapshotColumns" :data-source="snapshots" :pagination="false" row-key="id" size="small" :locale="{ emptyText: '暂无快照' }">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'publishedAt'">
          {{ new Date(record.publishedAt).toLocaleString() }}
        </template>
      </template>
    </a-table>

    <h3>只读分享链接</h3>
    <div class="share-actions" v-if="can('SHARE')">
      <a-button @click="createShare">创建分享链接（默认 30 天）</a-button>
    </div>
    <a-table :columns="shareColumns" :data-source="shares" :pagination="false" row-key="id" size="small" :locale="{ emptyText: '暂无分享' }">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'url'">
          <code>{{ shareUrl(record.token) }}</code>
        </template>
        <template v-else-if="column.key === 'expiresAt'">
          {{ record.expiresAt ? new Date(record.expiresAt).toLocaleString() : '永久' }}
        </template>
        <template v-else-if="column.key === 'state'">
          {{ shareState(record) }}
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-button v-if="!record.revokedAt" type="link" danger size="small" @click="revoke(record)">撤销</a-button>
        </template>
      </template>
    </a-table>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { message } from 'ant-design-vue';
import { createShareApi, listSharesApi, listSnapshotsApi, revokeShareApi } from '../../api/plan-doc';
import type { PlanShareTokenView, PlanSnapshotView } from '../../types';
import type { usePlanDoc } from '../../composables/usePlanDoc';

const props = defineProps<{ doc: ReturnType<typeof usePlanDoc> }>();

const conclusion = ref('');
const snapshots = ref<PlanSnapshotView[]>([]);
const shares = ref<PlanShareTokenView[]>([]);

const snapshotColumns = [
  { title: 'revision', dataIndex: 'revision', key: 'revision' },
  { title: '发布人', dataIndex: 'publishedBy', key: 'publishedBy' },
  { title: '发布时间', key: 'publishedAt' },
];
const shareColumns = [
  { title: '链接', key: 'url' },
  { title: '过期时间', key: 'expiresAt' },
  { title: '状态', key: 'state' },
  { title: '操作', key: 'actions' },
];

onMounted(() => void reload());

function can(action: string) {
  return Boolean(props.doc.permissions.value[action]);
}

async function reload() {
  const planId = props.doc.plan.value?.id;
  if (!planId) return;
  snapshots.value = await listSnapshotsApi(planId).catch(() => []);
  shares.value = await listSharesApi(planId).catch(() => []);
}

async function publish() {
  const ok = await props.doc.transition('publish', { conclusion: conclusion.value.trim() }, '已发布');
  if (ok) await reload();
}

async function createShare() {
  const planId = props.doc.plan.value?.id;
  if (!planId) return;
  await createShareApi(planId);
  await reload();
  message.success('分享链接已创建');
}

async function revoke(record: PlanShareTokenView) {
  const planId = props.doc.plan.value?.id;
  if (!planId) return;
  await revokeShareApi(planId, record.id);
  await reload();
  message.success('已撤销');
}

function shareUrl(token: string) {
  return `${window.location.origin}/share/plans/${token}`;
}

function shareState(record: PlanShareTokenView) {
  if (record.revokedAt) return '已撤销';
  if (record.expiresAt && new Date(record.expiresAt) < new Date()) return '已过期';
  return '有效';
}
</script>

<style scoped>
.publish-form { display: flex; flex-direction: column; gap: 8px; max-width: 520px; margin-bottom: 16px; }
.publish-hint { color: var(--muted); }
</style>
```


- [ ] **Step 4: ScenarioDesignModule.vue（完整文件；Pretty 的场景设计模块）**

```vue
<template>
  <div class="scenario-module">
    <div class="scenario-module-head">
      <span>场景 = 文档章节（业务内容）+ 执行配置实体；脚本不进文档，评审通过后关联。</span>
      <a-button v-if="canEditScenario" type="primary" size="small" @click="emit('request-add')">添加场景</a-button>
    </div>

    <div v-for="block in blocks" :key="block.heading" class="scenario-card">
      <div class="scenario-card-head">
        <strong>{{ block.heading }}</strong>
        <span v-if="latestRecord(block.name)" class="scenario-latest" :title="latestRecord(block.name)">
          最新执行：{{ latestRecord(block.name) }}
        </span>
        <span v-else class="scenario-latest none">未执行</span>
      </div>
      <p class="scenario-purpose">目的：{{ block.purpose || '（待填写）' }}</p>
      <div class="scenario-actions">
        <a-button size="small" @click="emit('request-edit', scenarioOf(block.name))" :disabled="!scenarioOf(block.name)">编辑</a-button>
        <a-button
          v-if="!scriptBound(block.name) && canBindScript"
          size="small" type="primary"
          @click="bindScript(block.name)"
        >关联脚本</a-button>
        <a-button
          v-if="scriptBound(block.name) && canExecute"
          size="small" type="primary"
          @click="run(block.name)"
        >执行</a-button>
      </div>
      <details v-if="block.records.length" class="scenario-records">
        <summary>执行记录（{{ block.records.length }}）</summary>
        <ul><li v-for="(record, index) in block.records" :key="index">{{ record }}</li></ul>
      </details>
    </div>
    <p v-if="blocks.length === 0" class="scenario-empty">暂无场景。在评审前添加，或在评审通过后编写脚本并关联。</p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { message } from 'ant-design-vue';
import { useRouter } from 'vue-router';
import type { TaskPlan, TaskScenario } from '../../types';
import type { usePlanDoc } from '../../composables/usePlanDoc';
import { parseScenarioBlocks } from '../../utils/plan-markdown';
import { bindScenarioScriptApi, precheckSkipApi } from '../../api/plan-doc';
import { triggerExecutionApi } from '../../api/task-plans';

const props = defineProps<{ docPlan: ReturnType<typeof usePlanDoc>; plan: TaskPlan; scenarios: TaskScenario[] }>();
const emit = defineEmits<{ (e: 'changed'): void; (e: 'request-add'): void; (e: 'request-edit', scenario: TaskScenario): void }>();

const router = useRouter();
const blocks = computed(() => parseScenarioBlocks(props.plan.body));

const canEditScenario = computed(() => {
  const phase = props.docPlan.plan.value?.phase;
  const status = props.docPlan.plan.value?.status;
  return (phase === 'DRAFT' || phase === 'REVIEW' || (phase === 'EXECUTION' && status !== 'RUNNING') || phase === 'REPORT')
    && phase !== 'PUBLISH';
});
const canBindScript = computed(() => canEditScenario.value);
const canExecute = computed(() => {
  const phase = props.docPlan.plan.value?.phase;
  return phase === 'EXECUTION' || phase === 'REPORT';
});

function scenarioOf(name: string) {
  return props.scenarios.find((s) => s.name === name) ?? null;
}

function scriptBound(name: string) {
  return Boolean(scenarioOf(name)?.scriptVersionId);
}

function latestRecord(name: string) {
  const records = parseScenarioBlocks(props.plan.body).find((b) => b.name === name)?.records ?? [];
  return records[0] ?? '';
}

async function bindScript(name: string) {
  const scenario = scenarioOf(name);
  if (!scenario) return;
  const input = window.prompt(`关联脚本版本 ID（场景：${name}）`);
  const scriptVersionId = Number(input);
  if (!input || Number.isNaN(scriptVersionId) || scriptVersionId <= 0) return;
  try {
    await bindScenarioScriptApi(scenario.id, scriptVersionId);
    message.success('脚本已关联');
    emit('changed');
  } catch (error) {
    message.error(error instanceof Error ? error.message : '关联失败');
  }
}

async function run(name: string) {
  const scenario = scenarioOf(name);
  if (!scenario) return;
  try {
    const execution = await triggerExecutionApi(scenario.id, { idempotencyKey: `ui-${Date.now()}` });
    await router.push(`/projects/${props.plan.projectId}/executions/${execution.id}`);
  } catch (error) {
    const text = error instanceof Error ? error.message : '';
    if (text.includes('PLAN_PRECHECK_FAILED')) {
      if (window.confirm(`${text}\n\n是否跳过环境检查继续执行？（将记录系统批注）`)) {
        await precheckSkipApi(props.plan.id);
        await run(name);
      }
      return;
    }
    message.error(text || '执行失败');
  }
}
</script>

<style scoped>
.scenario-module-head { display: flex; justify-content: space-between; align-items: center; color: var(--muted); margin-bottom: 8px; }
.scenario-card { border: 1px solid var(--border); border-radius: 8px; padding: 12px; margin-bottom: 8px; }
.scenario-card-head { display: flex; gap: 12px; align-items: baseline; }
.scenario-latest { font-size: 12px; color: var(--muted); }
.scenario-latest.none { color: var(--muted); opacity: 0.7; }
.scenario-purpose { margin: 6px 0; color: var(--muted); }
.scenario-actions { display: flex; gap: 8px; margin: 6px 0; }
.scenario-records summary { cursor: pointer; font-size: 13px; }
.scenario-empty { color: var(--muted); }
</style>
```

（`window.prompt` 关联脚本是最小可用版——执行时若 `useWorkspace().currentProjectScripts` 可用，替换为下拉选择脚本资产，`scriptVersionId` 取脚本最新版本；不改端点合同。）

**接线**：`PlanDetailDocument.vue` 中 `<ScenarioDesignModule>` 的使用处补两个事件透传并向上冒泡到 `TaskPlanDetail.vue` 打开 `ScenarioDialog`（`@request-add="scenarioDialogVisible = true"`）。实现方式：`PlanDetailDocument` 声明 emits `request-add`/`request-edit` 并在模板透传；`TaskPlanDetail.vue` 模板上补 `@request-add="scenarioDialogVisible = true"` `@request-edit="openEditScenario"`（后者需在 Task 14 壳里补 `editingScenario` 状态与 `openEditScenario(scenario)` 方法，引用既有 `ScenarioDialog` props 契约）。

- [ ] **Step 5: 验证 + Commit**

Run: `cd frontend && npm run build`
Expected: 0 错误。手工：评审 Tab 全流程（开始评审→批注→通过/驳回）；报告 Tab 生成后结论章节出现"执行结果总览"；发布 Tab 发布→快照出现→分享链接可打开；场景模块在 EXECUTION 阶段显示"关联脚本/执行"，precheck 失败弹 confirm 可跳过重试。

```bash
git add frontend/src
git commit -m "feat：P0-1 Task16 评审/报告/发布 Tab 与场景设计模块——批注时间线、结论回填预览、发布快照分享、业务化场景卡"
```

---

### Task 17: 列表/弹窗/分享页/快捷执行改造

**Files:**
- Modify: `frontend/src/components/task-plans/TaskPlanList.vue`（阶段/子状态 badge 列）
- Modify: `frontend/src/components/task-plans/TaskPlanDialog.vue`（创建时模板选择）
- Modify: `frontend/src/components/task-plans/ScenarioDialog.vue`（测试类型/场景目的/脚本可选）
- Create: `frontend/src/views/SharePlanPage.vue`
- Modify: `frontend/src/router/index.ts`（`/share/plans/:token` + 守卫放行）
- Modify: `frontend/src/composables/useTaskPlans.ts:409-436`（`runScriptAsset` 改调 quick-execute）
- Modify: `frontend/src/api/task-plans.ts:76-94`（`createScenarioApi` payload：`scriptVersionId` 可选 + `purpose/testType`）

**Interfaces:**
- Consumes: Task 13 API（`quickExecuteApi/listPlanTemplatesApi`）、Task 14 `statusLabel`。
- Produces: 无下游依赖（终端任务）。

- [ ] **Step 1: TaskPlanList.vue —— 阶段列**

在 `columns` 定义中（约 40 行后的 script 部分）加一列并在模板 `bodyCell` 渲染 badge：

```ts
{ title: '阶段', key: 'phase', width: 140 },
```

```vue
<template #bodyCell="{ column, record }">
  <template v-if="column.key === 'phase'">
    <a-tag :color="phaseColor(record.phase)">{{ phaseText(record.phase) }} · {{ statusLabel(record.phase, record.status) }}</a-tag>
  </template>
</template>
```

```ts
import { statusLabel } from '../../composables/usePlanDoc';

function phaseText(phase: string) {
  return { DRAFT: '草稿', REVIEW: '评审', EXECUTION: '执行', REPORT: '报告', PUBLISH: '发布' }[phase] ?? phase;
}

function phaseColor(phase: string) {
  return { DRAFT: 'default', REVIEW: 'processing', EXECUTION: 'warning', REPORT: 'cyan', PUBLISH: 'success' }[phase] ?? 'default';
}
```

（若该表格当前没有 `bodyCell` 模板，新增 `<template #bodyCell>` 于 `a-table` 内即可，其余列不受影响。）

- [ ] **Step 2: TaskPlanDialog.vue —— 模板选择**

创建态（`props.editingPlan === null`）表单顶部加模板选择；选择后无需预览内容（内容服务端渲染）：

```vue
<a-form-item v-if="!editingPlan" label="计划模板">
  <a-select v-model:value="templateId" placeholder="通用压测计划（默认）" allow-clear>
    <a-option v-for="template in templates" :key="template.id" :value="template.id">
      {{ template.builtin ? '内置 · ' : '' }}{{ template.name }}
    </a-option>
  </a-select>
</a-form-item>
```

```ts
import { useRoute } from 'vue-router';
import { listPlanTemplatesApi } from '../../api/plan-doc';
import type { PlanTemplate } from '../../types';

const route = useRoute();
const templateId = ref<number | null>(null);
const templates = ref<PlanTemplate[]>([]);

watch(() => props.modelValue, async (open) => {
  if (open && !props.editingPlan && templates.value.length === 0) {
    const projectId = Number(route.params.projectId);
    if (projectId) templates.value = await listPlanTemplatesApi(projectId).catch(() => []);
  }
});
```

保存 payload 追加 `templateId: templateId.value ?? undefined`，`createTaskPlanApi`（`api/task-plans.ts`）payload 类型加 `templateId?: number | null`。

- [ ] **Step 3: ScenarioDialog.vue —— 业务字段与脚本可选**

表单加两个字段 + 脚本选择改为可空：

```vue
<a-form-item label="测试类型" name="testType">
  <a-select v-model:value="form.testType" allow-clear placeholder="选择测试类型">
    <a-option value="BENCHMARK">基准</a-option>
    <a-option value="SINGLE_TXN">单交易并发</a-option>
    <a-option value="COMPOSITE">组合交易</a-option>
    <a-option value="STABILITY">稳定性</a-option>
  </a-select>
</a-form-item>
<a-form-item label="场景目的" name="purpose">
  <a-textarea v-model:value="form.purpose" :rows="2" placeholder="业务目的，写入计划文档场景章节" />
</a-form-item>
```

脚本选择项的校验规则移除必填（`scriptVersionId` 可空 = 评审后再关联），form 状态加 `purpose: string` / `testType: string | null`，保存 payload 透传。`api/task-plans.ts` 的 `createScenarioApi`/`updateScenarioApi` payload 类型同步：`scriptVersionId?: number | null; purpose?: string; testType?: string | null`。

- [ ] **Step 4: 清理孤儿组件 ExecuteConfirmDialog**

`grep -rn "ExecuteConfirmDialog" frontend/src --include="*.vue"`——Task 14 重写 TaskPlanDetail 后该组件不再被引用（执行入口已移入 Task 16 的 ScenarioDesignModule，precheck 跳过在那里实现）。确认无引用后：

```bash
git rm frontend/src/components/task-plans/ExecuteConfirmDialog.vue
```

若有残留引用（如其它视图），保留文件并把其确认回调改为处理 `PLAN_PRECHECK_FAILED`（跳过 = `precheckSkipApi(planId)` 后重试触发），语义与 Task 16 `run()` 相同。

- [ ] **Step 5: SharePlanPage.vue + 路由**

```vue
<template>
  <main class="share-page">
    <h1>{{ shared?.name ?? '压测计划' }}</h1>
    <p class="share-meta">发布时间：{{ shared?.publishedAt ? new Date(shared.publishedAt).toLocaleString() : '—' }}</p>
    <MdPreview :model-value="shared?.body ?? '加载中…'" language="zh-CN" />
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { MdPreview } from 'md-editor-v3';
import 'md-editor-v3/lib/style.css';
import { getSharedPlanApi } from '../api/plan-doc';

const route = useRoute();
const shared = ref<{ name: string; body: string | null; publishedAt: string | null } | null>(null);

onMounted(async () => {
  try {
    shared.value = await getSharedPlanApi(String(route.params.token));
  } catch {
    shared.value = { name: '分享链接不存在或已失效', body: '', publishedAt: null };
  }
});
</script>

<style scoped>
.share-page { max-width: 960px; margin: 24px auto; padding: 0 16px; background: #fff; }
.share-meta { color: #5c6b7a; }
</style>
```

`router/index.ts`：顶层 routes 数组（`/login` 同级）加：

```ts
    {
      path: '/share/plans/:token',
      name: 'share-plan',
      component: SharePlanPage,
    },
```

import 加 `import SharePlanPage from '../views/SharePlanPage.vue';`；守卫第一行加：

```ts
  if (to.name === 'share-plan') return true;
```

- [ ] **Step 6: useTaskPlans.runScriptAsset 改造**

`frontend/src/composables/useTaskPlans.ts` 的 `runScriptAsset`（409 行起）整体替换：

```ts
  async function runScriptAsset(script: ScriptAsset) {
    if (!currentProject.value) return false;
    try {
      await confirmAction({
        title: '执行脚本',
        content: `确认执行脚本「${script.name}」？将创建任务计划并立即执行。`,
        okText: '执行',
      });
      const result = await quickExecuteApi(script.id);
      await loadPlans();
      void router.push(`/projects/${currentProject.value.id}/executions/${result.executionId}`);
      message.success('已创建计划并提交执行');
      return true;
    } catch (error) {
      message.error(error instanceof Error ? error.message : '执行失败');
      return false;
    }
  }
```

import 追加 `import { quickExecuteApi } from '../api/plan-doc';`。

- [ ] **Step 7: 验证 + Commit**

Run: `cd frontend && npm run build`
Expected: 0 错误。手工：列表 badge 五色正确；创建计划选模板后正文含 11 章节；场景弹窗可无脚本创建；脚本列表"执行"走单请求（Network 只见 `POST /api/scripts/{id}/quick-execute`）；分享链接匿名浏览器可打开。

```bash
git add frontend/src
git commit -m "feat：P0-1 Task17 列表阶段徽标、模板选择、场景业务字段、precheck 跳过、分享公开页与快捷执行单请求化"
```

---

### Task 18: 收尾 —— 领域词汇、实施日志、全量回归与验收走查

**Files:**
- Modify: `CONTEXT.md`（领域概念补 Plan 文档域）
- Modify: `docs/implementation-log.md`（追加 P0-1 条目）
- Modify: `docs/architecture-and-roadmap.md:241`（P0-1 状态 ⬜ → ✅，附一行"验收日期"）

**Interfaces:** 无代码接口；交付文档与验收结论。

- [ ] **Step 1: CONTEXT.md 领域词汇追加**

在"领域概念"列表 `TaskPlan` 条目后追加（或改写原条目）：

```markdown
- **TaskPlan（压测计划文档）**：一稿走到头——同一份 Markdown 原文经历 计划（评审）→ 执行（回填）→ 报告 → 发布；`body` 是唯一数据源（11 章节中文序号），Pretty 视图 = 受约束章节提取展示。二级状态：phase（草稿/评审/执行/报告/发布）+ status。
- **PlanComment / PlanTemplate / PlanPublishSnapshot / PlanShareToken**：评审批注（REVIEW/SYSTEM 两类，全文档级）/ 计划模板（内置+项目自定义）/ 发布快照（P1-4 消费）/ 只读分享令牌。
- **revision 与冲突**：任何原文变化 revision+1；更新带 baseRevision，不一致 409 + serverMarkdown，三选一解决（保留平台版/采纳本地版/手改），不做自动合并。
- **环境检查（precheck）**：计划执行设置（precheck_json），非文档内容；评审通过后首次执行自动运行、可跳过（系统批注留痕）。
```

模块地图追加一行：

```markdown
| `task/plandoc/PlanWorkflowService` | 计划文档域：状态机/批注/模板/分享/发布快照/precheck 门禁 | 执行门禁挂 ExecutionControlService seam（P0-1） |
```

- [ ] **Step 2: implementation-log 追加条目**

按该文件既有格式追加 P0-1 段（范围一句话 + 关键提交列表 + 验收口径核对结果）。

- [ ] **Step 3: 全量回归**

Run: `export JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ && ./gradlew :backend:test && cd frontend && npm run build`
Expected: 后端 BUILD SUCCESSFUL + 前端 0 错误。

- [ ] **Step 4: 验收走查（路线图 P0-1 口径，逐条人工核对并在 PR/提交说明记录结果）**

启动 `./gradlew :backend:bootRun` + `cd frontend && npm run dev`，以两个浏览器身份（admin + 项目成员）核对：

1. **从草稿走到已发布**：新建计划（模板）→ 提交评审 → 成员开始评审 + 批注 → 通过 → 进入执行 → 添加场景（无脚本）→ 关联脚本 → 执行（precheck 提示可跳过）→ 执行完成（场景块出现 `#### 执行记录` 条目）→ 进入报告 → 生成报告（结论章节出现结果总览与实际列）→ 发布（填总体结论）→ 状态=已发布、文档冻结。
2. **批注**：草稿/评审可发、进入执行后只读、SYSTEM 批注不可删、作者/负责人可删 REVIEW。
3. **冲突**：双标签页并发编辑 → 409 弹三选一，三种路径均收敛到一致 revision。
4. **复测**：报告已生成后再执行 → 报告重置为待生成；`newRevision` 后可再编辑。
5. **分享**：发布后创建链接 → 匿名窗口可读 → 撤销后 404。

- [ ] **Step 5: Commit**

```bash
git add CONTEXT.md docs/implementation-log.md docs/architecture-and-roadmap.md
git commit -m "docs：P0-1 收尾——领域词汇/实施日志/路线图勾选，验收走查通过"
```

---

## 附录：规格覆盖对照（Spec → Task）

| 设计文档章节 | 覆盖任务 |
|---|---|
| §3.1 一稿走到头/章节结构/Pretty 提取 | Task 2（解析）、Task 6（模板=格式合同）、Task 15（Pretty 视图） |
| §3.2/§3.3 新实体与包结构 | Task 1/4/6/10（实体）、Task 3-12（服务，全部在 `task/plandoc` + `ProjectAccessResolver`） |
| §3.4 业务化场景/脚本后置关联/翻译规则 | Task 7（实体+文档回写+绑定动作）、Task 16（场景模块 UI）、Task 17（场景弹窗） |
| §4 二级状态机/流转/编辑冻结 | Task 1（列）、Task 3（矩阵）、Task 5（流转）、Task 8/9（事件驱动）、Task 12（REST） |
| §5 revision/409/三选一 | Task 4（冲突体）、Task 15（冲突弹窗） |
| §6 批注 | Task 4（实体）、Task 5（CRUD 权限）、Task 16（时间线 UI） |
| §7 模板 | Task 6（seed/CRUD/渲染）、Task 17（选择 UI） |
| §8 回填 | Task 2（幂等标记）、Task 9（终态回填）、Task 11（报告回填） |
| §9 发布/快照/分享 | Task 10、Task 11、Task 16（发布 Tab）、Task 17（公开页） |
| §10 门禁/环境检查/快捷执行 | Task 8、Task 9（quick-execute）、Task 15（执行设置抽屉）、Task 16/17（跳过交互） |
| §11 DDL | Task 1/6/10（mysql-schema.sql 同步） |
| §12 REST | Task 12（+Task 13 补模板四端点） |
| §13 权限 | Task 3 |
| §14 前端 | Task 13-17 |
| §15 错误处理 | Task 1（异常）、Task 12（处理器） |
| §17 测试计划 | 各任务测试 + Task 18 验收走查 |
