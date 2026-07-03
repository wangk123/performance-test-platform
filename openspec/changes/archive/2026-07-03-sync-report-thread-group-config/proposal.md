## Why

场景线程组配置已改造为 preset（sortOrder）+ 多 Thread Group 行模型，任务计划场景执行记录（`ScenarioRowPanel`）已按该模型展示各线程组指标与汇总行。但报告仍以扁平 `rounds`（每条 execution 一行）聚合全量数据，会展示已删除配置的遗留执行、多线程组预设无法区分各 TG 指标，且图表误用 `tick.overall` 合并曲线。报告需与场景配置及执行记录展示对齐。

## What Changes

- 报告数据 API 从「按 execution 扁平列表」改为「按场景 preset 分组」，每个 preset 取最新一次有效执行
- 仅展示场景当前已配置的线程组 preset，无关历史执行不纳入报告
- 多线程组 preset：梯度对比总览与各梯度详情同时展示各 Thread Group 行 + 汇总行，与 `ScenarioRowPanel` 一致
- 单线程组 preset：仅展示该 TG 一行，无汇总行
- 无线程组配置的场景：报告展示空记录
- 时序图表改用 `tick.labels`（逐接口多条线），过滤为 preset 已配置 TG 下的接口，不再使用 `tick.overall`
- 汇总指标复用 `ScenarioThreadGroupConfigSupport` 的 label 裁剪与 `aggregatePresetSummary` 逻辑
- **BREAKING**：`PlanReportResponse` 中 `ScenarioReport.rounds` 替换为 `presets` 结构；前端报告预览页同步适配
- 本期不改动 Word 导出（`ReportExportService`）

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `report-data-api`：计划报告 API 响应结构改为 preset 分组，增加 per-TG 行与汇总行、执行过滤与指标裁剪规则
- `report-html-preview`：梯度对比总览与详情区按 preset/TG/汇总展示，图表使用 per-label 时序数据

## Impact

- 后端：`ReportDataService`、`PlanReportResponse`、`ScenarioThreadGroupConfigSupport`（下沉 `matchesThreadGroupConfig` 等共享逻辑）
- 前端：`ReportPreviewPage.vue`、`frontend/src/api/reports.ts` 类型定义
- 复用：`scenario-thread-group.ts` 中 `groupStoredThreadGroupConfigs`、`aggregatePresetSummary`、`sumPresetThreads`
- 不涉及：Word 导出、执行详情页、任务计划 CRUD API
