# 计划文档状态机简化设计：单一状态流转 + 版本解耦

- 日期：2026-09-11
- 状态：待评审（brainstorming 收敛稿）
- 前置讨论：2026-09-11 会话，逐条决策记录见 §13
- 关联文档：`docs/superpowers/specs/2026-09-10-plan-version-publish-design.md`（上一版版本/发布设计）、`CONTEXT.md`（域模型）

## 1. 背景与问题

现状计划文档（TaskPlan）采用 phase（5 值）× status（8 值）双枚举 + 角色动作矩阵（`PlanAccess` 12 个动作）驱动，并保留多条回退路径（驳回、撤回、退回草稿、新修订）。实际使用中暴露的问题：

- 状态组合多、心智负担重：`PlanStateException` 需要向用户解释"当前 phase/status 允许哪些动作"；
- 回退路径带来大量边界分支（如 back-to-draft 的"从未执行过"约束），但用户并不需要这么严格的流程管控；
- 「发布版本」（手动发版）与「发布」（状态流转）两个概念在 UI 上易混淆；
- revision 计数器暴露给用户，但用户只应关心"版本"。

## 2. 目标与非目标

**目标**

1. 流程状态收敛为单一字段、一条单行道：计划中 → 评审中 → 执行中 → 报告编辑中 → 已发布（终态）；
2. 状态只由四个流转按钮推进，删除一切回退/回滚动作；
3. 门禁从硬拦截（409）软化为前端二次确认告警；
4. 「新增版本」成为与状态无关的全局动作，版本号一律手动输入；发布动作同步创建一条版本记录；
5. 发布后不冻结文档。

**非目标（本期不做，见 §14 延后项）**

- 权限/角色矩阵细化（本期所有动作放开到项目成员级）；
- 分享链接内容语义（跟快照还是跟最新正文）；
- 环境检查（precheck）产品形态调整（本期维持现状行为）；
- 「测试方法」章节与执行记录回填形态的重设计；
- 报告章节结构与发布时报告内容的自动生成重设计。

## 3. 领域模型

### 3.1 状态（唯一流程字段）

`PlanStatus` 重新定义为 5 值，phase 与 status 合并，**不再有独立的 PlanPhase 枚举**：

| 枚举值 | 中文 | 语义 |
|---|---|---|
| `PLANNING` | 计划中 | 计划制定中（原 DRAFT 阶段） |
| `IN_REVIEW` | 评审中 | 评审批注沟通中（原 REVIEW·PENDING/IN_REVIEW） |
| `EXECUTING` | 执行中 | 压测执行期（原 EXECUTION·PENDING/RUNNING，不再细分待执行/执行中） |
| `REPORTING` | 报告编辑中 | 人工点击「执行完成」后进入，整理报告 |
| `PUBLISHED` | 已发布 | 终态。文档不再冻结，仍可编辑、可新增版本 |

### 3.2 阶段（纯展示概念，不落库）

阶段步骤条：**计划 → 评审 → 执行 → 报告 → 发布**，由 status 推导：

| status | 当前阶段 | 步骤条表现 |
|---|---|---|
| PLANNING | 计划 | 计划进行中，其余未开始 |
| IN_REVIEW | 评审 | 计划已完成，评审进行中 |
| EXECUTING | 执行 | 前2步完成，执行进行中 |
| REPORTING | 报告 | 前3步完成，报告进行中 |
| PUBLISHED | 发布 | 全部完成 |

阶段条不存储、不参与后端判定，仅前端渲染组件。

### 3.3 版本（Version）

人工命名的里程碑快照，与状态**完全解耦**：

- 「新增版本」为全局常驻按钮，任意状态（含已发布）可点；
- 版本号（versionNo）手动输入、必填，**不自动 +1**；同计划内唯一（沿用现有唯一约束，重复时前端提示错误）；
- 变更说明（changeNote）必填；
- 来源 kind 沿用两值：`MANUAL`（新增版本）/ `PUBLISH`（发布同步创建）；
- 版本创建即冻结当时正文快照（snapshotBody + 场景 + 摘要），历史版本不可变。

「发布」在状态流转之外**同步做一次新增版本操作**（kind=PUBLISH），版本号同样在发布弹窗手动输入——发布不是一种特殊的版本机制，只是"流转到终态 + 顺手打一个版本"。

### 3.4 revision（内部计数器，不外露）

每次正文保存自动 +1 的计数器**保留**，唯一用途是并发编辑冲突检测（baseRevision 不一致返回 409 + serverMarkdown，三选一解决，现状不变）。约束：**任何 UI 不再展示 revision 数值**，冲突提示话术改为"他人已修改此文档"，用户可见的概念只有「版本」。

## 4. 状态流转与动作

### 4.1 迁移表（全部迁移，无其他）

| # | 动作 | API | 起始状态 | 目标状态 | 说明 |
|---|---|---|---|---|---|
| 1 | 提交评审 | `POST /task-plans/{id}/submit` | PLANNING | IN_REVIEW | 可附评审备注 |
| 2 | 评审通过 | `POST /task-plans/{id}/approve` | IN_REVIEW | EXECUTING | 原 start-execution 并入，通过即进入执行 |
| 3 | 执行完成 | `POST /task-plans/{id}/finish-execution`（新增） | EXECUTING | REPORTING | 人工确认，非自动 |
| 4 | 发布 | `POST /task-plans/{id}/publish` | REPORTING | PUBLISHED | 总体结论必填 + 同步创建 kind=PUBLISH 版本 |

**无任何回退迁移。** 已发布为终态，但不冻结：编辑、新增版本照常。

### 4.2 评审循环（无驳回后的闭环）

评审有异议时不改状态：直接修改文档（编辑权限任意状态开放，见 §10）+ 批注沟通，改到通过为止。「评审通过」是评审中的唯一出口，不需要"重新提交评审"。

### 4.3 软门禁（二次确认，不阻断）

原则：**前端判断 + 确认弹窗，后端不再因执行活动状态拒绝流转**。

| 触发点 | 条件 | 表现 |
|---|---|---|
| 执行完成 | 存在未终态（QUEUED/RUNNING/STOPPING）的场景执行 | 弹窗："还有 N 个场景未完成，确认执行完成？" |
| 发布 | 同上 | 弹窗同款；确认后正常发布，快照摘要取各场景最近一次终态执行（现状逻辑已兼容） |

后端相应删除 `publish` 中的 `hasActiveExecution` 409 拦截。**保留的硬校验**仅两项：发布必须填写总体结论（内容完整性要求，非流程限制）；非法状态下的动作调用返回 `PLAN_STATE` 409（正常前端不会触发，防御性保留）。

### 4.4 非状态机动作

| 动作 | 可用状态 | 变化 |
|---|---|---|
| 编辑文档 / 行内编辑 / AI 润色 | 任意状态（含已发布） | 不变（已放开），发布后不再冻结 |
| 批注 / 回复 / 解决 / 重开 | 任意状态 | 原"限草稿/评审"限制删除 |
| 新增版本 | 任意状态（含已发布） | 原「发布版本」入口改名并提为全局 |
| 发起执行（场景压测） | EXECUTING、REPORTING | REPORTING 支持复测；原 PUBLISH 阶段禁止逻辑随冻结概念一并删除 |
| 删除计划 | 任意状态 | 维持现状 |
| 分享 | PUBLISHED | 门槛由 frozen 改为终态判定，行为等价；语义细化延后 |

### 4.5 执行联动（状态与执行生命周期解耦）

- 执行生命周期（QUEUED → RUNNING → SUCCESS/FAILED/INTERRUPTED，STOPPING → CANCELLED）**不再回写计划状态**：删除 `onExecutionStarted`/`onExecutionTerminal`/`correctExecutionState` 中的全部 `transitionTo` 调用；
- 执行记录回填保留且时机不变：**每次执行到达终态即回填正文**（幂等标记 `<!-- backfill:execution:{id} -->`，revision+1），非发布前回填。现状回填目标为「八、场景设计」各场景块的执行记录小节；产品目标位置为「测试方法」章节对应场景——**归属随章节重设计确定（延后项）**；
- 环境检查（precheck）维持现状：启用时首次发起执行自动运行、失败阻断、可跳过留痕。形态调整延后。

## 5. 数据迁移（Flyway V7）

`task_plans` 表：删除 `phase` 列，`status` 列按下表重写为新枚举值：

| 存量 phase·status | 新 status |
|---|---|
| DRAFT·DRAFT | PLANNING |
| REVIEW·PENDING / REVIEW·IN_REVIEW | IN_REVIEW |
| REVIEW·APPROVED | EXECUTING |
| EXECUTION·PENDING / EXECUTION·RUNNING | EXECUTING |
| EXECUTION·DONE | REPORTING |
| REPORT·DONE / REPORT·GENERATING | REPORTING |
| PUBLISH·PUBLISHED | PUBLISHED |

说明：REVIEW·APPROVED 语义即"评审已通过、等待执行"，直接映射 EXECUTING；EXECUTION·DONE 即"执行完毕待出报告"，映射 REPORTING。`plan_versions.plan_phase` 为历史快照元数据，**保留原值不迁移**（版本记录不可变），新记录写入新状态字符串。

## 6. API 变更

| 端点 | 变更 |
|---|---|
| `POST /task-plans/{id}/start-review` | **删除**（提交评审直达评审中） |
| `POST /task-plans/{id}/reject` | **删除** |
| `POST /task-plans/{id}/withdraw` | **删除** |
| `POST /task-plans/{id}/back-to-draft` | **删除** |
| `POST /task-plans/{id}/new-revision` | **删除**（概念由「新增版本」取代） |
| `POST /task-plans/{id}/submit` | 门槛改为 status=PLANNING |
| `POST /task-plans/{id}/approve` | 门槛改为 status=IN_REVIEW；目标改为 EXECUTING |
| `POST /task-plans/{id}/finish-execution` | **新增**；门槛 status=EXECUTING |
| `POST /task-plans/{id}/publish` | 门槛改为 status=REPORTING；删除活跃执行 409 拦截；其余（总体结论必填、判等表/总览回填、快照、版本）本期保持现状 |
| `POST /task-plans/{id}/versions` | 不变（前端入口改为全局「新增版本」） |
| `PUT /task-plans/{id}/document`、批注、precheck、模板、快照查询 | 不变 |
| 执行发起门禁 `assertExecutionAllowed` | status ∈ {EXECUTING, REPORTING}；脚本关联、precheck 逻辑不变 |

MCP 工具面（`mcp/plan`）同步：流转动作白名单更新为 提交评审/评审通过/执行完成/发布/新增版本，删除废弃动作；错误词表维持 `PLAN_STATE`/`PLAN_INVALID`。

## 7. 后端代码影响

| 文件 | 变更 |
|---|---|
| `PlanStatus.java` | 重写为 5 值枚举 |
| `PlanPhase.java` | 删除；`PersistentTaskPlanRecord` 移除 phase 字段及 `transitionTo(phase,status)` 签名（改单参 `transitionTo(status)`） |
| `PlanAccess.java` | 重写：删除角色维度（ownerLike/memberLike），输出"status → 可用动作集"映射；动作键收敛为 EDIT/SUBMIT/APPROVE/FINISH/PUBLISH/NEW_VERSION/EXECUTE/COMMENT/SHARE/DELETE |
| `PlanWorkflowService.java` | 删除 startReview/reject/withdraw/backToDraft/newRevision；新增 finishExecution；publish 删活跃执行拦截、门槛改 REPORTING；删除 onExecutionStarted/onExecutionTerminal/correctExecutionState 中的状态迁移（保留回填与摘要构建） |
| `PlanDocumentService.java` | `correctExecutionState` 删除或改为空实现 |
| `PlanDocumentController.java` | 五个端点删除、一个新增 |
| `PersistentPlanVersionRecord` | 不变（planPhase 保留历史语义） |
| Flyway `V7__plan_status_simplify.sql` | §5 迁移 |
| `CONTEXT.md` | TaskPlan 词条更新（单状态、版本解耦、无回退） |

## 8. 前端代码影响

| 模块 | 变更 |
|---|---|
| 计划详情页头部 | 状态徽标 + 五段阶段步骤条（由 status 推导，新组件） |
| 操作按钮区 | 显隐规则 = §4.1/§4.4 映射：提交评审（PLANNING）、评审通过（IN_REVIEW）、执行完成（EXECUTING）、发布（REPORTING）、新增版本（全局常驻）；删除驳回/撤回/退回草稿/新修订入口 |
| 二次确认弹窗 | 执行完成/发布前查询活跃执行数，>0 时确认 |
| 版本 Tab | 「发布版本」按钮更名「新增版本」；发布弹窗含版本号/变更说明/总体结论 |
| 评审工作台 | 保留批注全套；删除驳回/撤回/开始评审动作，仅留「评审通过」 |
| 冲突提示 | 移除 revision 数值展示，话术改"他人已修改" |
| 路由/Tab | 报告 Tab 保持已删除状态（报告=版本修订记录，不回退该决策） |

## 9. 测试策略

- **后端单测**：4 条合法迁移 + 非法起始状态 409；发布总体结论必填、版本号重复拒绝、kind=PUBLISH 记录内容断言；新增版本任意状态可用（含 PUBLISHED）；执行终态回填正文且不改状态；`assertExecutionAllowed` 双状态放行；V7 迁移映射（每种存量组合一条用例）。
- **`PlanAccess` 矩阵测试**：重写为 status × 动作断言（无角色维度）。
- **前端测试**：按钮显隐映射、步骤条推导、二次确认弹窗触发条件。
- **MCP**：`PlanToolsTest` 同步动作清单。

## 10. 权限（本期从简）

所有计划动作（含四个流转按钮、新增版本、删除）的**角色维度全部移除**，门槛统一为"项目成员"（非项目成员仍拒绝）。ownerLike/memberLike 概念从代码中消失；细粒度权限（谁能评审通过、谁能发布）留待权限管理专项，届时在 `PlanAccess` 单点恢复角色维度。

## 11. 错误处理

- 非法状态调用流转端点：`PLAN_STATE` 409，附当前状态与允许动作（简化后允许动作最多 1 个流转 + 若干非流转动作）；
- 版本号重复：`PLAN_INVALID` 409，提示已存在的版本号；
- 并发编辑：revision 409 + serverMarkdown 三选一，不变；
- 已发布后的编辑/新增版本正常放行，无特殊错误路径。

## 12. 测试与验收要点

- 存量库升级后：原草稿计划可提交评审；原评审通过/执行中计划可点执行完成；原执行完成/报告计划可发布；原已发布计划可新增版本、可再编辑；
- 发布后文档编辑 → 再新增版本 → 分享链接仍可打开（内容维持现状=终态后分享当前正文，语义细化延后）；
- 全流程按钮驱动走通：创建 → 提交评审 → 通过 → （跑一个场景）执行完成（有未完成场景时出现确认弹窗）→ 发布（填版本号）→ 已发布。

## 13. 关键决策记录（2026-09-11 讨论）

1. **状态与阶段合并**：数据只存单一 status，阶段为展示推导。——避免双枚举矩阵。
2. **无回退**：驳回/撤回/退回草稿全部删除；评审不通过直接改文档直到通过，不在状态上体现；"不做严格流程限制，否则很不习惯"。
3. **新增版本与状态无关**：任意状态可用，含终态后；发布只是"流转 + 同步新增版本"，不是一个独立版本机制。
4. **软门禁**：未完成场景等条件只做告警二次确认，不做硬限制。
5. **版本号手输**：不自动 +1；revision 计数器退化为内部冲突保护，UI 不暴露。
6. **权限暂不限制**：之后做权限管理时再细化。
7. **阶段命名**：第一阶段由"策略"改为"计划"，消除与压测策略的歧义。
8. **分享与precheck**：形态延后考虑；本期行为维持现状等价迁移。

## 14. 延后项（Out of Scope）

| 项 | 现状/本期处理 | 延后内容 |
|---|---|---|
| 权限矩阵 | 全部放开到项目成员 | 角色细分、审批人指定 |
| 分享语义 | 仅 PUBLISHED 可分享，内容为发布快照（现状） | 分享跟快照还是最新正文、有效期策略 |
| precheck | 首次执行自动检查、失败阻断可跳过（现状） | 是否软化、检查项配置化 |
| 执行记录回填位置 | 执行终态回填「八、场景设计」场景块 | 「测试方法」章节重设计及回填形态 |
| 报告内容生成 | 发布时回填执行结果总览/判等表（现状保留） | 报告章节结构重设计、生成时机与模板 |
