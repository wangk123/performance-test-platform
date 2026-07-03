## Context

任务计划场景已支持 `threadGroupConfigsJson`：按 `sortOrder` 分组为 preset，每个 preset 可含多个 Thread Group 行。执行时按 preset 触发，一次执行可同时 patch 多个 TG。`ScenarioRowPanel` 已展示各 TG 行 + 汇总行，指标通过 `ScenarioThreadGroupConfigSupport.summarizeThreadGroupResult` 按 sampler label 裁剪。

`ReportDataService.aggregateByPlan` 仍遍历场景全部已完成 execution，输出扁平 `rounds`，使用全量 aggregate 与 `tick.overall`，与场景配置模型脱节。

## Goals / Non-Goals

**Goals:**

- 计划报告 API 与预览页按 preset 组织数据，与 `ScenarioRowPanel` 展示一致
- 每个 preset 仅关联最新一次匹配执行；仅包含场景当前配置的 preset
- 多 TG preset 展示各 TG 行 + 汇总行；单 TG 无汇总行
- 时序图使用 `tick.labels` 逐接口展示，过滤为 preset 已配置 TG 下的接口
- 下沉 `matchesThreadGroupConfig` 至 `ScenarioThreadGroupConfigSupport` 供报告与场景服务共用

**Non-Goals:**

- Word 导出（`ReportExportService`）本期不改
- 单 execution 维度报告 API（`GET /api/reports/{executionId}/data`）本期不改
- 修改执行详情页图表逻辑

## Decisions

### 1. API 响应结构：rounds → presets

**决策**：`ScenarioReport.rounds` 替换为 `presets[]`，每项含 `sortOrder`、配置元数据、`rows[]`（per-TG）、可选 `summary`、关联 `executionId` 及详情字段（aggregateRows、metricSeries、failures）。

```
ScenarioReport
└── presets[]
     ├── sortOrder, label ("配置 N")
     ├── executionId, status, startedAt, ...
     ├── rows[]: { stepId, stepName, threads, rampUp, duration, summary }
     ├── summary?: { samples, throughput, avgRt, p95, errorRate }  // 多 TG 时
     └── detail: { aggregateRows, metricSeries, failures }          // preset 级详情
```

**备选**：保留 `rounds` 并前端分组。否决——后端应输出语义正确的结构，避免前端重复裁剪与匹配逻辑。

### 2. 执行匹配与过滤

**决策**：复用 `matchesThreadGroupConfig` 逻辑（按 `threadGroupPresetSortOrder` 或 `threadGroupConfigId`），对每个 preset 的 `sortOrder` 在已完成执行中按 id 降序取第一条匹配记录。

- 场景 `threadGroupConfigs` 为空 → `presets: []`
- 执行 config 不匹配任何当前 preset → 不展示
- 已删除 preset 的历史执行 → 不展示

**实现**：将 `TaskScenarioService.matchesThreadGroupConfig` 移至 `ScenarioThreadGroupConfigSupport.matchesExecutionConfig(configJson, sortOrder, configId)`。

### 3. 指标裁剪

**决策**：

| 数据 | 单 TG preset | 多 TG preset |
|------|-------------|-------------|
| rows[].summary | 全量 execution summary 或 label 裁剪后 summary | `summarizeThreadGroupResult(steps, config, result)` |
| summary 行 | 不返回 | `summarizeAggregateRows` 合并各 TG labels，或加权聚合各 row summary（与 `aggregatePresetSummary` 一致） |
| aggregateRows（详情） | 该 TG 下 sampler labels | 各 TG labels 合集 |
| failures | 按 label 过滤 | 按 preset 全部 TG labels 过滤 |
| metricSeries | `tick.labels` 过滤 preset labels | 同上 |

**P95**：per-TG summary 从裁剪后的 aggregate rows 计算；汇总行 P95 取各 TG P95 的 samples 加权或 max（与 `aggregatePresetSummary` 对齐，avgRt/errorRate 加权，P95 在汇总行展示加权估算值）。

### 4. 时序图表

**决策**：`PlanReportResponse.MetricTick` 改为携带 `labels[]`（与 `TaskMetricSeries` 一致），报告页按接口画多条线，同 `TaskMonitoringCharts`。不使用 `overall`。

**过滤**：`collectSamplerLabels` 合并 preset 内所有 TG 的 labels，仅保留这些 label 的 series。

### 5. 前端梯度对比总览

**决策**：按 preset 分块渲染表格（列与 `ScenarioRowPanel` 一致：Thread Group、线程数、Ramp-Up、执行时间、采样数、TPS、平均响应时间、错误率），多 TG 时追加汇总行。不再使用扁平「梯度 / 线程数 / 采样数」单表。

「各梯度详细结果」折叠区按 preset 展开，展示接口聚合表与 per-label 图表。

## Risks / Trade-offs

- **[Risk] API BREAKING** → 仅 `ReportPreviewPage` 与 `reports.ts` 消费该 API，同步改即可；Word 导出暂用旧结构或本期不验证
- **[Risk] 汇总行 P95 无精确加权** → 与 `ScenarioRowPanel` 一致暂不展示 P95 汇总列，或展示 `—`；梯度总览表去掉 P95 列与场景记录对齐
- **[Risk] 逻辑重复** → 共享逻辑下沉至 `ScenarioThreadGroupConfigSupport`，前端复用 `scenario-thread-group.ts` 工具函数做展示格式化

## Migration Plan

1. 后端先改 `PlanReportResponse` 与 `ReportDataService`
2. 前端同步改类型与 `ReportPreviewPage`
3. 无数据库迁移；历史执行数据不变，仅读取过滤方式变化
4. 回滚：恢复 API 响应结构与前端渲染

## Open Questions

（无——探索阶段已确认：最新执行、无配置为空、图表 per-label、Word 跳过）
