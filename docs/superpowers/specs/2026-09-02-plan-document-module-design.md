# P0-1 计划文档模块重构 —— 详细设计

> 日期：2026-09-02
> 来源：`docs/architecture-and-roadmap.md` P0-1 行，遵循决策 D1/D2/D3/D4/D5/D6/D11/D12。
> 本文档是 P0-1 的设计规格；验收口径以路线图为准，实现按此文档拆 Issue。

## 1. 目标与验收口径

**验收口径（路线图原文）**：一个计划能从草稿走到"已发布"，中间可评审、可批注、可被本地 Agent 同步修改且冲突可手工处理。

**P0-1 范围内**：

- TaskPlan 升级为压测计划文档：结构化字段 + Markdown 正文（D1）
- 计划状态机：草稿 → 待评审 → 评审中 → 已通过 → 已发布（终态），"执行中"为派生显示状态（D2）
- 评审流程：项目成员批注、通过/驳回、撤回（D2）
- 模板体系：内置通用模板 + 项目自定义模板，Markdown 占位符渲染（D3）
- 自动回填：执行摘要自动追加到文档正文；回填机制预留环境检查/缺陷清单扩展点（D4）
- revision 冲突三选一：409 + 双栏差异 + 保留平台版/采纳本地版/手改（D5）
- 发布终态：发布权限校验、发布快照（D11 预埋）、只读分享链接（D2）

**P0-1 范围外（由后续任务承接，见 §16）**：

- MCP 计划工具集（P0-2）、验收标准实体与自动判等（P0-3）、环境检查（P1-1）、迭代对比（P1-4）、缺陷台账/通知（P3-7/P3-8）
- 行级/锚定批注、自动合并、历史版本追溯（D5 明确不做自动覆盖与行级合并，历史追溯未要求）

## 2. 现状盘点与约束（代码事实）

> 包根：`backend/src/main/java/com/yr/perftest/platform`。

| 事实 | 位置 | 对设计的影响 |
|------|------|--------------|
| `TaskPlan` 是纯字段 record（id/projectId/name/remark/默认节点/监控目标），无状态、无正文 | `task/TaskPlan.java`、`task/PersistentTaskPlanRecord.java` | 状态机、正文、结构化字段全部是新增 |
| 计划-场景-执行经 `scenarioId` 关联，计划/项目在执行中由 join 派生 | `task/ScenarioExecution.java`、`task/ExecutionQueryService.java:257` | 执行摘要回填按 planId 聚合已有现成查询 |
| 执行状态机 `QUEUED→RUNNING→SUCCESS/FAILED/INTERRUPTED`，`RUNNING→STOPPING→CANCELLED` | `execution/ExecutionStatus.java` | "计划执行中"由场景执行状态派生，避免双份状态 |
| 执行启停唯一 seam 是 `ExecutionControlService.start` | `task/ExecutionControlService.java`（CONTEXT.md C1） | **执行门禁必须加在这条 seam 上**，新入口（快捷执行）也必须走它 |
| 快捷执行：前端"脚本列表 → 执行"先建计划（`/task-plans`）再建场景再触发 | `frontend/src/composables/useTaskPlans.ts:409` `runScriptAsset` | 若门禁生效，此流程需改造为服务端合并端点（§10.3） |
| 登录体系存在：Bearer token → `AuthenticationFilter` 设置 SecurityContext，`HumanPrincipal(username, SystemRole)` | `config/AuthenticationFilter.java:42`、`identity/HumanPrincipal.java` | 新端点从 SecurityContext 取身份；不再信任裸 `X-User` 头 |
| 项目成员模型存在但未在计划接口强制：`project.ownerUsername` + `project_members(username, OWNER/MEMBER)` | `project/PersistentProjectRecord.java:30`、`PersistentProjectMemberRecord.java` | 评审/发布权限要新建一层访问判定（§13） |
| 系统级 `SystemRole.ADMIN` 存在 | `identity/SystemRole.java` | 系统 ADMIN 全权兜底 |
| 回填数据源现成：`ExecutionSummary`（executionId/planId/throughput/p95/errorRate/status）与 `ReportDataService.aggregateByPlan` | `facade/data/ExecutionSummary.java`、`report/ReportDataService.java:59` | 回填直接复用，不新建查询 |
| 无 `@Version` 乐观锁先例；seed 模块有手工 `config_version`/`version_no` 计数先例 | `seed/PersistentSeedCaptureStrategyRecord.java:50` | revision 用普通 INT 列 + 应用层比对即可，与 D5 语义一致 |
| 无 Flyway/Liquibase，`ddl-auto: update` + 手写 `docs/database/mysql-schema.sql` | `application.yml:8-10` | 新列新表同时改实体与 sql 文件（P0-4 并行批次） |
| 前端无 Markdown 编辑器依赖；ant-design-vue 4 + Vue 3.5 | `frontend/package.json` | 需引入编辑组件（§14.4） |
| 后端测试为 JUnit 5 + `@SpringBootTest` 集成测试与 MockMvc API 测试两种风格 | `backend/src/test/java/.../task/` | 新代码按两种风格补测试（§17） |
| 无任何 review/comment/revision 域概念（grep 确认） | — | 批注、状态机均为绿地 |

## 3. 领域模型

### 3.1 计划文档 = 结构化字段 + Markdown 正文（D1）

```
TaskPlan（升级后）
├─ 既有字段：id / projectId / name / remark / createdBy / createdAt / updatedAt
│            defaultControllerNodeId / defaultWorkerNodeIds / defaultMonitorTargetIds
├─ 新增结构化字段：
│   goal        目标指标与背景说明（自由文本，P0-3 的验收标准实体另行挂载）
│   environment 环境说明（自由文本）
│   conclusion  结论（发布时人工确认填写；P0-3 起改为"判等预填 + 人工确认"半自动）
├─ 新增文档字段：
│   body        Markdown 正文（含用户内容与系统回填区块）
│   revision    文档修订号（D5 冲突控制）
│   status      状态机当前状态（存储态；"执行中"为派生显示态，见 §4.3）
│   publishedAt 发布时间（终态时间戳）
└─ 关联场景：task_scenarios（现状不变，快照见 §9.2）
```

- "验收标准实体"属 P0-3：P0-1 只提供 `goal` 自由文本字段占位，不建验收实体、不做判等（避免死列与双份工作，边界见 §16）。
- "关联场景"即现有 `task_scenarios`，不新增字段。

### 3.2 新增实体

| 实体 | 职责 | 表 |
|------|------|-----|
| `PlanComment` | 评审批注 + 系统流转记录 | `plan_comments` |
| `PlanTemplate` | 计划模板（内置 + 项目自定义） | `plan_templates` |
| `PlanPublishSnapshot` | 发布时固化的文档+场景+结果摘要快照（P1-4 消费） | `plan_publish_snapshots` |
| `PlanShareToken` | 已发布计划的只读分享链接 | `plan_share_tokens` |

### 3.3 新增服务与包结构

遵循"拆模块只挪边界不挪语义"，P0-1 不拆 Maven 模块（模块拆分是 §7 的并行轨道），新类放 `task/plandoc` 子包：

- `task/plandoc/TaskPlanStatus.java` — 状态枚举
- `task/plandoc/PlanDocumentService.java` — 文档读写、revision 冲突、回填追加
- `task/plandoc/PlanWorkflowService.java` — 状态机流转、批注、发布快照、分享链接、模板 CRUD
- `task/plandoc/PlanAccess.java`（+ `project/ProjectAccessResolver.java`）— 权限判定
- `task/plandoc/PlanValidationException.java` / `PlanStateException.java` / `PlanRevisionConflictException.java`
- `task/ExecutionCompletedEvent.java` + `task/plandoc/PlanBackfillListener.java` — 执行结束回填
- `api/PlanDocumentController.java` — 新 REST 面

P0-2 的 MCP 工具直接调用这两个 domain service（facade seam 共用），不额外建 facade 类。

## 4. 状态机（D2）

### 4.1 状态与转移

```
 DRAFT ──submit──▶ PENDING_REVIEW ──startReview──▶ IN_REVIEW ──approve──▶ APPROVED ──publish──▶ PUBLISHED
   ▲                    ▲                              │                │
   │◀──withdraw─────────┘◀──withdraw───────────────────┘◀──reject───────┘
   └───────────────────────────── newRevision（仅从 PUBLISHED）◀─────────────────────────────────┘
```

存储态：`DRAFT / PENDING_REVIEW / IN_REVIEW / APPROVED / PUBLISHED`。终态 = `PUBLISHED`。
显示态：当计划任一场景存在活跃执行（`QUEUED/RUNNING/STOPPING`）时，读取接口返回 `RUNNING`（**派生，不落库**，避免与执行状态机双写漂移）。详见 §4.3。

### 4.2 转移动作与权限（权限判定见 §13）

| 动作 | 前置 | 后置 | 允许者 | 附注 |
|------|------|------|--------|------|
| `submit` 提交评审 | DRAFT | PENDING_REVIEW | 计划负责人 / 项目 OWNER / 系统 ADMIN | |
| `startReview` 开始评审 | PENDING_REVIEW | IN_REVIEW | 项目成员 | 记录系统批注 |
| `approve` 评审通过 | IN_REVIEW | APPROVED | 项目成员 | 记录系统批注（含审批人）；自审允许（见决策 2） |
| `reject` 驳回 | IN_REVIEW | DRAFT | 项目成员 | **必须附批注**；记录系统批注 |
| `withdraw` 撤回 | PENDING_REVIEW / IN_REVIEW | DRAFT | 计划负责人 / 项目 OWNER / 系统 ADMIN | |
| `publish` 发布 | APPROVED 且无活跃执行 | PUBLISHED | 计划负责人 / 项目 OWNER / 系统 ADMIN | 生成发布快照（§9.2） |
| `newRevision` 发起新修订 | PUBLISHED | DRAFT | 计划负责人 / 项目 OWNER / 系统 ADMIN | `revision + 1`；旧发布快照保留 |

### 4.3 "执行中"派生规则

- 读取计划时查询：该计划全部场景下是否存在 `QUEUED/RUNNING/STOPPING` 的执行，存在则返回 `RUNNING`。
- `RUNNING` 期间：`publish` 被拒绝（"存在进行中的执行"）；文档编辑本就被状态机限制（仅 DRAFT 可编辑），无额外处理。
- 执行全部结束后自然回落到存储态（APPROVED 或 PUBLISHED）。发布后继续执行（P1-4 的"同计划再执行"）不改变存储态。

### 4.4 编辑与状态的关系

- **文档编辑（结构化字段 + 正文）仅允许在 DRAFT**。评审中/已通过状态下要改文档，先 `withdraw` 回 DRAFT 再编辑，编辑后重新 `submit`。
- 执行默认配置（默认 controller/workers/monitors，老 `PUT /api/task-plans/{id}`）同样仅 DRAFT 可改；这是"评审内容不可变"的简化规则（决策 3）。
- 场景增删改：现有场景接口不受状态机限制（执行配置在触发时固化，执行中修改不影响在跑执行）。**例外**：已发布计划冻结场景增删改（D11 快照含场景列表，发布后场景变化会破坏快照一致性），需要改则先 `newRevision`。发布状态下场景接口返回 409 并提示发起新修订。
- 回填是系统写操作，不受 DRAFT 限制、不校验 baseRevision（§8.4）。

## 5. 修订与冲突处理（D5）

### 5.1 revision 语义

- `task_plans.revision`：文档修订号，从 1 起。**任何导致文档内容变化（结构化字段或正文）的写入 `revision + 1`**，包括用户编辑与系统回填。
- 执行默认配置（§4.4）变化**不**影响 revision（不属于文档内容）。
- 场景增删改**不**影响 revision（冲突控制只覆盖文档内容；P0-2 的 MCP `plan_update` 同样只更新文档）。

### 5.2 乐观并发控制

- 更新文档必须携带 `baseRevision`（等于读取时拿到的 revision）。
- 服务端在事务内比对：`当前 revision != baseRevision` → **409 `PLAN_REVISION_CONFLICT`**，响应体：

```json
{
  "code": "PLAN_REVISION_CONFLICT",
  "message": "计划文档已被修改（当前 revision=5，提交基于 revision=4）",
  "currentRevision": 5,
  "serverDocument": { "goal": "…", "environment": "…", "conclusion": "…", "markdown": "…" }
}
```

- 服务端**不落盘**请求方提交的内容（提交方自己持有本地版本），只返回当前服务器版本。不做自动覆盖、不做行级合并（D5）。

### 5.3 三选一冲突解决（UI 与本地 Agent 通用）

1. **保留平台版**：放弃本地修改，重新读取服务器版本继续工作。
2. **采纳本地版**：以 409 返回的 `currentRevision` 为新 base，重放本地全部内容（整文档覆盖，非行级合并）。
3. **手改**：以服务器版本为基底打开编辑器，人工合并后再以 `currentRevision` 提交。

MCP（P0-2）与 REST 共用同一语义：P0-2 的 `plan_update` 返回同样结构，本地 Agent 自行实现三选一（路线图 D5 的"差异文本"由双方各自持有两版全文实现）。

### 5.4 双栏差异展示

- 前端冲突弹窗左右并排展示"平台当前版"与"本地版"，行级高亮（§14.3）。
- 差异对比范围 = 结构化字段 + 正文；结构化字段用并排表单，正文用行级 diff。

## 6. 评审与批注（D2）

### 6.1 批注模型

`plan_comments`：`id / planId / author / content / kind / createdAt`。

- `kind = REVIEW`：评审批注，项目成员在 `DRAFT/PENDING_REVIEW/IN_REVIEW/APPROVED` 状态可发。
- `kind = SYSTEM`：流转记录（提交评审/开始评审/通过/驳回/撤回/发布/发起新修订/快捷执行自动通过/执行摘要回填），由服务写入，不可编辑删除。
- **批注为全文档级，不做行锚定**：Markdown 行号随编辑漂移，且 D5 明确不做行级合并，行锚定收益低、实现重（决策 5）。
- `PUBLISHED` 状态：评审批注与系统批注均只读（终态冻结）。

### 6.2 批注权限

- 新增批注：项目成员（含负责人）。
- 删除批注：作者本人 / 计划负责人 / 项目 OWNER / 系统 ADMIN；`SYSTEM` 批注不可删。

## 7. 模板体系（D3）

### 7.1 数据模型

`plan_templates`：`id / projectId(null=内置) / name / description / content(Markdown) / builtin / createdBy / createdAt / updatedAt`。

- 内置模板：`builtin=true, projectId=null`，启动时由初始化器 seed（存在即跳过），不可编辑删除。
- 项目自定义模板：`builtin=false, projectId=项目`，项目 OWNER / 系统 ADMIN 可增删改，项目成员可查看使用。

### 7.2 占位符与渲染

- 占位符语法 `{{name}}`。P0-1 提供 4 个：`{{planName}} / {{goal}} / {{environment}} / {{conclusion}}`。
- 渲染发生在"从模板创建计划"：占位符替换为对应字段值，未填字段渲染为空。模板自带内容（如固定章节骨架）原样保留。
- 首版内置模板一份："通用压测计划"（目标/环境/风险/场景清单章节骨架 + 占位符）。

## 8. 自动回填（D4）

### 8.1 机制

- 系统在正文维护受管区块 `## 执行记录`，回填只向该区块**追加**条目、不触碰其它正文（用户可自由编辑该区块之外的任何内容）。
- 回填由 `PlanBackfillListener` 监听 `ExecutionCompletedEvent` 触发（执行进入终态 SUCCESS/FAILED/INTERRUPTED/CANCELLED 时由执行写路径发布该事件）。
- 幂等：每条目带 `<!-- backfill:execution:<executionId> -->` 标记，追加前检查正文是否已含该标记，存在则跳过。
- 回填条目模板（确定性拼接，不调用 LLM）：

```markdown
<!-- backfill:execution:123 -->
### 2026-09-02 14:30 · <场景名> · <执行名>
- 状态：SUCCESS
- 样本 12000 ｜ 吞吐 158.3 TPS ｜ P95 96 ms ｜ 错误率 0.42%
```

- 回填数据来自 `ExecutionSummary`（`facade/data/ExecutionSummary.java`）。
- 环境检查结果（P1-1）、缺陷清单（P3-7）回填走同一追加机制，P0-1 只交付机制 + 执行摘要一个源。

### 8.2 回填与 revision

回填是系统写入：不校验 baseRevision、不受状态机编辑限制，但 **`revision + 1`**（内容确实变了）。本地 Agent 若持有旧 revision 提交会收 409 → 重新读取（此时正文含回填条目）→ 保留自己的修改以新 base 提交，即现有冲突流程，无特殊处理。

### 8.3 结论回填（P0-3 半自动的预埋）

P0-1 只提供 `conclusion` 字段；"判等自动算 + 发布前人工确认"在 P0-3 实现（判等结果预填 conclusion，人工确认后发布）。

## 9. 发布终态、快照与分享（D2/D11）

### 9.1 发布动作

`publish`（APPROVED → PUBLISHED，权限见 §4.2）：

1. 校验无活跃执行（有则 409）。
2. 生成发布快照（§9.2）。
3. 置 `status=PUBLISHED, publishedAt=now`，写系统批注"已发布（revision=N）"。
4. 发布后：文档、默认配置、场景均冻结；继续执行不受影响（P1-4 同计划再执行场景）；要变更内容走 `newRevision`。

### 9.2 发布快照（D11 预埋）

`plan_publish_snapshots`：`id / planId / revision / publishedBy / publishedAt / docJson / scenarioJson / summaryJson`，`unique(planId, revision)`。

- `docJson`：发布时的结构化字段 + 正文全文。
- `scenarioJson`：场景列表（场景名/脚本版本 ID/线程组配置）。
- `summaryJson`：各场景最近一次成功执行的结果摘要（复用 `ReportDataService`/`ExecutionQueryService` 聚合结果）。
- P1-4 迭代对比直接消费最近两个快照；`newRevision` 后再发布产生新 revision 快照，旧快照保留。

### 9.3 只读分享链接

- `plan_share_tokens`：`id / planId / token(UUID) / expiresAt / revokedAt / createdBy / createdAt`。
- 创建：仅已发布计划，权限 = 计划负责人 / 项目 OWNER / 系统 ADMIN，可选有效期（默认 30 天）。
- 撤销：删除/置 `revokedAt`；过期或撤销后访问返回 404。
- 公开读取：`GET /api/share/plans/{token}`，无需登录（SecurityConfiguration 放行 `/api/share/**`），返回结构化字段 + 正文（前端渲染）+ 发布时间，不返回场景/执行明细、批注与快照。

## 10. 执行门禁与快捷执行改造

### 10.1 门禁规则

- `ExecutionControlService.start`（唯一 seam）在现有校验后追加：场景 → 计划，要求计划存储态 ∈ `{APPROVED, PUBLISHED}`（派生 RUNNING 时存储态即二者之一），否则抛 `PlanStateException`（409，附"请先通过评审"）。
- 影响面：计划详情页的执行按钮、场景执行按钮、agent 面 `StartExecutionTool`。agent 面行为不变（先审批后执行是闭环本意）；前端按钮在计划不可执行时禁用并给出提示。

### 10.2 现有行为变化（明确声明）

- 今天任何状态都可执行；门禁后**未通过评审的计划不可执行**。这是 P0 闭环的刻意收紧（决策 1）。

### 10.3 快捷执行改造

现状 `runScriptAsset`（`useTaskPlans.ts:409`）在前端串三步。改造为服务端合并端点：

`POST /api/scripts/{scriptVersionId}/quick-execute`
→ 服务端事务：创建计划（`status=APPROVED`，name=`<脚本名> / 即时执行`）→ 写系统批注"快捷执行自动通过" → 创建场景 → 走 `ExecutionControlService.start` 触发 → 返回 executionId。
前端 `runScriptAsset` 改为调用该端点。**自动通过逻辑只存在于服务端一处**，普通 `POST /task-plans` 创建的计划一律 DRAFT，不提供绕过评审的参数。

## 11. 数据模型 DDL

### 11.1 task_plans 新增列

```sql
ALTER TABLE task_plans
  ADD COLUMN status       VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
  ADD COLUMN revision     INT          NOT NULL DEFAULT 1,
  ADD COLUMN goal         TEXT         NULL,
  ADD COLUMN environment  TEXT         NULL,
  ADD COLUMN conclusion   TEXT         NULL,
  ADD COLUMN body         LONGTEXT     NULL,
  ADD COLUMN published_at DATETIME     NULL;
```

存量行迁移：默认 `DRAFT / revision=1 / body=null`，无数据订正需求（无生产数据）。

### 11.2 新表

```sql
CREATE TABLE plan_comments (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  plan_id    BIGINT      NOT NULL,
  author     VARCHAR(80) NOT NULL,
  content    TEXT        NOT NULL,
  kind       VARCHAR(20) NOT NULL,            -- REVIEW / SYSTEM
  created_at DATETIME    NOT NULL,
  KEY idx_plan_comments_plan (plan_id, id)
);

CREATE TABLE plan_templates (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id  BIGINT       NULL,              -- NULL = 内置
  name        VARCHAR(160) NOT NULL,
  description VARCHAR(1000) NULL,
  content     LONGTEXT     NOT NULL,
  builtin     TINYINT(1)   NOT NULL DEFAULT 0,
  created_by  VARCHAR(80)  NOT NULL,
  created_at  DATETIME     NOT NULL,
  updated_at  DATETIME     NOT NULL,
  KEY idx_plan_templates_project (project_id)
);

CREATE TABLE plan_publish_snapshots (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  plan_id      BIGINT      NOT NULL,
  revision     INT         NOT NULL,
  published_by VARCHAR(80) NOT NULL,
  published_at DATETIME    NOT NULL,
  doc_json     LONGTEXT    NOT NULL,
  scenario_json LONGTEXT   NOT NULL,
  summary_json LONGTEXT    NULL,
  UNIQUE KEY uk_plan_snapshot (plan_id, revision)
);

CREATE TABLE plan_share_tokens (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  plan_id    BIGINT      NOT NULL,
  token      VARCHAR(64) NOT NULL,
  expires_at DATETIME    NULL,
  revoked_at DATETIME    NULL,
  created_by VARCHAR(80) NOT NULL,
  created_at DATETIME    NOT NULL,
  UNIQUE KEY uk_share_token (token),
  KEY idx_share_plan (plan_id)
);
```

- 同步更新 `docs/database/mysql-schema.sql`；开发态依赖 `ddl-auto: update`（H2 兼容，P0-4 并行批次负责 MySQL 正式迁移）。
- 现有 `TaskPlanService.createPlan/updatePlan/deletePlan` 扩展：创建写 status/revision 初始值；删除级联清理 comments/snapshots/share tokens。

## 12. REST API 设计

新端点统一挂 `api/PlanDocumentController`（认证身份取 SecurityContext `HumanPrincipal`，不再信任 `X-User` 头；未登录 401）。

### 12.1 计划文档

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/task-plans/{id}` | 扩展响应：加 `status(含派生 RUNNING)/revision/goal/environment/conclusion/body/publishedAt`；`owner` 即 `createdBy`（计划负责人，不新增列） |
| PUT | `/api/task-plans/{id}/document` | 请求 `{baseRevision, goal?, environment?, conclusion?, markdown?}`；200 返回新文档 / 409 冲突（§5.2） |
| POST | `/api/task-plans/{id}/submit` | body 可选 `{comment}` |
| POST | `/api/task-plans/{id}/start-review` | |
| POST | `/api/task-plans/{id}/approve` | body 可选 `{comment}` |
| POST | `/api/task-plans/{id}/reject` | body `{comment}`（必填） |
| POST | `/api/task-plans/{id}/withdraw` | |
| POST | `/api/task-plans/{id}/publish` | 返回快照 id；有活跃执行/非 APPROVED 则 409 |
| POST | `/api/task-plans/{id}/new-revision` | PUBLISHED → DRAFT，revision+1 |
| PUT | `/api/task-plans/{id}` | 老端点（默认配置）保留；DRAFT 外 409（§4.4） |
| DELETE | `/api/task-plans/{id}` | 权限收紧为 负责人/OWNER/ADMIN（§13）；级联新表 |
| POST | `/api/projects/{projectId}/task-plans` | 创建扩展：可选 `{templateId, goal, environment}`，模板渲染正文 |

### 12.2 批注 / 模板 / 分享 / 快捷执行

| 方法 | 路径 | 说明 |
|------|------|------|
| GET/POST | `/api/task-plans/{id}/comments` | 批注列表 / 新增（REVIEW） |
| DELETE | `/api/task-plans/{id}/comments/{commentId}` | 权限见 §6.2 |
| GET | `/api/projects/{projectId}/plan-templates` | 内置 + 项目自定义 |
| POST/PUT/DELETE | `/api/projects/{projectId}/plan-templates[/{id}]` | 仅项目自定义；OWNER/ADMIN |
| POST | `/api/task-plans/{id}/shares` | body `{expiresInDays?}`；仅已发布 |
| GET/DELETE | `/api/task-plans/{id}/shares[/{tokenId}]` | 列表 / 撤销 |
| GET | `/api/share/plans/{token}` | 公开只读（放行白名单） |
| POST | `/api/scripts/{scriptVersionId}/quick-execute` | §10.3 |

### 12.3 响应通用约定

- 每个计划读取响应附带 `permissions`：当前用户对该计划可执行动作列表（`EDIT/SUBMIT/START_REVIEW/APPROVE/REJECT/WITHDRAW/PUBLISH/NEW_REVISION/DELETE/COMMENT/SHARE`），前端按钮显隐以此为准。
- 状态非法 → 409 `PLAN_STATE`（附当前状态与允许动作）；权限不足 → 403；文档不存在 → 404。

## 13. 权限模型

### 13.1 角色解析（`ProjectAccessResolver`）

对计划所在项目，解析当前用户角色：

1. 系统 `SystemRole.ADMIN` → 视为项目 OWNER 之上（全权）。
2. `project.ownerUsername == username` 或 `project_members.role == OWNER` → **项目 OWNER**。
3. `project_members` 存在 → **项目成员**。
4. 计划 `createdBy == username` → **计划负责人**（负责人通常也是成员；非成员负责人不单独放权，以成员身份为前提）。
5. 其它 → 拒绝（403）。

### 13.2 动作矩阵（汇总 §4.2/§6/§7/§9/§10）

| 动作 | 成员 | 负责人 | 项目 OWNER | 系统 ADMIN |
|------|:--:|:--:|:--:|:--:|
| 读计划/批注 | ✅ | ✅ | ✅ | ✅ |
| 编辑文档（DRAFT） | ❌ | ✅ | ✅ | ✅ |
| 改默认配置（DRAFT） | ❌ | ✅ | ✅ | ✅ |
| 场景增删改（非发布） | ✅ | ✅ | ✅ | ✅ |
| submit / withdraw | ❌ | ✅ | ✅ | ✅ |
| startReview / 批注 | ✅ | ✅ | ✅ | ✅ |
| approve / reject | ✅ | ✅ | ✅ | ✅ |
| publish / newRevision / 分享创建撤销 | ❌ | ✅ | ✅ | ✅ |
| 删除计划 | ❌ | ✅ | ✅ | ✅ |
| 模板管理（增删改） | ❌ | ❌ | ✅ | ✅ |
| 快捷执行 | ✅ | ✅ | ✅ | ✅ |

说明：approve 允许任意项目成员（含负责人自审）——评审人 = 项目成员（D2），最终放行由发布权限（负责人/OWNER/ADMIN）兜底（决策 2）。MCP 白名单（D12）在 P0-2 落地：publish 终态、删除类、分享创建不进 MCP，与本权限模型无冲突。

## 14. 前端设计

### 14.1 计划详情页重构（`TaskPlanDetail.vue`）

改为 Tab 结构，沿用现有项目壳路由 `/projects/:projectId/task-plans/:planId`：

- **文档 Tab**：状态条（状态标签 + 负责人 + revision + 动作按钮按 `permissions` 显隐）；结构化字段表单（goal/environment/conclusion）；Markdown 编辑器；保存带 baseRevision，409 弹冲突框（§14.3）。
- **评审 Tab**：批注时间线（REVIEW + SYSTEM 区分样式）、新增批注框、流转动作（开始评审/通过/驳回/撤回）。
- **场景 Tab**：现有场景面板（执行按钮按计划状态禁用并提示）。
- **发布 Tab**：发布按钮、发布快照列表（时间/revision/发布人）、分享链接创建/撤销列表。

### 14.2 计划列表（`TaskPlanList.vue`）

加状态 badge（含派生"执行中"）、负责人列；行内动作随权限过滤。

### 14.3 冲突三选一弹窗

- 左右双栏："平台当前版" vs "本地版"；结构化字段并排表单 + 正文行级 diff 高亮。
- 三个按钮：**保留平台版**（丢弃本地，重新加载）/ **采纳本地版**（以 currentRevision 重放本地全文）/ **手改**（以平台版为基底进编辑器）。
- 行级 diff 用 `jsdiff`（轻量、无渲染依赖）自绘高亮，不引重组件。

### 14.4 Markdown 编辑器

- 引入 `md-editor-v3`（Vue3 原生、工具栏 + 预览 + 中文生态成熟），计划文档编辑与模板编辑共用。
- 分享公开页 `/share/plans/:token`：独立公开路由，Markdown 只读渲染 + 结构化字段展示。

### 14.5 其它

- `TaskPlanDialog`（创建弹窗）：增加模板选择、goal/environment 输入；创建即 DRAFT。
- 快捷执行按钮改调 `/api/scripts/{scriptVersionId}/quick-execute`。
- 新增 `frontend/src/api/plan-doc.ts` 与类型扩展（`TaskPlan` 增新字段）。

## 15. 错误处理

| 异常 | HTTP | code | 说明 |
|------|------|------|------|
| `PlanStateException` | 409 | `PLAN_STATE` | 状态非法，附当前状态 + 允许动作 |
| `PlanRevisionConflictException` | 409 | `PLAN_REVISION_CONFLICT` | §5.2 响应体 |
| `PlanAccessDeniedException` | 403 | `PLAN_ACCESS_DENIED` | 非项目成员/权限不足 |
| `PlanValidationException` | 400 | `PLAN_INVALID` | 参数非法（如 reject 缺批注、模板占位符越界） |
| 分享过期/撤销 | 404 | `SHARE_NOT_FOUND` | 不泄露计划是否存在 |

统一走现有 `PlatformExceptionHandler` 注册。

## 16. 与其它任务的边界

| 任务 | 关系 |
|------|------|
| P0-2 MCP 工具集 | 直接调用 `PlanDocumentService`/`PlanWorkflowService`；`plan_update` 复用 409 冲突语义与 `baseRevision` 参数 |
| P0-3 验收判等 | P0-1 提供 goal/conclusion 字段；P0-3 建验收标准实体 + 判等引擎，结论半自动（预填 conclusion）走 P0-3 |
| P1-1 环境检查 | 环境检查结果回填走 P0-1 的受管区块追加机制（扩展点） |
| P1-4 迭代对比 | 消费 `plan_publish_snapshots`（P0-1 已按 D11 内容建快照） |
| P0-4 MySQL 迁移 | 并行批次：新 DDL 进 `mysql-schema.sql`，开发 H2 靠 ddl-auto |
| 模块拆分（并行轨道） | P0-1 不拆 Maven 模块；新类放 `task/plandoc`，后续挪 core 不动语义 |

## 17. 测试计划

沿用现有两种风格（JUnit 5 `@SpringBootTest` 集成 + MockMvc API）。

**状态机与权限（集成测试）**：

- 全转移链：DRAFT→PENDING_REVIEW→IN_REVIEW→APPROVED→PUBLISHED→(newRevision)→DRAFT 每步的成功与非法转移（409）
- 权限矩阵：成员/负责人/OWNER/ADMIN/非成员 对每个动作的允许与 403
- reject 缺批注 → 400；approve 后 publish 无活跃执行 → 成功

**冲突与三选一**：

- 相同 baseRevision 更新成功且 revision+1；过期 baseRevision → 409 且响应含 serverDocument；以 currentRevision 重放 → 成功
- 回填 bump revision 后旧 base 提交 → 409 → 重放成功（模拟 Agent 交替编辑）

**回填**：

- 执行终态事件 → 正文追加受管区块条目且幂等（同 executionId 二次触发不重复）
- 回填不触碰 `## 执行记录` 之外的正文

**发布与分享**：

- 快照内容 = 发布时文档/场景/摘要；unique(planId, revision) 冲突防护
- 分享链接匿名可读、过期/撤销 404、非发布计划不可创建

**门禁与快捷执行**：

- DRAFT/PENDING_REVIEW 计划触发执行 → 409；APPROVED 可执行
- quick-execute 单请求完成 建计划(APPROVED)+系统批注+场景+执行
- 活跃执行期间 publish → 409；派生 RUNNING 状态正确回落

**前端**：冲突三选一弹窗交互、权限驱动按钮显隐（沿用 `frontend/tests` 现有测试栈）。

## 18. 关键决策清单（请评审确认）

实现前请确认以下在路线图中未完全钉死、本设计自行拍板的点：

1. **执行门禁**：仅 APPROVED/PUBLISHED 计划可执行；快捷执行改为服务端合并端点并自动通过（系统批注留痕）。
2. **approve 权限**：任意项目成员可 approve（含负责人自审）；最终放行靠发布权限兜底。
3. **编辑冻结规则**：文档与默认配置仅 DRAFT 可编辑；评审中改内容先撤回；已发布冻结文档/配置/场景，改则发起新修订。
4. **发布后修订**：已发布不是永久冻结，`newRevision` 回 DRAFT 继续迭代，旧快照保留（支撑 P1-4 多版本对比）。
5. **批注粒度**：全文档级批注，不做行锚定。
6. **分享链接**：仅已发布计划可创建，匿名只读，默认 30 天有效、可撤销。
7. **回填与 revision**：回填走系统写入（不校验 baseRevision）但 revision+1。
8. **删除收紧**：计划删除从"任何人"收紧为 负责人/项目 OWNER/系统 ADMIN。
9. **模板管理权限**：项目 OWNER/系统 ADMIN 管理模板；成员可用。内置模板 seed 于启动。
10. **新依赖**：前端 `md-editor-v3`（Markdown 编辑）+ `jsdiff`（冲突双栏 diff）。

## 附录 A：决策依据索引

- D1 结构化字段 + Markdown 正文 → §3.1；D2 状态机/评审人/发布权限 → §4/§6/§13；D3 模板 → §7；D4 自动回填 → §8；D5 冲突三选一 → §5；D6 验收判等（P0-3）→ §16；D11 发布快照 → §9.2；D12 MCP 白名单 → §13.2。
- 执行 seam 唯一性（CONTEXT.md C1）→ §10.1 门禁位置。
