# 测试方法章节优化 · 详细设计

日期：2026-09-15
状态：待评审
范围：计划文档「测试方法」章节结构化改造——场景小节实体化、执行记录表（可执行、结果自动回填）、监控证据区（引用执行数据图 + 手动上传截图）

## 1. 背景与目标

现状「测试方法」章节（如 `## 八、测试方法`）是纯 Markdown 文本：场景靠手写 bullet（目标/入口/模型/数据/通过判据），与压测脚本、执行、结果完全脱节；监控证据无处归档。

目标：

1. 每个场景一个小节，由 Scenario 实体同步生成（增删改名自动同步，解决「场景数=0」的组织断裂）。
2. 每场景一张执行记录表：可发起执行（参数就地调整），结果列（样本数/成功率/平均RT/P95/TPS）自动回填，禁止手填。
3. 监控证据：执行数据图（TPS/RT 趋势、CPU/内存）直接复用执行详情页组件以终态数据渲染，零生成成本；另支持手动上传补充截图。
4. 表格行点击跳执行详情页（复用现有 `ExecutionDetailView`），执行发起后自动跳转跟进。

## 2. 关键决策记录（已确认）

| # | 决策 | 结论 |
|---|------|------|
| D1 | 数据模型 | 复用 Scenario + 既有执行链路；表格行 = 执行记录，每次执行追加一行，不覆盖 |
| D2 | 文档存储 | 引用式：执行记录/截图不写入文档 body，独立结构化数据实时拉取，避免 revision 冲突 |
| D3 | 场景小节 | 实体同步生成（同场景设计模块机制） |
| D4 | 监控截图 | 不重新生成图片——复用详情页图表组件以 execution 终态数据只读渲染；手动上传图存 `platform.storage.root` |
| D5 | 删行语义 | 两选项：仅从表格移除（`method_hidden` 标记，数据保留）/ 彻底删除（复用详情页物理删除，级联删聚合+关联手动图） |
| D6 | 未展示记录去处 | 执行详情页「查看历史记录」下拉 = 场景全量执行记录（现有功能），表格可恢复被移出的记录 |

## 3. 信息架构

```
## 八、测试方法                    ← 章节标题（自定义模板序号不限）
  章节导语（body 自由文本，保留）
  ### S1 放款提交 · 容量测试       ← 实体同步生成，改名自动跟进
    小节头：场景名 + 测试类型徽标 + 关联脚本（下拉：项目脚本版本）
    方法说明：自由 Markdown（现有手写内容并入，行内编辑）
    执行记录表：见 §4
    监控证据区：见 §5
  ### S2 还款扣款 · 稳定性测试
    ...
  [+ 新增场景]                     ← 章节级操作
```

- Pretty 视图整章由新组件 `TestMethodModule.vue` 渲染；Markdown 视图显示 body 骨架（场景小节标题 + 方法说明文本），表格与截图不进入 Markdown（骨架中每场景带一行 `> 执行记录与监控证据：见 Pretty 视图 / 报告`）。

### 章节识别与挂载

- `frontend/src/utils/plan-markdown.ts` 的 `CANONICAL_HEADINGS` 注册「测试方法」规范标题；`canonicalTitleOf()` 调整匹配优先级：**关键词精确匹配（标题以「测试方法」结尾）先于序号前缀容错**——否则 `八、测试方法` 会被 `八、` 前缀吞并映射到「八、场景设计」。
- `PlanDetailDocument.vue` 增加 `v-else-if` 分支：`section.title === '测试方法'`（heading 双重校验同场景设计做法）渲染 `TestMethodModule`；`isModuleSection()` 纳入该标题（不提供通用行内 Markdown 编辑入口）。
- 内置模板（`PlanTemplateSeeder`）本次不动；本功能对含「测试方法」独立章节的自定义模板生效。

### body 骨架同步

- 扩展 `PlanScenarioDocSync`：场景增删改时，除同步场景设计章节外，若文档存在「测试方法」章节，同步维护该章节下的场景小节骨架（`### S{n} 名称 · 类型` + 方法说明占位），方法说明文本为用户自由区，同步不触碰（复用 `PlanMarkdownSupport.upsertScenarioFacts` 的保留语义）。
- 章节导语（标题与首个场景小节之间的内容）原样保留；存量文档的手写场景 bullet 不做自动迁移，由用户整理进各场景方法说明。

## 4. 执行记录表

### 列定义（初始集，可扩展）

| # | 用户数 | Ramp-up(s) | 压测时间 | 样本数 | 成功率 | 平均RT(ms) | P95(ms) | TPS | 状态 | 执行时间 | 操作 |
|---|--------|-----------|---------|--------|--------|-----------|---------|-----|------|---------|------|

- 参数三列（用户数/Ramp-up/压测时间）显示**该次执行的快照**（`execution.configJson`），调参后旧行不变。
- 关联脚本为场景级属性（小节头下拉维护），同一场景内脚本唯一，不设行级列。
- 结果五列自动回填自 `AggregateReport.summary`：`samples`、成功率 = `100 - errorRate`（1 位小数）、`avgRt`、`p95`、`throughput`。未终态显示 `—`。
- 状态列：PENDING/RUNNING/SUCCESS/FAILED/INTERRUPTED 徽标。
- 列扩展路径：p90/p99/min/max 已在 `summaryJson`/`aggregateRows` 中，前端列配置加列即可，无后端改动。

### 新增执行（表格底部常驻行）

- 三个参数输入（用户数/Ramp-up/压测时间），默认值取场景首个 preset（无 preset 时取场景级 threads/rampUp/duration），可就地改——参数不固定，随压测进展调整。
- 「执行」按钮：状态门禁（见 §7）；预检失败沿用场景设计模块的跳过预检确认弹窗。
- 点击执行 → `POST /api/scenarios/{id}/executions` → 成功后 `router.push('/projects/{projectId}/executions/{id}')` 跳执行详情页跟进（复用 `useTaskPlans.openExecution`）。

### 行交互

- 行点击（操作列除外）→ 跳执行详情页（全量数据：聚合明细/实时监控/被测目标监控/异常样本/历史记录）。
- 执行中行：模块内 5s 轮询章节聚合端点，直至该场景无活跃执行。
- 删除（操作列）：弹窗两选项——
  1. **仅从表格移除**：`scenario_executions.method_hidden = true`；执行详情页历史下拉仍全量可见；表格底部出现「已移出 N 条」恢复入口。
  2. **彻底删除**：调既有批量删除端点物理删执行记录 + 聚合数据，级联删挂该 execution 名下的手动上传图；弹窗红字提示「报告页对应数据将一并消失」。

## 5. 监控证据区

### 5.1 自动引用（执行数据图，不落图片文件）

监控证据区位于**记录表格下方**（场景级归档区块，不嵌在表格行间）：区块头提供执行记录选择器（默认最新一次成功执行），切换后渲染该次执行的四张趋势图，组件复用、数据取执行终态：

| 图 | 组件 | 数据 |
|----|------|------|
| TPS 趋势 | `TaskMonitoringCharts`（纯展示无轮询，`monitoring.ticks[*].overall.throughput`） | `GET /api/executions/{id}/monitoring` |
| 响应时间趋势 | 同上（`avgRtMs`/`p95RtMs`） | 同上 |
| 服务器 CPU | `TargetMetricChartCard`（kind=`SERVER_CPU`，经 `TargetServerMetricsPanel` 同构调用） | `GET /api/executions/{id}/target-monitoring/series` |
| 服务器内存 | 同上（kind=`SERVER_MEM`） | 同上 |

`TargetServerMetricsPanel` 传 `polling=false` 即单次拉取终态序列，只读复用成立。未绑定被测目标监控时显示占位提示。

### 5.2 手动上传（补充截图）

- 每场景两列大图网格：图片按**原始比例完整显示**（监控截图的坐标轴/图例必须可辨，不做裁切缩略图）；上传（png/jpg/webp，≤5MB）、图注行内编辑（如「CPU 85% @09-15 14:30」）、拖拽排序、替换、删除、点击灯箱放大。
- 可选挂在某条执行记录名下（删行「彻底删除」时级联删）或仅挂场景。

## 6. 数据模型与 API

### 6.1 表变更

- `scenario_executions` 加列 `method_hidden TINYINT(1) NOT NULL DEFAULT 0`（按既有 JPA 实体管理方式演进）。
- 新表 `plan_evidence_images`：

| 列 | 类型 | 说明 |
|----|------|------|
| id | BIGINT PK | |
| plan_id / scenario_id | BIGINT | 归属 |
| execution_id | BIGINT NULL | 可选挂执行名下 |
| caption | VARCHAR(200) | 图注 |
| sort_order | INT | 场景内排序 |
| stored_path | VARCHAR(500) | 相对 `platform.storage.root`，沿用现有约定 |
| content_type / size_bytes | | 校验与响应头 |
| uploaded_by / created_at | | 审计 |

文件落盘：`{storageRoot}/images/plans/{planId}/{uuid}.{ext}`。无静态资源映射（现状如此），读取走 API 流式端点并做项目成员鉴权——与现有脚本/日志产物同策略。

### 6.2 API 变更

| 端点 | 类型 | 说明 |
|------|------|------|
| `GET /api/task-plans/{planId}/method` | 新增 | 章节聚合：场景列表（含绑定脚本）+ 每场景执行记录（config 快照摘要 + summary + method_hidden + 关联手动图元数据），避免前端 N+1 |
| `POST /api/scenarios/{id}/executions` | 扩展 | `TriggerExecutionRequest` 增加可选 `overrides { threads, rampUpSec, durationSec }`；`ExecutionConfigMerger` 在选中 preset（默认首个）基础上应用覆盖后快照进 `configJson`；`RequestHashing` 将 overrides 纳入 requestHash（防同键不同参误重放）；`executionName` 缺省自动生成 `S{n} {threads}并发 {MM-dd HH:mm}` |
| `PATCH /api/executions/{id}/method-visibility` | 新增 | 移出/恢复表格（`methodHidden`） |
| `POST /api/task-plans/{planId}/scenarios/{sid}/images` | 新增 | multipart 上传手动截图 |
| `PUT /api/images/{id}` / `DELETE /api/images/{id}` | 新增 | 图注/排序 / 删除 |
| `GET /api/images/{id}/file` | 新增 | 鉴权后流式返回图片 |
| `DELETE /api/scenarios/{id}/executions` | 既有 | 彻底删除复用（`deleteExecutionsApi`），后端补级联删 `plan_evidence_images.execution_id` |

## 7. 权限与阶段门禁

| 操作 | 权限 | 依据 |
|------|------|------|
| 方法说明/行参数/图片/移出行/绑脚本/增删场景编辑 | `EDIT`（项目成员，任意状态） | 沿用 `PlanAccess` 现状 |
| 执行按钮 | `EXECUTE`（仅 EXECUTING/REPORTING） | `PlanWorkflowService.assertExecutionAllowed` 既有门禁；其他状态禁用 + tooltip |
| 彻底删除 | 与详情页历史批量删除一致（现有端点权限） | 现状 |
| 图片文件流 | 项目成员 | 新端点按 plan→project 校验 |

发布（PUBLISHED）后文档冻结语义沿用现状，无新增规则。

## 8. 导出与报告联动

- **Word/PDF 导出**：唯一需要静态图的地方。导出请求由前端发起前，对表格内每条执行记录离屏渲染四张图并 `ECharts.getDataURL()` 转 PNG，连同手动图 id 列表随导出请求提交，`ReportExportController` 嵌入。后端不引图表库。
- **报告页**：preset 汇总（`GET /api/reports/plans/{id}/data`）与执行记录表同源（execution + aggregate），无需改动即一致。

## 9. 非目标

- 内置模板「八、场景设计」章节的改造或合并（后续演进另议）。
- preset 管理界面增强（进阶参数仍走场景编辑）。
- 监控图服务端渲染、图片外链/图床。
- 执行参数模板化（同参数一键重跑历史行）。

## 10. 测试要点

- 后端：overrides 合并与 requestHash 纳入、`method_hidden` 过滤/恢复、图片上传鉴权/类型/大小/文件流、彻底删除级联（聚合+图）、章节聚合端点组装、幂等重放。
- 前端：章节激活（关键词优先于序号前缀，不误伤场景设计）、骨架同步保留自由文本、表格轮询至终态、执行选择器切换监控图、删行两选项与恢复、执行后跳转、EXECUTE 门禁态。
