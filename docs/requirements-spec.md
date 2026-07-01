# 性能测试平台 - 功能需求规格说明书

> 版本：v1.0 | 更新日期：2026-07-01
>
> 本文档完整描述平台的功能需求，与代码实现保持同步。各需求项标注当前实现状态。

---

## 目录

1. [产品概述](#1-产品概述)
2. [目标用户](#2-目标用户)
3. [核心闭环](#3-核心闭环)
4. [模块需求](#4-模块需求)
   - [4.1 系统管理](#41-系统管理)
   - [4.2 项目管理](#42-项目管理)
   - [4.3 脚本管理](#43-脚本管理)
   - [4.4 测试执行](#44-测试执行)
   - [4.5 报告管理](#45-报告管理)
   - [4.6 监控采集](#46-监控采集)
   - [4.7 造数工厂](#47-造数工厂)
   - [4.8 函数库](#48-函数库)
   - [4.9 辅助脚本](#49-辅助脚本)
   - [4.10 Git 日志 AI 分析](#410-git-日志-ai-分析)
   - [4.11 分布式执行](#411-分布式执行)
5. [系统架构](#5-系统架构)
6. [数据库设计](#6-数据库设计)
7. [版本路线图](#7-版本路线图)
8. [附录：实现状态总览](#附录实现状态总览)

---

## 1. 产品概述

### 1.1 产品定位

性能测试平台面向内部测试团队和项目研发团队，提供从性能脚本管理、测试执行、过程状态查看到测试报告生成的统一工作台。长期目标是形成可沉淀、可追溯、可扩展的性能工程平台。

### 1.2 产品愿景

将当前"以 JMeter 为中心的单引擎平台"演进为"**脚本多源创建 + 可视化编辑 + XML 双模切换 + 多引擎执行**"的专业性能测试平台。

```
                        脚本创建
        ┌──────────────────┼──────────────────┐
        ▼                   ▼                   ▼
   JMX 导入解析        AI 生成脚本         手动创建脚本
        │                   │                   │
        └──────────────────┼──────────────────┘
                           ▼
              ┌─────────────────────┐
              │  可视化编辑器（步骤树） │
              │        ↕ 切换        │
              │  XML 源码编辑器      │
              └────────┬────────────┘
                       │
              ┌────────┴────────┐
              ▼                 ▼
         导出 .jmx          导出 k6 / Gatling
              │                 │
              └────────┬────────┘
                       ▼
              ┌─────────────────┐
              │  远程 Agent 执行  │
              └─────────────────┘
```

### 1.3 不做的事

- 不追求覆盖 JMeter 功能全集
- 不替代 JMeter GUI 所有能力
- 不实现企业级组织架构同步和复杂审批流

---

## 2. 目标用户

| 角色 | 主要诉求 | 典型操作 |
|------|----------|----------|
| 测试负责人 | 统一管理项目和测试资产，查看报告结论 | 创建项目、分配成员、查看报告 |
| 性能测试工程师 | 上传脚本、配置参数、发起执行、分析结果 | 管理 JMX、提交任务、下载报告 |
| 研发工程师 | 查看压测结果和错误信息，辅助定位问题 | 查看执行结果、下载报告 |
| 平台管理员 | 管理用户、角色、系统配置和运行风险 | 用户管理、权限配置、系统配置 |

---

## 3. 核心闭环

```text
创建项目 → 上传/创建 JMX 脚本 → 可视化编辑脚本步骤
    → 创建测试计划 → 配置测试场景（绑定脚本+执行参数）
    → 提交执行 → 实时查看状态和指标 → 查看聚合报告
    → （可选）关联监控目标查看资源指标
```

---

## 4. 模块需求

> 状态标识：✅ 已实现 &nbsp;&nbsp; 🔨 部分实现 &nbsp;&nbsp; 📋 未实现

---

### 4.1 系统管理

**模块定位**：负责平台登录、用户身份和基础审计。不负责企业组织同步和多租户隔离。

**职责边界**：

| 负责 | 不负责 |
|------|--------|
| 用户登录、退出、当前用户信息 | 企业统一身份同步 |
| 平台角色定义 | 复杂组织树和岗位体系 |
| 基础认证拦截 | 数据脱敏审批流 |
| 创建人、更新时间等审计字段 | 完整审计报表和风控模型 |

#### REQ-SYS-001 用户登录 ✅

**用户故事**：作为平台用户，我希望通过用户名密码登录平台，避免匿名访问测试资产。

**功能说明**：
1. 支持用户名密码登录
2. MVP 内置三类角色：ADMIN（管理员）、PROJECT_OWNER（项目负责人）、PROJECT_MEMBER（项目成员）
3. 登录后可查看当前用户信息和角色
4. 业务接口识别当前用户，写操作记录审计字段

**验收标准**：
1. 合法用户可以登录平台
2. 错误密码无法登录
3. 未登录用户无法访问项目、脚本、任务页面

**实现要点**：
- 后端：`POST /api/auth/login`，`AuthController` + `PersistentAuthenticationService`
- 前端：`AuthScreen.vue` + `useAuth` composable，用户信息持久化到 localStorage
- 当前密码明文存储（开发环境），生产需改为 BCrypt
- 写操作通过 `X-User` 请求头传递操作者身份

#### REQ-SYS-002 基础审计字段 🔨

**用户故事**：作为平台管理员，我希望关键数据记录创建人和更新时间，便于排查问题。

**功能说明**：
1. 核心表统一记录创建人、创建时间
2. 关键操作记录操作人：项目归档、脚本上传、任务提交、任务取消
3. 审计字段不允许普通用户手工修改

**验收标准**：
1. 新建项目、上传脚本、提交任务后能看到创建人和创建时间 ✅
2. 取消任务后能看到取消操作人和取消时间 ✅
3. 用户无法通过普通编辑接口篡改审计字段 🔨

**未实现部分**：
- 缺少独立的 `operation_log` 操作日志表
- 缺少 `updated_by` 字段（当前只有 `created_by` + `created_at` + `updated_at`）
- 系统配置管理界面（当前仅有 Mock 数据）

#### REQ-SYS-003 系统配置管理 📋

**功能说明**：平台级配置项管理，包括执行并发上限、文件大小限制、日志保留周期等。

**当前状态**：配置项硬编码在 `application.yml`，无管理界面。`ModuleMockController` 提供 Mock API。

---

### 4.2 项目管理

**模块定位**：项目是平台资产归属入口。脚本、执行任务、报告、监控目标、造数模板和函数资产都必须挂靠到项目。

**职责边界**：

| 负责 | 不负责 |
|------|--------|
| 项目创建、编辑、归档、恢复 | 用户账号生命周期 |
| 项目成员管理 | 企业组织同步 |
| 项目资产访问入口 | 脚本文件解析、任务调度、报告生成 |

#### REQ-PROJ-001 创建和维护项目 ✅

**用户故事**：作为测试负责人，我希望为每个被测系统创建独立项目，集中管理脚本、任务和报告。

**功能说明**：
1. 支持创建、编辑、查询、归档和恢复项目
2. 项目字段：编码（唯一）、名称、描述、负责人、状态、创建时间
3. 归档项目默认不出现在新建任务选择列表中
4. 项目不做物理删除

**验收标准**：
1. 用户可以创建项目，并在项目列表中看到 ✅
2. 重复项目编码无法保存 ✅
3. 归档项目后不出现在活跃项目列表中 ✅

**实现要点**：
- 后端：`ProjectController` 完整 CRUD + archive/restore
- 前端：`ProjectListView.vue`（列表+搜索+筛选）、`ProjectFormDialog.vue`（创建/编辑）
- 状态：ACTIVE / ARCHIVED，枚举 `ProjectStatus`
- 表：`projects`（唯一约束 `code`）

#### REQ-PROJ-002 项目成员管理 ✅

**用户故事**：作为测试负责人，我希望控制项目成员范围，避免无关用户修改测试资产。

**功能说明**：
1. 支持为项目添加和移除成员
2. 项目角色：OWNER（负责人）、MEMBER（成员）
3. 管理员可管理所有项目
4. 项目负责人可管理自己负责的项目

**验收标准**：
1. 项目负责人可以添加项目成员 ✅
2. 非项目成员无法查看该项目资产 ✅
3. 管理员可以查看和维护所有项目 ✅

**实现要点**：
- 后端：`ProjectController` 成员子资源 `/api/projects/{id}/members`
- 前端：`MemberDialog.vue`，项目详情页内联成员管理
- 表：`project_members`（联合唯一约束 `project_id + username`）

#### REQ-PROJ-003 项目仪表盘 ✅

**用户故事**：作为项目成员，我希望进入项目时看到资产总览和快捷入口。

**功能说明**：
1. 项目概览展示：脚本数量、待执行场景数、监控目标数、报告数
2. 资产流程引导卡片：解析脚本 → 配置场景 → 绑定监控 → 生成报告
3. 最近脚本列表

**实现要点**：
- 前端：`ProjectOverview.vue`，数据来自 `useWorkspace` composable
- 后端：`DashboardController` 提供全局仪表盘 `/api/dashboard/summary`

---

### 4.3 脚本管理

**模块定位**：负责 JMeter JMX 文件的上传、存储、版本记录、结构化解析和可视化编辑。只管理可执行资产，不直接启动压测任务。

**职责边界**：

| 负责 | 不负责 |
|------|--------|
| JMX 文件上传和校验 | JMeter 进程启动 |
| 脚本版本追加和追溯 | 执行队列调度 |
| 可视化步骤编辑 | 报告指标聚合 |
| 脚本文件存储路径管理 | Git 仓库同步 |

#### REQ-SCRIPT-001 上传 JMX 脚本 ✅

**用户故事**：作为性能测试工程师，我希望上传 JMeter JMX 文件，作为后续执行任务的脚本来源。

**功能说明**：
1. 支持在项目下上传 `.jmx` 文件
2. 上传后生成脚本记录和版本号
3. 记录文件名、上传人、上传时间、存储路径
4. 同一脚本再次上传时生成新版本，不覆盖历史版本

**验收标准**：
1. 用户可以上传合法 JMX 文件 ✅
2. 同一脚本上传第二次后版本号递增 ✅
3. 非 JMX 文件无法上传 ✅

**实现要点**：
- 后端：`ScriptController.uploadScript()` multipart 上传
- 表：`script_versions`（`project_id` + `version_no` 自增）
- 文件存储：`storage/scripts/{projectId}/`

#### REQ-SCRIPT-002 脚本版本管理 ✅

**用户故事**：作为性能测试工程师，我希望查看和管理脚本的所有历史版本。

**功能说明**：
1. 脚本版本列表，展示版本号、文件名、上传人、上传时间
2. 支持删除指定版本
3. 支持下载原始 JMX 文件

**实现要点**：
- 后端：`GET /api/projects/{id}/scripts`、`DELETE /api/projects/{id}/scripts/{versionId}`
- 前端：`ScriptWorkspace.vue` 版本历史面板

#### REQ-SCRIPT-003 可视化步骤编辑器 ✅

**用户故事**：作为性能测试工程师，我希望在平台上可视化管理脚本步骤，减少直接修改 JMX 文件。

**功能说明**：
1. 以步骤树展示脚本结构（线程组 → HTTP请求 → 断言/配置）
2. 支持拖拽排序步骤
3. 支持创建、编辑、删除步骤
4. 支持的步骤类型：

| 步骤类型 | 说明 | 状态 |
|---------|------|------|
| ThreadGroup | 线程组（计数/时长/阶梯模式） | ✅ |
| HttpRequest | HTTP 请求（方法/URL/参数/头/体） | ✅ |
| ResponseAssertion | 响应断言（状态码/响应体/头） | ✅ |
| JsonAssertion | JSONPath 断言 | ✅ |
| CsvDataSet | CSV 数据文件 | ✅ |
| UserParameters | 用户参数 | ✅ |
| HeaderManager | HTTP 头管理器 | ✅ |

**验收标准**：
1. 用户可通过步骤树查看脚本结构 ✅
2. 支持拖拽调整步骤顺序 ✅
3. 编辑后保存，重新打开保持一致 ✅

**实现要点**：
- 后端：JMX 解析（`JmeterScriptParser`）+ 渲染（`JmeterScriptRenderer`）
- 前端：`ScriptEditorPage.vue` 全页编辑器，`StepSidebar.vue` + `StepDetail.vue` 布局
- 拖拽使用 HTML5 Drag & Drop API
- 步骤层级限制：最多 3 层（root → child → grandchild）

#### REQ-SCRIPT-004 HTTP 调试 ✅

**用户故事**：作为性能测试工程师，我希望在编辑器中直接测试 HTTP 请求，验证配置正确性。

**功能说明**：
1. 在 HTTP 请求步骤编辑时，点击"调试"发送真实请求
2. 展示响应状态码、响应头、响应体、耗时

**实现要点**：
- 后端：`HttpDebugController.post /api/http-debug`，使用 `java.net.http.HttpClient`
- 前端：`HttpDebugDialog.vue` 展示调试结果

#### REQ-SCRIPT-005 步骤导入 ✅

**功能说明**：
1. 支持从 JMX XML 片段导入步骤
2. 支持从 curl 命令解析并生成 HTTP 请求步骤

**实现要点**：
- 前端：`StepImportDialog.vue`

#### REQ-SCRIPT-006 脚本参数配置 ✅

**用户故事**：作为性能测试工程师，我希望在执行前配置线程数、循环次数等参数。

**功能说明**：
1. 支持为脚本版本维护默认执行参数
2. 参数包括：线程数、循环次数、持续时间、Ramp-Up、目标环境、JMeter 属性扩展项
3. 执行时可覆盖默认参数

**实现要点**：
- 前端：`ScriptParamDrawer.vue` 侧边抽屉

#### REQ-SCRIPT-007 手动创建空白脚本 ✅

**功能说明**：从空步骤树开始，逐条添加步骤构建脚本。

**实现要点**：
- 前端：`ScriptCreateDialog.vue` 创建空白脚本
- 后端：`POST /api/projects/{id}/scripts`（JSON body 模式）

#### REQ-SCRIPT-008 XML 源码编辑器 🔨

**功能说明**：可视化编辑器和原始 XML 编辑器并存，通过 Tab 切换。高级用户可直接编辑 JMX 源码。

**实现要点**：
- 前端：`CodeEditor.vue` 已集成 CodeMirror（XML 语法高亮）
- 后端：`ScriptContent` API 已提供原始内容读写
- 缺少：XML 编辑后的无损回写（当前 Parser→Renderer 可能丢失不支持的元素）

#### REQ-SCRIPT-009 AI 生成脚本 📋

**功能说明**：
1. 从 OpenAPI/Swagger JSON 解析接口定义，自动生成脚本步骤
2. 从自然语言描述生成脚本

**未实现**。

#### REQ-SCRIPT-010 多引擎导出 📋

**功能说明**：将 JMX 脚本导出为 k6 JavaScript 或 Gatling Scala 脚本。

**未实现**。

---

### 4.4 测试执行

**模块定位**：负责把脚本版本和执行配置转化为一次可追踪的压测运行过程。使用单机队列，通过 `TestExecutor` 抽象支持后续演进到分布式执行。

**职责边界**：

| 负责 | 不负责 |
|------|--------|
| 测试计划与场景管理 | 脚本文件上传 |
| 执行配置快照 | 报告页面展示 |
| JMeter 启动、停止、日志归档 | 指标图表渲染 |
| 执行状态流转和 SSE 实时推送 | 监控指标采集 |

#### REQ-EXEC-001 测试计划管理 ✅

**用户故事**：作为性能测试工程师，我希望创建测试计划来组织多个测试场景。

**功能说明**：
1. 在项目下创建、编辑、删除测试计划
2. 计划字段：名称、备注、默认控制器节点、默认工作节点、默认监控目标
3. 计划作为场景的容器

**验收标准**：
1. 用户可以创建测试计划 ✅
2. 计划可配置默认执行资源和监控绑定 ✅

**实现要点**：
- 后端：`TaskPlanController` 计划 CRUD
- 前端：`TaskPlanList.vue` + `TaskPlanDialog.vue`
- 表：`task_plans`

#### REQ-EXEC-002 测试场景配置 ✅

**用户故事**：作为性能测试工程师，我希望在计划下创建场景，绑定脚本和配置执行参数。

**功能说明**：
1. 场景绑定：脚本版本、线程数、Ramp-Up、持续时间、循环次数
2. 可覆盖计划的默认控制器/工作节点/监控目标
3. 场景配置 JSON 快照（JMeter 扩展属性）

**验收标准**：
1. 用户可以创建场景并绑定脚本版本 ✅
2. 场景参数独立于脚本默认参数 ✅

**实现要点**：
- 后端：`TaskPlanController` 场景子资源
- 前端：`TaskPlanDetail.vue` + `ScenarioDialog.vue`
- 表：`task_scenarios`

#### REQ-EXEC-003 提交执行 ✅

**用户故事**：作为性能测试工程师，我希望能一键触发场景执行。

**功能说明**：
1. 支持从场景直接触发执行
2. 支持从脚本列表一键执行（自动创建计划+场景+执行）
3. 每次执行生成独立执行记录，配置固化为快照
4. 同一时刻默认只运行有限数量任务（当前配置为 1）

**验收标准**：
1. 用户可以提交执行并看到 QUEUED/RUNNING 状态 ✅
2. 执行配置在提交后固化，后续参数变化不影响该执行 ✅
3. JMeter 启动失败时展示失败原因 ✅

**实现要点**：
- 后端：`POST /api/scenarios/{id}/executions`，`TestExecutionService`
- 前端：`ExecuteConfirmDialog.vue`，支持自定义执行名称
- 表：`scenario_executions`

#### REQ-EXEC-004 执行状态与日志 ✅

**用户故事**：作为性能测试工程师，我希望实时查看执行状态和日志。

**功能说明**：
1. 状态机：QUEUED → RUNNING → SUCCESS/FAILED/INTERRUPTED，RUNNING → STOPPING → CANCELLED
2. 支持查看 JMeter 执行日志
3. 支持 SSE 实时推送执行状态
4. 平台重启后 RUNNING 状态任务标记为 INTERRUPTED

**验收标准**：
1. 用户可以按状态查看执行列表 ✅
2. 运行中执行展示开始时间、运行时长 ✅
3. 平台重启后运行中任务不会长期停留在 RUNNING 状态 ✅

**实现要点**：
- 实时流：`GET /api/executions/{id}/stream` SSE 端点
- 前端：`useTaskPlans` composable 中 5 秒轮询 + SSE 连接

#### REQ-EXEC-005 停止执行 ✅

**用户故事**：作为性能测试工程师，我希望停止误提交或不需要的压测任务。

**功能说明**：
1. 支持停止运行中的执行
2. 平台尝试终止对应 JMeter 进程

**验收标准**：
1. 运行中执行可被停止 ✅
2. 停止后不再产生新采样数据 ✅

**实现要点**：
- 后端：`POST /api/executions/{id}/stop`
- JMeter 进程通过 `Process.destroy()` 终止

#### REQ-EXEC-006 实时采样与指标 ✅

**用户故事**：作为性能测试工程师，我希望在压测进行中实时查看吞吐量、响应时间、错误率等指标。

**功能说明**：
1. SSE 实时推送采样数据和指标刻度
2. 实时图表：吞吐量、响应时间（avg/p95）、错误率随时间变化
3. 聚合报告：样本总数、吞吐量、平均RT、P95、错误率
4. 按标签（采样器名称）分组的明细统计表

**验收标准**：
1. 运行中执行可看到实时指标图表 ✅
2. 图表随时间自动更新 ✅

**实现要点**：
- 后端：`GET /api/executions/{id}/samples/stream` SSE，聚合计算在 `JmeterResultParser`
- 前端：`ExecutionDetailView.vue` + `TaskMonitoringCharts.vue`（ECharts）
- 表：`execution_metric_series`（时序指标）、`aggregate_report`（聚合报告）

#### REQ-EXEC-007 采样浏览器 ✅

**用户故事**：作为性能测试工程师，我希望查看单个请求的详细信息（请求/响应/断言结果）。

**功能说明**：
1. 分页查看采样列表，支持按标签、响应码、成功/失败筛选
2. 点击单个采样查看：请求头、请求体、响应头、响应体、断言结果、失败信息
3. 失败样本自动清洗（`FailureSampleNormalizer`）

**验收标准**：
1. 用户可以分页浏览采样数据 ✅
2. 可查看单个采样的完整请求/响应 ✅

**实现要点**：
- 后端：`GET /api/executions/{id}/samples` + `GET /api/executions/{id}/samples/{sampleId}`
- 失败样本存储在 SQLite 数据库（`FailureSampleStore`）
- 前端：`ExecutionDetailView.vue` 采样表格 + 详情面板

#### REQ-EXEC-008 执行历史管理 ✅

**功能说明**：
1. 场景下执行历史列表
2. 支持单个删除和批量删除
3. 历史执行下拉切换，快速对比不同执行

**实现要点**：
- 后端：`DELETE /api/executions/batch`
- 前端：执行详情页历史下拉框 + 批量删除

---

### 4.5 报告管理

**模块定位**：负责把执行结果解析为可交付的测试报告，提供项目内查看和下载能力。

**职责边界**：

| 负责 | 不负责 |
|------|--------|
| JMeter 结果文件解析 | JMeter 执行和停止 |
| 聚合指标计算 | 原始脚本版本管理 |
| 报告展示 | 监控 Agent 采集 |

#### REQ-REPORT-001 聚合报告展示 ✅

**用户故事**：作为性能测试工程师，我希望执行结束后看到聚合统计数据。

**功能说明**：
1. 执行完成后展示：样本总数、吞吐量、平均RT、P95、错误率
2. 按采样器标签分组的明细统计：平均值、中位数、P90/P95/P99、最小/最大、错误率、吞吐量
3. 报告图表快照（PNG）
4. 准确性标签：final / final partial / live（区分执行中和已完成数据）

**验收标准**：
1. 已结束任务的执行详情页展示聚合统计 ✅
2. 包括按标签分组的明细 ✅
3. 报告数据持久化到数据库 ✅

**实现要点**：
- 表：`aggregate_report`（`execution_id` 唯一约束）
- 前端：`ExecutionDetailView.vue` 聚合报告区域
- 聚合计算在 `ReportAggregator` 中完成

#### REQ-REPORT-002 报告生成与下载 📋

**用户故事**：作为性能测试工程师，我希望生成独立的测试报告文件用于交付。

**功能说明**：
1. 基于聚合数据生成 Markdown 格式报告
2. 基于聚合数据生成 HTML 格式报告
3. 支持报告下载

**当前状态**：未实现。`ModuleMockController` 提供 Mock API。前端 Reports 标签页为占位状态。

#### REQ-REPORT-003 报告对比 📋

**功能说明**：支持选择两次执行进行对比，展示指标差异。

**当前状态**：未实现。Mock API 存在（`POST /api/reports/compare`）。

#### REQ-REPORT-004 报告分享与追溯 📋

**功能说明**：
1. 报告与项目、执行记录、脚本版本关联
2. 报告链接访问
3. 从报告跳转回原始执行详情

**当前状态**：未实现。

---

### 4.6 监控采集

**模块定位**：用于把压测结果和被测资源指标关联起来，帮助定位性能瓶颈。

**职责边界**：

| 负责 | 不负责 |
|------|--------|
| 监控目标管理 | 启停 JMeter 任务 |
| CPU、内存、磁盘、网络等指标采集 | 项目成员权限模型 |
| 执行期间指标关联展示 | 原始 JMX 管理 |

#### REQ-MON-001 监控目标管理 ✅

**用户故事**：作为性能测试工程师，我希望配置被测服务器的监控目标，在执行期间查看资源指标。

**功能说明**：
1. 创建、编辑、删除监控目标
2. 目标字段：名称、服务名、主机、端口、Metrics 路径、环境标识
3. 支持 SSH 隧道连接（SSH 用户名/密码/端口）
4. 支持附加标签和监控项配置
5. 启用/禁用目标

**验收标准**：
1. 用户可以创建监控目标 ✅
2. SSH 配置可选择性填写 ✅

**实现要点**：
- 后端：`MonitorTargetController` 完整 CRUD
- 前端：`ProjectMonitoringView.vue` + 新建服务器对话框
- 表：`monitor_target`
- SSH 字段通过 `MonitoringSchemaInitializer` 动态添加

#### REQ-MON-002 监控代理部署 ✅

**用户故事**：作为性能测试工程师，我希望一键部署监控导出器到目标服务器。

**功能说明**：
1. 支持通过 SSH 远程部署 Node Exporter
2. 支持部署应用级导出器：JVM JMX Agent、MySQL Exporter、Redis Exporter、Nginx Exporter、Kafka Exporter
3. 部署结果展示

**验收标准**：
1. 用户可部署监控代理到目标服务器 ✅
2. 部署结果在界面展示 ✅

**实现要点**：
- 后端：`POST /api/monitor-targets/{id}/deploy`，通过 Python remote runner 执行
- 前端：部署结果对话框
- Exporter 二进制已预编译在 `deploy/monitoring/prometheus/`

#### REQ-MON-003 监控目标健康检查 ✅

**功能说明**：支持对监控目标进行可达性检查。

**实现要点**：
- 后端：`POST /api/monitor-targets/{id}/check`
- 前端：健康检查按钮 + 状态展示

#### REQ-MON-004 执行期间资源指标展示 ✅

**用户故事**：作为性能测试工程师，我希望在压测执行期间同时查看被测服务器的 CPU、内存等资源指标。

**功能说明**：
1. 执行详情页展示绑定的监控目标指标
2. 服务端指标：CPU 使用率、内存、磁盘 IO、网络、TCP 连接
3. JVM 指标：堆内存、GC 活动、线程数、CPU
4. 指标以时序图表展示

**验收标准**：
1. 执行详情页可查看关联目标的 CPU/内存等指标 ✅
2. 图表随时间更新 ✅

**实现要点**：
- 后端：`GET /api/executions/{id}/target-monitoring` + `target-monitoring/series`
- Prometheus 查询通过 `PrometheusQueryService`
- 前端：`TargetServerMetricsPanel.vue` + `TargetJvmMetricsPanel.vue`（ECharts）
- 表：`execution_target_metrics_snapshot`、`execution_monitor_binding`

#### REQ-MON-005 监控告警 📋

**功能说明**：资源指标超过阈值时发送告警通知。

**当前状态**：未实现。

#### REQ-MON-006 Word/PDF 报告导出 📋

**功能说明**：支持将包含资源指标的报告导出为 Word 或 PDF 格式。

**当前状态**：未实现。

---

### 4.7 造数工厂

**模块定位** 📋 全部未实现

**功能范围**：
1. 数据模板管理：创建、编辑、删除数据生成模板
2. 数据生成规则：随机值、序列、正则、引用、SQL 查询等
3. 数据预览：在生成前预览样例数据
4. 数据导出：CSV/JSON/SQL 格式导出，可直接绑定到脚本的 CSV Data Set

**当前状态**：前端 Reports/Data 标签页为占位状态。`ModuleMockController` 提供 Mock API：
- `GET /api/projects/{id}/data-templates`
- `POST /api/projects/{id}/data-templates`
- `POST /api/data-templates/{id}/preview`
- `POST /api/data-templates/{id}/generate`

---

### 4.8 函数库

**模块定位** 📋 全部未实现

**功能范围**：
1. Groovy 函数管理：创建、编辑、版本记录
2. 函数调试：在线运行并查看输出
3. 函数与脚本绑定：在 JMeter 脚本中引用函数
4. 函数分类和搜索

**当前状态**：前端 Functions 标签页为占位状态。`ModuleMockController` 提供 Mock API：
- `GET /api/projects/{id}/functions`
- `POST /api/projects/{id}/functions`
- `POST /api/function-versions/{id}/debug`

---

### 4.9 辅助脚本

**模块定位** 📋 全部未实现

**功能范围**：
1. 前置/后置执行脚本：在压测开始前或结束后执行自定义操作
2. 脚本类型：Shell、Python、SQL
3. 执行日志：记录辅助脚本执行过程
4. 失败策略：忽略/重试/中止压测

**当前状态**：仅文档设计，无代码实现。

---

### 4.10 Git 日志 AI 分析

**模块定位** 📋 全部未实现

**功能范围**：
1. Git 仓库关联：项目绑定 Git 仓库
2. 代码变更分析：分析 commit log，识别可能影响性能的变更
3. AI 辅助分析：基于 LLM 分析日志异常、性能退化原因
4. 变更与压测关联：某次压测关联的代码变更范围

**当前状态**：仅文档设计，无代码实现。

---

### 4.11 分布式执行

**模块定位**：突破单机执行能力限制，支持多执行节点注册、远程任务下发和结果回收。

**职责边界**：

| 负责 | 不负责 |
|------|--------|
| 执行节点注册和心跳 | 项目业务建模 |
| 远程 JMeter 任务下发 | 基础报告指标定义 |
| 任务结果回收 | 造数规则管理 |

#### REQ-DIST-001 执行节点管理 ✅

**用户故事**：作为平台管理员，我希望注册和管理 JMeter 分布式执行节点。

**功能说明**：
1. 注册单个节点：名称、主机、SSH 端口、SSH 用户名、SSH 私钥路径、角色、远程工作目录
2. 节点角色：CONTROLLER（控制节点）、WORKER（工作节点）、BOTH
3. 支持批量初始化节点
4. SSH 密钥自动部署
5. 节点健康检查

**验收标准**：
1. 用户可以注册执行节点 ✅
2. 支持 SSH 连接检查和健康状态查询 ✅
3. 节点状态：UNKNOWN → AVAILABLE/OFFLINE ✅

**实现要点**：
- 后端：`ExecutionNodeController` 完整 CRUD + initialize + check
- 前端：`ExecutionNodeView.vue`、`SettingsView.vue` 节点管理标签页
- 表：`execution_nodes`
- 远程操作通过 Python `remote-runner/remote_jmeter_runner/main.py`

#### REQ-DIST-002 远程执行 ✅

**用户故事**：作为性能测试工程师，我希望将压测任务下发给远程执行节点。

**功能说明**：
1. 测试场景可指定控制器节点和工作节点
2. 通过 SSH 将 JMX 脚本下发到远程节点
3. 使用 Docker 容器运行 JMeter（`justb4/jmeter:latest`）
4. 执行结果回传到平台

**验收标准**：
1. 场景可绑定远程执行节点 ✅
2. 执行结果可正常回传 ✅

**实现要点**：
- Python remote runner 命令：`start-run`、`poll-run`、`collect-run`、`stop-run`
- 后端配置：`platform.distributed.runner.*`
- 前端：场景对话框中可选择控制器/工作节点

#### REQ-DIST-003 分布式调度 📋

**功能说明**：
1. 任务分片：将压测任务拆分到多个工作节点
2. 容量调度：根据节点负载分配任务
3. 结果聚合：合并多节点执行结果

**当前状态**：未实现。当前一个场景只能在一个控制器节点执行。

#### REQ-DIST-004 平台运行监控 📋

**功能说明**：
1. 节点资源使用监控
2. 执行队列状态
3. 平台健康告警

**当前状态**：未实现。

---

## 5. 系统架构

### 5.1 实际技术栈

| 分类 | 选型 | 版本 |
|------|------|------|
| 后端框架 | Spring Boot | 3.5.14 |
| JDK | Java | 17 |
| 构建工具 | Gradle | (wrapper) |
| ORM | Spring Data JPA + Hibernate | 6.x |
| 数据库（开发） | H2 (file, MySQL 兼容模式) | - |
| 数据库（生产） | MySQL | 8.0+ |
| 前端框架 | Vue 3 + TypeScript | 3.5.x |
| 构建工具 | Vite | 7.x |
| UI 组件库 | Ant Design Vue | 4.x |
| 图表 | ECharts + vue-echarts | 6.x |
| 代码编辑器 | CodeMirror | 6.x |
| 测试引擎 | Apache JMeter | 5.6.3 |
| 远程执行 | Python + paramiko + Docker | 3.x |
| 监控 | Prometheus | - |

### 5.2 部署架构

```
┌────────────────────────────────────────────────────────────────┐
│                         用户浏览器                              │
│                   http://platform-host:5173                     │
└───────────────────────────┬────────────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────────────┐
│                    Vue 3 前端 (Vite / Nginx)                    │
│                    /api → 代理到后端 8080                       │
└───────────────────────────┬────────────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────────────┐
│              Spring Boot 3 后端 (port 8080)                     │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────────┐  │
│  │  Auth    │ │ Project  │ │  Script  │ │   Execution      │  │
│  │Controller│ │Controller│ │Controller│ │   (TaskPlan +    │  │
│  │          │ │          │ │          │ │    Scenario)     │  │
│  └──────────┘ └──────────┘ └──────────┘ └──────────────────┘  │
│  ┌──────────┐ ┌──────────┐ ┌──────────────────────────────┐   │
│  │Dashboard │ │Execution │ │   Monitoring                  │   │
│  │Controller│ │   Node   │ │  (MonitorTarget + Metrics +   │   │
│  │          │ │Controller│ │   ExecutionMonitorBinding)    │   │
│  └──────────┘ └──────────┘ └──────────────────────────────┘   │
└───────────────────────────┬────────────────────────────────────┘
                            │
              ┌─────────────┼─────────────┐
              ▼             ▼             ▼
        ┌──────────┐ ┌──────────┐ ┌──────────────┐
        │  MySQL   │ │  本地    │ │  Python      │
        │  (H2开发) │ │  文件系统 │ │  Remote      │
        │          │ │ (脚本/日志│ │  Runner (SSH)│
        │          │ │ /结果)   │ │              │
        └──────────┘ └──────────┘ └──────┬───────┘
                                         │
                           ┌─────────────┼─────────────┐
                           ▼             ▼             ▼
                     ┌──────────┐ ┌──────────┐ ┌──────────┐
                     │ 远程节点  │ │ Docker   │ │ JMeter   │
                     │ (SSH)    │ │ Container│ │ CLI      │
                     └──────────┘ └──────────┘ └──────────┘

┌────────────────────────────────────────────────────────────────┐
│              监控栈 (Docker Compose)                            │
│  ┌──────────┐                                                  │
│  │Prometheus│                                                  │
│  │ :9090    │                                                  │
│  └──────────┘                                                  │
│       │                                                        │
│       ▼                                                        │
│  ┌──────────────────────────────────────────┐                  │
│  │  Exporters: Node / MySQL / Redis /       │                  │
│  │  Nginx / Kafka / JMX Java Agent         │                  │
│  └──────────────────────────────────────────┘                  │
└────────────────────────────────────────────────────────────────┘
```

### 5.3 后端分层结构

```
backend/src/main/java/com/yr/perftest/platform/
├── api/            REST 控制器（9个）
│   ├── AuthController            POST /api/auth/login
│   ├── DashboardController       GET  /api/dashboard/summary
│   ├── ProjectController         CRUD /api/projects + members
│   ├── ScriptController          CRUD /api/projects/{id}/scripts
│   ├── TaskPlanController        CRUD plans + scenarios + executions + samples + monitoring
│   ├── ExecutionNodeController   CRUD /api/execution-nodes
│   ├── MonitorTargetController   CRUD /api/monitor-targets
│   ├── HttpDebugController       POST /api/http-debug
│   └── ModuleMockController      模拟 API（造数/函数/报告）
├── config/         平台配置（Security, PlatformService, MonitoringSchema）
├── execution/      JMeter 执行引擎
│   ├── JmeterCommandExecutor     JMeter CLI 封装
│   ├── JmeterResultParser        .jtl 结果解析 + 聚合
│   ├── TestExecutionService      执行调度
│   ├── ReportAggregator          聚合报告计算
│   ├── FailureSampleStore        失败样本 SQLite 存储
│   ├── aggregate/                聚合报告 + 时序指标实体
│   └── distributed/              执行节点实体 + 枚举
├── identity/       用户认证（AuthService, UserAccount 实体）
├── monitoring/     Prometheus 查询 + 监控目标实体
├── project/        项目 + 成员实体
├── script/         JMX Parser/Renderer + 脚本版本实体
└── task/           测试计划 + 场景 + 执行实体
```

### 5.4 核心接口抽象

| 能力 | 接口 | MVP 实现 | 扩展方向 |
|------|------|----------|----------|
| 文件存储 | 直接文件系统操作 | `java.nio.file` | MinIO、OSS |
| 任务调度 | 数据库状态 + Spring `@Async` | `TestExecutionService` | MQ、分布式调度 |
| 测试执行 | JMeter CLI 适配器 | `JmeterCommandExecutor` | Gatling、k6 |
| 报告生成 | 聚合计算 + JSON 序列化 | `ReportAggregator` | 模板引擎、Word/PDF |
| 远程执行 | Python SSH runner | `remote_jmeter_runner/main.py` | Agent 模式、gRPC |

---

## 6. 数据库设计

### 6.1 核心实体关系

```text
user_accounts ──────────────────────────────────────────────
     │
     │ (username 外键)
     ▼
project_members ─────────── projects ─────────────────────
     │                         │
     │                         │ (project_id 外键)
     │                         ▼
     │                  script_versions ─────────────────
     │                         │
     │                         │ (script_version_id 外键)
     │                         ▼
     │                  task_scenarios ──────────────────
     │                    │          │
     │                    │ (plan_id)│ (scenario_id)
     │                    ▼          ▼
     │              task_plans   scenario_executions ────
     │                               │        │        │
     │                               │        │        │
     │                               ▼        ▼        ▼
     │                    aggregate_report  │  execution_monitor_binding
     │                               │      │        │
     │                               │      │        ▼
     │                               │      │  monitor_target
     │                               │      │
     │                               ▼      ▼
     │                    execution_metric_series
     │                               │
     │                               ▼
     │                    execution_target_metrics_snapshot
     │
     ▼
execution_nodes
```

### 6.2 表清单（13 张）

| # | 表名 | 说明 | 主键 | 关键约束 |
|---|------|------|------|----------|
| 1 | `user_accounts` | 用户账户 | `username` (VARCHAR) | - |
| 2 | `projects` | 项目 | `id` (AUTO) | `code` UNIQUE |
| 3 | `project_members` | 项目成员 | `id` (AUTO) | `(project_id, username)` UNIQUE |
| 4 | `script_versions` | 脚本版本 | `id` (AUTO) | - |
| 5 | `task_plans` | 测试计划 | `id` (AUTO) | - |
| 6 | `task_scenarios` | 测试场景 | `id` (AUTO) | - |
| 7 | `scenario_executions` | 场景执行记录 | `id` (AUTO) | - |
| 8 | `execution_nodes` | 执行节点 | `id` (AUTO) | - |
| 9 | `aggregate_report` | 聚合报告 | `id` (AUTO) | `execution_id` UNIQUE |
| 10 | `execution_metric_series` | 执行时序指标 | `id` (AUTO) | INDEX ×2 |
| 11 | `monitor_target` | 监控目标 | `id` (AUTO) | - |
| 12 | `execution_monitor_binding` | 执行监控绑定 | `id` (AUTO) | - |
| 13 | `execution_target_metrics_snapshot` | 目标指标快照 | `id` (AUTO) | `(execution_id, kind)` UNIQUE |

完整建表 SQL 见 `docs/database/mysql-schema.sql`。

### 6.3 数据存储路径

```
storage/
├── perftest.mv.db          H2 数据库文件（开发环境）
├── scripts/{projectId}/    脚本 .jmx 文件
├── executions/             执行目录
│   └── {executionId}/
│       ├── script.jmx      脚本副本
│       ├── result.jtl      执行结果
│       └── jmeter.log      执行日志
└── reports/{executionId}/  报告文件（规划中）
```

---

## 7. 版本路线图

### 已完成（v0.1 - v0.5）

| 版本 | 内容 | 状态 |
|------|------|------|
| v0.1 | Spring Boot + Vue 3 骨架，登录，项目 CRUD，H2 持久化 | ✅ |
| v0.2 | 项目成员管理，JMX 上传，脚本版本管理 | ✅ |
| v0.3 | 可视化步骤编辑器，HTTP 调试，拖拽排序 | ✅ |
| v0.4 | 测试计划+场景，单机执行，状态流转，日志 | ✅ |
| v0.5 | SSE 实时流，时序图表，采样浏览器，失败样本清洗 | ✅ |
| v0.6 | 执行节点管理，SSH 远程执行，Python runner | ✅ |
| v0.7 | 监控目标管理，Prometheus 集成，资源指标展示 | ✅ |
| v0.8 | 聚合报告持久化，监控代理部署，健康检查 | ✅ |

### 进行中/短期（v0.9）

| 需求 | 内容 | 状态 |
|------|------|------|
| REQ-REPORT-002 | Markdown/HTML 报告生成与下载 | 🔨 |
| REQ-SCRIPT-008 | XML 源码编辑器（无损编辑） | 🔨 |
| REQ-SYS-002 | 完整审计字段 + 操作日志 | 🔨 |

### 远期规划（v1.0+）

| 优先级 | 模块 | 内容 |
|--------|------|------|
| 高 | 报告管理 | 报告生成、下载、对比、分享 |
| 高 | 脚本管理 | AI 生成脚本（OpenAPI + 自然语言） |
| 高 | 分布式执行 | 任务分片、容量调度、结果聚合 |
| 中 | 监控采集 | 告警、Word/PDF 导出 |
| 中 | 脚本管理 | 多引擎导出（k6、Gatling） |
| 中 | 脚本管理 | .jmx 文件下载 |
| 低 | 造数工厂 | 数据模板、生成规则、导出 |
| 低 | 函数库 | Groovy 函数管理、调试 |
| 低 | 辅助脚本 | 前置/后置脚本 |
| 低 | Git AI | 代码关联、AI 分析 |

---

## 附录：实现状态总览

| 需求编号 | 需求名称 | 状态 |
|----------|----------|------|
| REQ-SYS-001 | 用户登录 | ✅ |
| REQ-SYS-002 | 基础审计字段 | 🔨 |
| REQ-SYS-003 | 系统配置管理 | 📋 |
| REQ-PROJ-001 | 创建和维护项目 | ✅ |
| REQ-PROJ-002 | 项目成员管理 | ✅ |
| REQ-PROJ-003 | 项目仪表盘 | ✅ |
| REQ-SCRIPT-001 | 上传 JMX 脚本 | ✅ |
| REQ-SCRIPT-002 | 脚本版本管理 | ✅ |
| REQ-SCRIPT-003 | 可视化步骤编辑器 | ✅ |
| REQ-SCRIPT-004 | HTTP 调试 | ✅ |
| REQ-SCRIPT-005 | 步骤导入 | ✅ |
| REQ-SCRIPT-006 | 脚本参数配置 | ✅ |
| REQ-SCRIPT-007 | 手动创建空白脚本 | ✅ |
| REQ-SCRIPT-008 | XML 源码编辑器 | 🔨 |
| REQ-SCRIPT-009 | AI 生成脚本 | 📋 |
| REQ-SCRIPT-010 | 多引擎导出 | 📋 |
| REQ-EXEC-001 | 测试计划管理 | ✅ |
| REQ-EXEC-002 | 测试场景配置 | ✅ |
| REQ-EXEC-003 | 提交执行 | ✅ |
| REQ-EXEC-004 | 执行状态与日志 | ✅ |
| REQ-EXEC-005 | 停止执行 | ✅ |
| REQ-EXEC-006 | 实时采样与指标 | ✅ |
| REQ-EXEC-007 | 采样浏览器 | ✅ |
| REQ-EXEC-008 | 执行历史管理 | ✅ |
| REQ-REPORT-001 | 聚合报告展示 | ✅ |
| REQ-REPORT-002 | 报告生成与下载 | 📋 |
| REQ-REPORT-003 | 报告对比 | 📋 |
| REQ-REPORT-004 | 报告分享与追溯 | 📋 |
| REQ-MON-001 | 监控目标管理 | ✅ |
| REQ-MON-002 | 监控代理部署 | ✅ |
| REQ-MON-003 | 监控目标健康检查 | ✅ |
| REQ-MON-004 | 资源指标展示 | ✅ |
| REQ-MON-005 | 监控告警 | 📋 |
| REQ-MON-006 | Word/PDF 导出 | 📋 |
| - | 造数工厂（全部） | 📋 |
| - | 函数库（全部） | 📋 |
| - | 辅助脚本（全部） | 📋 |
| - | Git 日志 AI 分析（全部） | 📋 |
| REQ-DIST-001 | 执行节点管理 | ✅ |
| REQ-DIST-002 | 远程执行 | ✅ |
| REQ-DIST-003 | 分布式调度 | 📋 |
| REQ-DIST-004 | 平台运行监控 | 📋 |

**统计**：✅ 已实现 25 项 &nbsp;|&nbsp; 🔨 部分实现 2 项 &nbsp;|&nbsp; 📋 未实现 16 项
