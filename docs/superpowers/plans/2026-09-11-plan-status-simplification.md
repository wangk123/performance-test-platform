# 计划文档状态机简化 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将计划文档的 phase×status 双枚举状态机合并为单一 5 值状态单行道（计划中→评审中→执行中→报告编辑中→已发布），删除全部回退动作，版本与状态解耦，门禁软化为前端二次确认。

**Architecture:** 后端一次原子换核（枚举合并牵动 14 个主代码文件 + 23 个测试文件，编译耦合无法拆分提交），随后前端接线（新状态推导纯函数 + 组件适配），最后文档与流程图。执行联动与计划状态解耦：执行生命周期不再回写计划状态，回填逻辑保留。

**Tech Stack:** Spring Boot 3 / JPA / Flyway（MySQL，测试 H2 MySQL 模式）、Vue 3 + TypeScript + Ant Design Vue、Vitest。

**Spec:** `docs/superpowers/specs/2026-09-11-plan-status-simplification-design.md`（本计划从 spec 出发，执行者需同时阅读 spec）

## Global Constraints

- 新状态枚举值固定 5 个：`PLANNING, IN_REVIEW, EXECUTING, REPORTING, PUBLISHED`（不得增删改名）。
- `PlanPhase` 枚举与 `task_plans.phase` 列彻底删除，任何代码不得残留 phase 概念（`plan_versions.plan_phase` 历史快照列除外，保留原值不迁移）。
- 流转动作仅 4 个：SUBMIT（提交评审）、APPROVE（评审通过）、FINISH（执行完成，新端点 finish-execution）、PUBLISH（发布）。禁止新增任何回退/回滚动作。
- 版本号一律手动输入，禁止任何自动 +1 / 自动生成版本号逻辑（revision 内部计数器除外，且任何 UI 不得展示 revision 数值）。
- 权限维度全部删除：所有动作门槛 = 登录且为项目成员（NONE 角色 403 保留）；不得保留 ownerLike/memberLike 判定。
- 后端不得因执行活动状态拒绝 finish-execution / publish（409 拦截删除）；唯一保留的发布硬校验 = 总体结论必填。
- 运行 Gradle 必须设置 `JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/`。
- 后端聚焦测试命令（任务内用，只跑受影响类）：`JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ ./gradlew :backend:test --tests 'com.yr.perftest.platform.task.plandoc.*' --tests 'com.yr.perftest.platform.api.Plan*' ...`（按任务实际触及的包追加过滤）。**全量 `:backend:test` 整个执行过程只在最终收尾时跑一次**（见 Task 3），各任务禁止跑全量。
- 前端测试/构建（Task 2/3 用）：`cd frontend && npm run test`（vitest，秒级）与 `npm run build`（vue-tsc + vite）。
- 提交信息用仓库现行风格（中文、`feat：`/`refactor：` 前缀、全角冒号）。

---

### Task 1: 后端状态机换核（原子任务）

> **警告执行者：** 本任务必须整体完成、单次提交。`PlanPhase` 删除后 14 个主代码文件与 23 个测试文件同时编译失败，无法拆成多个绿色提交。步骤按序执行，中途编译红是预期的，最后一步全量测试绿后才提交。

**Files:**
- Create: `backend/src/main/resources/db/migration/V7__plan_status_simplify.sql`
- Delete: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanPhase.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanStatus.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/PersistentTaskPlanRecord.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/TaskPlan.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/TaskPlanService.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanAccess.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanStateException.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanWorkflowService.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanDocumentService.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/TaskScenarioService.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanVerdictService.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanVersionService.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanQuickExecuteService.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/ExecutionControlService.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/api/PlanDocumentController.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/api/PlanErrorBody.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/mcp/McpToolSupport.java`（仅 PlanStateException 构造适配）
- Test: 下列 23 个测试文件全部适配（清单见 Step 13）

**Interfaces:**
- Produces（Task 2 依赖）:
  - `PlanStatus` 5 值枚举；
  - `TaskPlan` record 删除 `phase` 组件，仅保留 `status`；
  - `PlanAccess.compute(PlanStatus status) -> Map<String, Boolean>`，键集：`EDIT, COMMENT, NEW_VERSION, EXECUTE, SUBMIT, APPROVE, FINISH, PUBLISH, SHARE, DELETE, PRECHECK_RUN, PRECHECK_SKIP`；
  - `PlanResponse(TaskPlan plan, Map<String, Boolean> permissions, int activeExecutions)`；
  - REST：`POST /api/task-plans/{id}/finish-execution` 新增；`start-review`/`reject`/`withdraw`/`back-to-draft`/`start-execution`/`new-revision` 六个端点删除；
  - `PlanErrorBody` 删除 `phase` 字段，保留 `status` + `allowedActions`。

- [ ] **Step 1: 写 V7 迁移脚本**

```sql
-- V7__plan_status_simplify.sql
-- 状态机简化（spec 2026-09-11 §5）：phase×status 双枚举合并为单一 status。
-- 先放宽列类型再改写值，避免旧 ENUM 不含新值导致写入失败；最终保持 varchar 与 plan_versions.plan_phase 同风格。
ALTER TABLE `task_plans` MODIFY COLUMN `status` varchar(20) NOT NULL;

UPDATE `task_plans` SET `status` = CASE
    WHEN `phase` = 'DRAFT'     AND `status` = 'DRAFT'                    THEN 'PLANNING'
    WHEN `phase` = 'REVIEW'    AND `status` IN ('PENDING', 'IN_REVIEW')  THEN 'IN_REVIEW'
    WHEN `phase` = 'REVIEW'    AND `status` = 'APPROVED'                 THEN 'EXECUTING'
    WHEN `phase` = 'EXECUTION' AND `status` IN ('PENDING', 'RUNNING')    THEN 'EXECUTING'
    WHEN `phase` = 'EXECUTION' AND `status` = 'DONE'                     THEN 'REPORTING'
    WHEN `phase` = 'REPORT'                                              THEN 'REPORTING'
    WHEN `phase` = 'PUBLISH'   AND `status` = 'PUBLISHED'                THEN 'PUBLISHED'
    ELSE 'PLANNING'
  END;

ALTER TABLE `task_plans` DROP COLUMN `phase`;
```

- [ ] **Step 2: 重写 PlanStatus、删除 PlanPhase**

`PlanStatus.java` 全文替换为：

```java
package com.yr.perftest.platform.task.plandoc;

/** 计划单一状态（spec 2026-09-11 §3.1）：单行道流转，无回退。 */
public enum PlanStatus { PLANNING, IN_REVIEW, EXECUTING, REPORTING, PUBLISHED }
```

删除 `PlanPhase.java` 文件。

- [ ] **Step 3: 实体与记录单字段化**

`PersistentTaskPlanRecord.java`：
- 删除字段 `private PlanPhase phase = PlanPhase.DRAFT;` 及其 getter/setter 与 `@Column` 映射；
- `private PlanStatus status = PlanStatus.DRAFT;` 改为 `= PlanStatus.PLANNING;`；
- `forceState(PlanPhase, PlanStatus)` 与 `transitionTo(PlanPhase, PlanStatus)` 合并为单参：

```java
/** 仅供测试与数据订正直接置状态；正常流转走 PlanWorkflowService。 */
public void forceState(PlanStatus status) {
    this.status = status;
    this.updatedAt = Instant.now();
}

/** 状态机流转写入（前置校验在 PlanWorkflowService）。 */
public void transitionTo(PlanStatus status) {
    this.status = status;
    this.updatedAt = Instant.now();
}
```

- `applyPublish(Instant publishedAt)` 内部改 `transitionTo(PlanStatus.PUBLISHED);` 后仍写 `publishedAt`；
- 删除 `applyNewRevision()` 方法整体。

`TaskPlan.java`（record）：删除 `PlanPhase phase,` 组件，保留 `PlanStatus status`。`TaskPlanService.toPlan(...)`（约 212 行）同步删除 `plan.getPhase(),` 实参。

- [ ] **Step 4: PlanStateException 单状态化**

```java
public class PlanStateException extends RuntimeException {
    private final PlanStatus status;
    private final java.util.List<String> allowedActions;

    public PlanStateException(String message, PlanStatus status, java.util.List<String> allowedActions) {
        super(message);
        this.status = status;
        this.allowedActions = java.util.List.copyOf(allowedActions);
    }

    public PlanStatus getStatus() { return status; }
    public java.util.List<String> getAllowedActions() { return allowedActions; }
}
```

`PlanErrorBody.java`：删除 `String phase,` 组件（保留 `status`、`allowedActions`），`PlatformExceptionHandler` 与 `McpToolSupport.java` 中构造处同步删参。

- [ ] **Step 5: TDD 重写 PlanAccessTest（先红）**

`PlanAccessTest.java` 全文替换为（删除全部角色维度用例）：

```java
package com.yr.perftest.platform.task.plandoc;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 无角色维度：动作可见性只由单一状态决定（spec §4.1/§4.4/§10）。 */
class PlanAccessTest {

    private Map<String, Boolean> of(PlanStatus status) {
        return PlanAccess.compute(status);
    }

    @Test
    void planningOnlyAllowsSubmit() {
        Map<String, Boolean> p = of(PlanStatus.PLANNING);
        assertThat(p.get("SUBMIT")).isTrue();
        assertThat(p.get("APPROVE")).isFalse();
        assertThat(p.get("FINISH")).isFalse();
        assertThat(p.get("PUBLISH")).isFalse();
    }

    @Test
    void inReviewOnlyAllowsApprove() {
        Map<String, Boolean> p = of(PlanStatus.IN_REVIEW);
        assertThat(p.get("APPROVE")).isTrue();
        assertThat(p.get("SUBMIT")).isFalse();
        assertThat(p.get("FINISH")).isFalse();
    }

    @Test
    void executingOnlyAllowsFinish() {
        Map<String, Boolean> p = of(PlanStatus.EXECUTING);
        assertThat(p.get("FINISH")).isTrue();
        assertThat(p.get("APPROVE")).isFalse();
        assertThat(p.get("PUBLISH")).isFalse();
        assertThat(p.get("EXECUTE")).isTrue();
    }

    @Test
    void reportingAllowsPublishAndExecute() {
        Map<String, Boolean> p = of(PlanStatus.REPORTING);
        assertThat(p.get("PUBLISH")).isTrue();
        assertThat(p.get("FINISH")).isFalse();
        assertThat(p.get("EXECUTE")).isTrue(); // 报告阶段支持复测
    }

    @Test
    void publishedIsTerminalButUnfrozen() {
        Map<String, Boolean> p = of(PlanStatus.PUBLISHED);
        assertThat(p.get("PUBLISH")).isFalse();
        assertThat(p.get("SHARE")).isTrue();
        assertThat(p.get("EDIT")).isTrue();       // 发布后不冻结文档
        assertThat(p.get("NEW_VERSION")).isTrue();
    }

    @Test
    void globalActionsAvailableInEveryStatus() {
        for (PlanStatus status : PlanStatus.values()) {
            Map<String, Boolean> p = of(status);
            assertThat(p.get("EDIT")).as("EDIT in %s", status).isTrue();
            assertThat(p.get("COMMENT")).as("COMMENT in %s", status).isTrue();
            assertThat(p.get("NEW_VERSION")).as("NEW_VERSION in %s", status).isTrue();
            assertThat(p.get("DELETE")).as("DELETE in %s", status).isTrue();
            assertThat(p.get("PRECHECK_RUN")).as("PRECHECK_RUN in %s", status).isTrue();
            assertThat(p.get("PRECHECK_SKIP")).as("PRECHECK_SKIP in %s", status).isTrue();
        }
    }

    @Test
    void executeForbiddenBeforeApproved() {
        assertThat(of(PlanStatus.PLANNING).get("EXECUTE")).isFalse();
        assertThat(of(PlanStatus.IN_REVIEW).get("EXECUTE")).isFalse();
    }
}
```

Run: `JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ ./gradlew :backend:test --tests 'com.yr.perftest.platform.task.plandoc.PlanAccessTest'`
Expected: 编译失败（compute 新签名不存在）——即预期的红。

- [ ] **Step 6: 重写 PlanAccess.compute（转绿）**

`PlanAccess.java` 全文替换为：

```java
package com.yr.perftest.platform.task.plandoc;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 动作可见性矩阵（spec 2026-09-11 §4）：无角色维度（权限专项前全员=项目成员门槛在服务层校验）。
 * 键集见 Global Constraints；流转键 SUBMIT/APPROVE/FINISH/PUBLISH 每状态至多一个为 true（单行道）。
 */
public final class PlanAccess {

    private PlanAccess() {
    }

    public static Map<String, Boolean> compute(PlanStatus status) {
        Map<String, Boolean> p = new LinkedHashMap<>();
        p.put("EDIT", true);                 // 任意状态可编辑，revision 冲突保护兜底
        p.put("COMMENT", true);              // 批注不再限评审域
        p.put("NEW_VERSION", true);          // 新增版本与状态解耦
        p.put("DELETE", true);
        p.put("PRECHECK_RUN", true);
        p.put("PRECHECK_SKIP", true);
        p.put("SUBMIT", status == PlanStatus.PLANNING);
        p.put("APPROVE", status == PlanStatus.IN_REVIEW);
        p.put("FINISH", status == PlanStatus.EXECUTING);
        p.put("PUBLISH", status == PlanStatus.REPORTING);
        p.put("EXECUTE", status == PlanStatus.EXECUTING || status == PlanStatus.REPORTING);
        p.put("SHARE", status == PlanStatus.PUBLISHED);
        return p;
    }
}
```

Run: 同 Step 5 命令。Expected: 编译仍失败（PlanWorkflowService/Controller 等未适配）——PlanAccessTest 本身绿需等 Step 12 全量跑。**不要单独提交。**

- [ ] **Step 7: 重写 PlanWorkflowService 流转方法**

删除方法：`startReview`、`reject`、`withdraw`、`backToDraft`、`startExecution`、`newRevision`、`onExecutionStarted`。
修改 `submit`：

```java
@Transactional
public void submit(long planId, HumanPrincipal actor, String comment) {
    PersistentTaskPlanRecord plan = requireActor(planId, actor, "SUBMIT");
    requireStatus(plan, PlanStatus.PLANNING, "SUBMIT");
    plan.transitionTo(PlanStatus.IN_REVIEW);
    commentService.systemComment(planId, actor.username() + " 提交评审");
    if (comment != null && !comment.isBlank()) {
        commentService.appendReviewNote(planId, actor.username(), comment.trim());
    }
}
```

修改 `approve`（通过即进入执行阶段，spec §4.1 迁移 2）：

```java
@Transactional
public void approve(long planId, HumanPrincipal actor, String comment) {
    PersistentTaskPlanRecord plan = requireActor(planId, actor, "APPROVE");
    requireStatus(plan, PlanStatus.IN_REVIEW, "APPROVE");
    plan.transitionTo(PlanStatus.EXECUTING);
    commentService.systemComment(planId, "评审通过，进入执行阶段（审批人：" + actor.username() + "）");
    if (comment != null && !comment.isBlank()) {
        commentService.appendReviewNote(planId, actor.username(), comment.trim());
    }
}
```

新增 `finishExecution`：

```java
/** 执行完成：人工确认进入报告阶段（spec §4.1 迁移 3）。活跃执行只由前端二次确认告警，后端不拦截。 */
@Transactional
public void finishExecution(long planId, HumanPrincipal actor) {
    PersistentTaskPlanRecord plan = requireActor(planId, actor, "FINISH");
    requireStatus(plan, PlanStatus.EXECUTING, "FINISH");
    plan.transitionTo(PlanStatus.REPORTING);
    commentService.systemComment(planId, actor.username() + " 执行完成，进入报告阶段");
}
```

修改 `publish`：门槛改 REPORTING；**删除** `if (documentService.hasActiveExecution(planId)) throw ...` 整段；`plan.applyPublish(now)` 保留；`versionService.publishForWorkflow(...)` 保留；方法首部加 `requireStatus(plan, PlanStatus.REPORTING, "PUBLISH");`。回填（upsertReportOverview/upsertVerdictTable/fillConclusionActualColumn/总体结论行）全部保留不动。

修改 `onExecutionTerminal`：删除末尾 `if (plan.getPhase() == ... ) plan.transitionTo(...DONE)` 状态迁移块，只保留 `documentService.backfillExecutionRecord(...)` 回填调用（spec §4.5）。

修改 `assertExecutionAllowed`：阶段判断替换为：

```java
boolean phaseOk = plan.getStatus() == PlanStatus.EXECUTING || plan.getStatus() == PlanStatus.REPORTING;
if (!phaseOk) {
    throw new PlanStateException("PLAN_STATE：请先通过评审进入执行阶段（当前 " + plan.getStatus() + "）",
            plan.getStatus(), List.of("SUBMIT", "APPROVE"));
}
```

（脚本关联校验、precheck 自动运行逻辑不动。）

修改 `requireActor`（删除角色矩阵消费，只留成员门槛 + 动作状态门槛）：

```java
/** 校验动作权限：非项目成员 403；成员但状态不允许 409（附允许动作）。 */
private PersistentTaskPlanRecord requireActor(long planId, HumanPrincipal actor, String action) {
    PersistentTaskPlanRecord plan = requirePlan(planId);
    if (actor == null) {
        throw new PlanAccessDeniedException("PLAN_ACCESS_DENIED：未登录");
    }
    ProjectAccessResolver.PlanActorRole role = accessResolver.resolve(plan.getProjectId(), actor, plan.getCreatedBy());
    if (role == ProjectAccessResolver.PlanActorRole.NONE) {
        throw new PlanAccessDeniedException("PLAN_ACCESS_DENIED：非项目成员");
    }
    Map<String, Boolean> permissions = PlanAccess.compute(plan.getStatus());
    if (!Boolean.TRUE.equals(permissions.get(action))) {
        throw new PlanStateException("PLAN_STATE：当前状态不允许「" + action + "」（当前 "
                + plan.getStatus() + "，允许：" + allowedActions(permissions) + "）",
                plan.getStatus(), allowedActions(permissions));
    }
    return plan;
}

private void requireStatus(PersistentTaskPlanRecord plan, PlanStatus expected, String action) {
    if (plan.getStatus() != expected) {
        Map<String, Boolean> permissions = PlanAccess.compute(plan.getStatus());
        throw new PlanStateException("PLAN_STATE：当前状态不允许「" + action + "」（当前 "
                + plan.getStatus() + "，允许：" + allowedActions(permissions) + "）",
                plan.getStatus(), allowedActions(permissions));
    }
}
```

删除 `requireActor` 中原 privileged 判定与 `hasAnyExecution(planId)` 传参；`updatePrecheckSettings` 中 `requireActor(planId, actor, "PRECHECK_RUN")` 调用保留（动作键仍在矩阵中）；`getSharedPlan` 中 `plan.getPhase() != PlanPhase.PUBLISH` 改为 `plan.getStatus() != PlanStatus.PUBLISHED`。

- [ ] **Step 8: 解耦执行联动**

`PlanDocumentService.java`：删除 `correctExecutionState(long planId)` 方法及 `getDocument` 中对它的调用（约 42 行）；新增活跃执行计数（软门禁数据源）：

```java
/** 活跃执行数（QUEUED/RUNNING/STOPPING）：执行完成/发布前的二次确认告警数据源（spec §4.3）。 */
@Transactional(readOnly = true)
public int countActiveExecutions(long planId) {
    List<Long> scenarioIds = scenarioRepository.findAllByPlanIdOrderBySortOrderAscIdAsc(planId).stream()
            .map(com.yr.perftest.platform.task.PersistentTaskScenarioRecord::getId)
            .toList();
    int count = 0;
    for (Long scenarioId : scenarioIds) {
        if (executionRepository.existsByScenarioIdAndStatusIn(scenarioId, List.of(
                com.yr.perftest.platform.execution.ExecutionStatus.QUEUED,
                com.yr.perftest.platform.execution.ExecutionStatus.RUNNING,
                com.yr.perftest.platform.execution.ExecutionStatus.STOPPING))) {
            count++;
        }
    }
    return count;
}
```

（若 `existsByScenarioIdAndStatusIn` 在 `PersistentScenarioExecutionRepository` 不存在则新增该查询方法；对照现有 `existsByScenarioId` 风格。）

`ExecutionControlService.java` 第 70 行：删除 `planWorkflowService.onExecutionStarted(planId);` 调用及该依赖字段/构造参数（若仅此一处使用）。

- [ ] **Step 9: 场景与判等服务门禁适配**

`TaskScenarioService.java`：
- `bindScript` 门禁替换为：

```java
boolean afterApproval = plan.getStatus() == PlanStatus.EXECUTING
        || plan.getStatus() == PlanStatus.REPORTING
        || plan.getStatus() == PlanStatus.PUBLISHED;
if (!afterApproval) {
    throw new PlanStateException(
            "PLAN_STATE：评审通过后才可关联脚本（当前 " + plan.getStatus() + "）",
            plan.getStatus(), List.of("APPROVE"));
}
```

- `requireScenarioMutationAllowed` 替换为（发布后不再冻结，仅活跃执行期间禁止增删改；扫描模式与 `deleteScenario` 现有写法一致）：

```java
/** 场景增删改门禁（spec §4.4）：存在活跃执行则冻结；发布后不冻结。 */
private void requireScenarioMutationAllowed(PersistentTaskPlanRecord plan) {
    for (PersistentTaskScenarioRecord scenario : scenarioRepository.findAllByPlanIdOrderBySortOrderAscIdAsc(plan.getId())) {
        boolean active = executionRepository.findAllByScenarioIdOrderByIdDesc(scenario.getId()).stream()
                .anyMatch(e -> e.getStatus() == com.yr.perftest.platform.execution.ExecutionStatus.QUEUED
                        || e.getStatus() == com.yr.perftest.platform.execution.ExecutionStatus.RUNNING
                        || e.getStatus() == com.yr.perftest.platform.execution.ExecutionStatus.STOPPING);
        if (active) {
            throw new com.yr.perftest.platform.task.plandoc.PlanStateException(
                    "PLAN_STATE：存在活跃执行，场景禁止增删改（当前 " + plan.getStatus() + "）",
                    plan.getStatus(), java.util.List.of("FINISH"));
        }
    }
}
```

`PlanVerdictService.java` 第 102-104 行可读性判断替换为：

```java
boolean available = plan.getStatus() == PlanStatus.REPORTING
        || plan.getStatus() == PlanStatus.PUBLISHED;
```

`PlanVersionService.java`：`doPublish` 中两处 `plan.getPhase().name()` 改为 `plan.getStatus().name()`。

`PlanQuickExecuteService.java` 第 61 行：`raw.forceState(PlanPhase.EXECUTION, PlanStatus.PENDING);` 改为 `raw.forceState(PlanStatus.EXECUTING);`。

- [ ] **Step 10: Controller 端点增删与响应扩展**

`PlanDocumentController.java`：
- 删除端点方法：`startReview`、`reject`、`withdraw`、`backToDraft`、`startExecution`、`newRevision`；
- 新增：

```java
@PostMapping("/task-plans/{planId}/finish-execution")
public PlanResponse finishExecution(@PathVariable long planId) {
    workflowService.finishExecution(planId, requireHuman());
    return getPlan(planId);
}
```

- `PlanResponse` 扩展：

```java
public record PlanResponse(TaskPlan plan, Map<String, Boolean> permissions, int activeExecutions) {
}
```

`getPlan` 返回 `new PlanResponse(plan, permissionsOf(plan), documentService.countActiveExecutions(planId));`（注入已有 `documentService`）。
- `permissionsOf` 改用 `PlanAccess.compute(plan.status())`（删除 role/hasAnyExecution 传参；principal 为空仍返回 `Map.of()`）；
- `deletePlan` 中权限判断改用新矩阵（`permissions.get("DELETE")` 语义不变，成员级放行）；错误文案改"PLAN_ACCESS_DENIED：非项目成员不可删除计划"。

- [ ] **Step 11: 修复全部受影响测试（编译 + 行为）**

23 个测试文件的处理策略（映射基准：`REVIEW·APPROVED→EXECUTING`、`EXECUTION·PENDING/RUNNING→EXECUTING`、`EXECUTION·DONE→REPORTING`、`REPORT·*→REPORTING`、`DRAFT→PLANNING`、`REVIEW·PENDING/IN_REVIEW→IN_REVIEW`、`PUBLISH·PUBLISHED→PUBLISHED`）：

| 测试文件 | 处理 |
|---|---|
| `PlanAccessTest` | Step 5 已重写 |
| `PlanWorkflowServiceTest` | 删除 startReview/reject/withdraw/backToDraft/startExecution/newRevision 用例；四流转各保留/新增：submit（PLANNING→IN_REVIEW）、approve（IN_REVIEW→EXECUTING）、finishExecution（EXECUTING→REPORTING）、publish（REPORTING→PUBLISHED）；新增：错误起始状态各断言 409 PLAN_STATE；publish 存在活跃执行仍成功（原拦截用例反转） |
| `PlanDocumentServiceTest` | 删除 correctExecutionState 用例；编辑/回填断言保留；`forceState(两参)` 全部改单参 |
| `PlanReportPublishTest` | publish 前置状态改 `forceState(PlanStatus.REPORTING)`；总体结论必填断言保留；活跃执行 409 用例删除或改为成功 |
| `PlanShareTest` | 前置改 `forceState(PlanStatus.PUBLISHED)` |
| `PlanExecutionGateTest` | `assertExecutionAllowed`：EXECUTING/REPORTING 放行、PLANNING/IN_REVIEW 409；脚本/precheck 断言保留 |
| `PlanExecutionLifecycleTest` | 终态回填断言保留；删除状态迁移断言（终态后 status 不变） |
| `PlanScenarioMutationGateTest` | 场景增删改：无活跃执行任意状态放行（含 PUBLISHED）、有活跃执行禁止；bindScript：PLANNING/IN_REVIEW 禁止、EXECUTING/REPORTING/PUBLISHED 放行 |
| `PlanQuickExecuteServiceTest` | forceState 单参机械修复 |
| `PlanVerdictServiceTest` / `PlanVerdictReportTest` / `api/PlanVerdictApiTest` | 可读性前置状态改 REPORTING/PUBLISHED |
| `PlanEntityExtensionTest` | forceState 单参机械修复 |
| `PlanInitialMarkdownCreateTest` | 默认状态断言改 `PLANNING` |
| `api/PlanDocumentApiTest` | 删除六个废弃端点用例；新增 finish-execution 200/409；PlanResponse 断言加 `activeExecutions` 字段；permissions 键集断言更新 |
| `mcp/McpServerApiTest` / `mcp/plan/McpToolSupportPlanErrorTest` | PlanErrorBody 无 `phase` 字段断言适配 |
| `agent/AgentAuditApiTest`、`agent/AgentExecutionControlApiTest`、`api/UiExecutionControlApiTest`、`auxscript/AuxScriptApiTest`、`governance/AgentGovernanceApiTest`、`task/ExecutionControlServiceTest` | 建计划后 `forceState(EXECUTING)` 机械替换原两参调用；行为断言不变 |

新增用例示例（`PlanWorkflowServiceTest`）：

```java
@Test
void finishExecutionMovesToReporting() {
    plan.forceState(PlanStatus.EXECUTING);
    workflowService.finishExecution(plan.getId(), memberActor);
    assertThat(planRepository.findById(plan.getId()).orElseThrow().getStatus())
            .isEqualTo(PlanStatus.REPORTING);
}

@Test
void finishExecutionRejectedWhenNotExecuting() {
    plan.forceState(PlanStatus.PLANNING);
    assertThatThrownBy(() -> workflowService.finishExecution(plan.getId(), memberActor))
            .isInstanceOf(PlanStateException.class);
}

@Test
void publishSucceedsEvenWithActiveExecution() {
    plan.forceState(PlanStatus.REPORTING);
    // 造一个 RUNNING 执行（沿用现有测试工厂方法）
    workflowService.publish(plan.getId(), ownerActor, "结论：通过", "v1.0");
    assertThat(planRepository.findById(plan.getId()).orElseThrow().getStatus())
            .isEqualTo(PlanStatus.PUBLISHED);
}
```

（用例中的工厂方法/字段名以该测试文件现有 setup 为准，沿用其建计划、造执行的工具方法。）

- [ ] **Step 12: 聚焦测试转绿（禁止全量）**

Run: `JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ ./gradlew :backend:test --tests 'com.yr.perftest.platform.task.plandoc.*' --tests 'com.yr.perftest.platform.task.ExecutionControlServiceTest' --tests 'com.yr.perftest.platform.api.PlanDocumentApiTest' --tests 'com.yr.perftest.platform.api.PlanVerdictApiTest' --tests 'com.yr.perftest.platform.api.UiExecutionControlApiTest' --tests 'com.yr.perftest.platform.mcp.*' --tests 'com.yr.perftest.platform.agent.*' --tests 'com.yr.perftest.platform.auxscript.*' --tests 'com.yr.perftest.platform.governance.*'`
Expected: BUILD SUCCESSFUL，0 failures（覆盖 Step 11 全部 23 个受影响测试类所在包）。若有失败逐个修复（常见：遗漏的 forceState 两参调用、PlanErrorBody 构造、H2 对 V7 的执行——若 H2 报 `ALTER TABLE ... MODIFY COLUMN` 不支持，将 V7 中两条 ALTER 改写为 H2 MySQL 模式兼容形式，但必须保持 MySQL 生产可用）。全量 `:backend:test` 留到 Task 3 收尾时跑一次。

- [ ] **Step 13: 提交**

```bash
git add backend/src
git commit -m "refactor：计划状态机换核——phase×status 双枚举合并为单一 PlanStatus（计划中→评审中→执行中→报告编辑中→已发布，无回退）——①删 start-review/reject/withdraw/back-to-draft/start-execution/new-revision 端点与服务方法，新增 finish-execution；approve 直达执行；②PlanAccess 去角色维度（全员=项目成员），键集收敛 12 键；③执行生命周期不再回写计划状态（删 onExecutionStarted/correctExecutionState），终态回填保留；④publish 删活跃执行 409 拦截（软门禁数据 activeExecutions 进 PlanResponse）；⑤场景门禁改活跃执行判定、判等可读改 REPORTING/PUBLISHED；⑥V7 迁移（存量组合映射+删 phase 列）；⑦23 个测试文件同步重写，受影响包聚焦测试绿（全量留收尾）"
```

---

### Task 2: 前端状态/阶段双显示 + 按钮区 + 版本入口 + 软门禁弹窗

**Files:**
- Create: `frontend/src/utils/plan-status.ts`
- Test: `frontend/src/utils/plan-status.test.ts`
- Modify: `frontend/src/api/plan-doc.ts`
- Modify: `frontend/src/composables/usePlanDoc.ts`
- Modify: `frontend/src/components/task-plans/PlanPhaseStepper.vue`
- Modify: `frontend/src/components/task-plans/TaskPlanDetail.vue`
- Modify: `frontend/src/components/task-plans/PlanDetailReview.vue`
- Modify: `frontend/src/components/task-plans/PlanDetailVersions.vue`
- Modify: `frontend/src/components/task-plans/PlanConflictDialog.vue`

**Interfaces:**
- Consumes: Task 1 的 REST 面（`finish-execution` 端点、PlanResponse.activeExecutions、permissions 键集、TaskPlan 无 phase）。
- Produces: `plan-status.ts` 导出 `STATUS_LABEL: Record<string,string>`、`STAGES: string[]`、`stageIndexOf(status: string): number`、`visibleActions(status: string): string[]`、`activeWarning(count: number): string | null`。

- [ ] **Step 1: 写 plan-status 纯函数模块的失败测试**

`frontend/src/utils/plan-status.test.ts`：

```typescript
import { describe, expect, it } from 'vitest';
import { STATUS_LABEL, STAGES, activeWarning, stageIndexOf, visibleActions } from './plan-status';

describe('plan-status 状态与阶段推导（spec 2026-09-11 §3.1/§3.2）', () => {
  it('五状态标签', () => {
    expect(STATUS_LABEL.PLANNING).toBe('计划中');
    expect(STATUS_LABEL.IN_REVIEW).toBe('评审中');
    expect(STATUS_LABEL.EXECUTING).toBe('执行中');
    expect(STATUS_LABEL.REPORTING).toBe('报告编辑中');
    expect(STATUS_LABEL.PUBLISHED).toBe('已发布');
  });

  it('阶段条五段与状态推导下标', () => {
    expect(STAGES).toEqual(['计划', '评审', '执行', '报告', '发布']);
    expect(stageIndexOf('PLANNING')).toBe(0);
    expect(stageIndexOf('IN_REVIEW')).toBe(1);
    expect(stageIndexOf('EXECUTING')).toBe(2);
    expect(stageIndexOf('REPORTING')).toBe(3);
    expect(stageIndexOf('PUBLISHED')).toBe(4);
  });

  it('各状态可见流转按钮（单行道）', () => {
    expect(visibleActions('PLANNING')).toEqual(['submit']);
    expect(visibleActions('IN_REVIEW')).toEqual(['approve']);
    expect(visibleActions('EXECUTING')).toEqual(['finish-execution']);
    expect(visibleActions('REPORTING')).toEqual(['publish']);
    expect(visibleActions('PUBLISHED')).toEqual([]);
  });

  it('软门禁告警文案', () => {
    expect(activeWarning(0)).toBeNull();
    expect(activeWarning(3)).toBe('还有 3 个场景执行未完成，确认继续？');
  });
});
```

Run: `cd frontend && npx vitest run src/utils/plan-status.test.ts`
Expected: FAIL（模块不存在）。

- [ ] **Step 2: 实现 plan-status.ts**

`frontend/src/utils/plan-status.ts`：

```typescript
/** 计划单一状态 → UI 推导（spec 2026-09-11 §3.1/§3.2/§4.3）。纯函数，供步骤条/按钮区/告警共用。 */

export const STATUS_LABEL: Record<string, string> = {
  PLANNING: '计划中',
  IN_REVIEW: '评审中',
  EXECUTING: '执行中',
  REPORTING: '报告编辑中',
  PUBLISHED: '已发布',
};

/** 阶段步骤条（纯展示，不落库）：计划 → 评审 → 执行 → 报告 → 发布。 */
export const STAGES = ['计划', '评审', '执行', '报告', '发布'] as const;

const STAGE_ORDER = ['PLANNING', 'IN_REVIEW', 'EXECUTING', 'REPORTING', 'PUBLISHED'];

export function stageIndexOf(status: string): number {
  const index = STAGE_ORDER.indexOf(status);
  return index < 0 ? 0 : index;
}

/** 状态 → 可见流转按钮（单行道，每状态至多一个；「新增版本」为全局常驻，不在此列）。 */
export function visibleActions(status: string): string[] {
  const map: Record<string, string[]> = {
    PLANNING: ['submit'],
    IN_REVIEW: ['approve'],
    EXECUTING: ['finish-execution'],
    REPORTING: ['publish'],
    PUBLISHED: [],
  };
  return map[status] ?? [];
}

/** 软门禁：执行完成/发布前活跃执行告警文案；无活跃执行返回 null。 */
export function activeWarning(activeExecutions: number): string | null {
  if (activeExecutions <= 0) return null;
  return `还有 ${activeExecutions} 个场景执行未完成，确认继续？`;
}
```

Run: 同 Step 1。Expected: PASS。

- [ ] **Step 3: API 层收窄**

`frontend/src/api/plan-doc.ts`：
- `TransitionAction` 收窄为：

```typescript
export type TransitionAction = 'submit' | 'approve' | 'finish-execution' | 'publish';
```

- `transitionPlanApi` 的 action 映射（若内部有 action→URL 表）删除 `start-review`/`reject`/`withdraw`/`back-to-draft`/`start-execution`/`new-revision` 项，新增 `finish-execution` 映射（URL 相同规则）；
- plan 类型定义删除 `phase` 字段；PlanResponse 返回类型加 `activeExecutions: number`；
- `publishPlanVersionApi`（新增版本）保留不变。

- [ ] **Step 4: usePlanDoc 适配**

`frontend/src/composables/usePlanDoc.ts`：`statusLabel(phase, status)` 签名简化为基于单 status（内部改用 `STATUS_LABEL[status]`，导入自 `../utils/plan-status`）；`transition()` 的 action 类型随 `TransitionAction` 收窄自动收紧；`plan` 类型随 API 层更新。

- [ ] **Step 5: 步骤条改造**

`PlanPhaseStepper.vue`：prop 从 `phase: string` 改为 `status: string`；内部用 `STAGES` + `stageIndexOf(status)` 渲染五段：下标 < 当前 = 已完成（实心/对勾）、= 当前 = 进行中（高亮）、> 当前 = 未开始；父组件 `TaskPlanDetail.vue` 中 `<PlanPhaseStepper :phase="...">` 改传 `:status="doc.plan.value?.status ?? 'PLANNING'"`。

- [ ] **Step 6: 详情页徽标 + 按钮区 + 软门禁弹窗**

`TaskPlanDetail.vue`：
- 头部徽标：删 `phase · status` 双拼与 `PHASE_TEXT`，直接 `STATUS_LABEL[status]`；`isRunning` 改 `status === 'EXECUTING'`；
- 按钮区改为：`visibleActions(status)` 渲染流转按钮（提交评审/评审通过/执行完成/发布，文案常量表）+ **常驻「新增版本」按钮**（任意状态显示，打开新增版本弹窗，复用 `publishPlanVersionApi`，表单=版本号+变更说明，二者必填）；
- 「执行完成」「发布」点击时：若 `activeExecutions > 0`，先 `Modal.confirm({ title: '未完成执行告警', content: activeWarning(activeExecutions) })`，确认后才调 `transition('finish-execution' | 'publish')`；
- 「发布」弹窗：沿用现有发布弹窗（总体结论 + 版本号），确认版本号必填（后端 publishForWorkflow 仍校验）。

- [ ] **Step 7: 评审工作台收窄**

`PlanDetailReview.vue`：删除 开始评审/驳回/撤回/退回草稿 按钮与对应 `transition(...)` 调用；保留批注列表/回复/解决/重开/编辑与「评审通过」按钮（审批通过附言输入保留）。

- [ ] **Step 8: 版本 Tab 文案 + 冲突弹窗去 revision**

`PlanDetailVersions.vue`：按钮文案「发布版本」→「新增版本」；`PlanConflictDialog.vue`：删除 revision 数值展示（"服务器当前已是 revision N"改为"他人已修改此文档"），三选一按钮/说明保留但去掉 revision 措辞。

- [ ] **Step 9: 前端验证**

Run: `cd frontend && npm run test && npm run build`
Expected: vitest 全绿（含 plan-status.test.ts 与既有 plan-blocks/plan-anchors 等）；vue-tsc 无类型错误。

- [ ] **Step 10: 提交**

```bash
git add frontend/src
git commit -m "feat：前端接入简化状态机——状态徽标单值化+五段阶段条（计划/评审/执行/报告/发布）+按钮区单行道（提交评审/评审通过/执行完成/发布）+新增版本全局常驻+执行完成/发布软门禁二次确认（activeExecutions）+冲突弹窗去 revision 数值——vitest 与 vue-tsc 构建绿"
```

---

### Task 3: 文档、流程图与端到端验收

**Files:**
- Modify: `CONTEXT.md`（TaskPlan 词条）
- Modify: `docs/superpowers/specs/2026-09-11-plan-status-simplification-design.md`（状态行改"已实现"）
- Modify: `prototype-assets/plan-lifecycle-flowchart/plan-lifecycle-flowchart.html`（仓库根，重画为目标终版）

**Interfaces:**
- Consumes: Task 1/2 的最终行为。

- [ ] **Step 1: 更新 CONTEXT.md**

TaskPlan 词条（第 9 行）替换为：

```markdown
- **TaskPlan（压测计划文档）**：一稿走到头——同一份 Markdown 原文经历 计划（评审）→ 执行（回填）→ 报告 → 发布；`body` 是唯一数据源（11 章节中文序号），Pretty 视图 = 受约束章节提取展示。单一状态 `status`（计划中/评审中/执行中/报告编辑中/已发布，单行道无回退，2026-09-11 简化）；阶段步骤条为纯前端推导。版本（手输版本号的里程碑快照）与状态解耦，任意状态可「新增版本」；发布=流转终态+同步登记版本。revision 为内部并发保护计数器，UI 不展示。执行生命周期不回写计划状态；执行终态即时回填正文。
```

- [ ] **Step 2: spec 标记与流程图重画**

spec 头部 `状态：待评审（brainstorming 收敛稿）` 改 `状态：已实现（2026-09-11）`。

`prototype-assets/plan-lifecycle-flowchart/plan-lifecycle-flowchart.html` 重画为最终版，结构要求：
- 五条泳道对应五状态（含中文标签与枚举名）；
- 主链：创建 → 计划中 →（提交评审）→ 评审中 →（评审通过）→ 执行中 →（执行完成）→ 报告编辑中 →（发布）→ 已发布（终态）；
- 「新增版本」画为横跨全部泳道的旁路动作（非状态迁移）；
- 软门禁标注：执行完成/发布前 activeExecutions>0 二次确认；
- 图例注明：无回退、发布后不冻结、批注/编辑任意状态可用；
- 保持现有 HTML/CSS 手绘风格（绝对定位节点 + SVG 连线），自包含单文件。

- [ ] **Step 3: 渲染验收流程图**

Run:
```bash
"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --headless --disable-gpu --screenshot=/tmp/plan-flow-final.png --window-size=1280,<按画布高度> --hide-scrollbars "file://$(pwd)/prototype-assets/plan-lifecycle-flowchart/plan-lifecycle-flowchart.html"
```
检查截图：无重叠/裁切/断行（可交 judge 子代理验收）。发现问题修 HTML 后重渲。

- [ ] **Step 4: 端到端手动验收 + 全量测试（整个执行过程唯一一次全量）**

Run: `JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ ./gradlew :backend:test`（全量，仅此一次）
Expected: BUILD SUCCESSFUL。

启动前后端（沿用仓库现有启动方式），走通：创建计划 → 编辑保存 → 提交评审 → 批注 → 评审通过（直进执行中）→ 发起场景执行 → 执行完成（造一个活跃执行验证二次确认弹窗出现）→ 发布（填总体结论+版本号）→ 已发布 → 新增版本（新版本号）→ 版本 Tab 可见两条记录。对照 spec §12 验收要点逐条确认。

- [ ] **Step 5: 提交**

```bash
git add CONTEXT.md docs/superpowers/specs/2026-09-11-plan-status-simplification-design.md prototype-assets/plan-lifecycle-flowchart/plan-lifecycle-flowchart.html
git commit -m "docs：CONTEXT.md 域模型同步简化状态机 + spec 标记已实现 + 流程图终版（单状态五段/四流转/新增版本旁路/软门禁标注）"
```

---

## Self-Review 记录

- **Spec 覆盖**：§3.1→Task1.2/3；§3.2→Task2.1/2/5；§3.3→Task1(versions 未动)+Task2.6（入口）；§3.4→Task2.8；§4.1→Task1.7；§4.2→Task1.7+Task2.7；§4.3→Task1.7/10+Task2.6；§4.4→Task1.6/9+Task2.6/7；§4.5→Task1.7/8；§5→Task1.1；§6→Task1.10+Task2.3；§7→Task1 全部文件；§8→Task2 全部文件；§9→Task1.11/12+Task2.1/9；§10→Task1.6/7；§11→Task1.4/7/10；§12→Task3.4；§14 延后项不在任何任务范围内（确认无泄漏实现）。
- **占位符扫描**：无 TBD/TODO；Task1.11 测试表格中"沿用现有测试工厂方法"指执行者复用该测试文件已有 setup 工具（文件内可见），不构成占位；所有代码步骤含完整代码。
- **类型一致性**：`PlanAccess.compute(PlanStatus)`（Task1.6 定义，Task1.7/10 消费）；`forceState(PlanStatus)`/`transitionTo(PlanStatus)`（Task1.3 定义，Task1.9/11 消费）；`PlanResponse(plan, permissions, activeExecutions)`（Task1.10 定义，Task2.3/6 消费）；`TransitionAction` 四值（Task2.3 定义，Task2.4/6/7 消费）；`visibleActions`/`activeWarning`/`STAGES`/`stageIndexOf`/`STATUS_LABEL`（Task2.2 定义，Task2.4/5/6 消费）。
