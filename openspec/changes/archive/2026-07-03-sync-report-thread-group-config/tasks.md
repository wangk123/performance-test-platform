## 1. 后端共享逻辑

- [x] 1.1 将 `matchesThreadGroupConfig` 从 `TaskScenarioService` 下沉至 `ScenarioThreadGroupConfigSupport`，新增 `matchesPresetSortOrder` / `findLatestMatchingExecution` 辅助方法
- [x] 1.2 在 `ScenarioThreadGroupConfigSupport` 新增 preset 级方法：`collectPresetSamplerLabels`、`filterAggregateRows`、`filterMetricTicks`、`buildPresetSummary`
- [x] 1.3 重构 `TaskScenarioService.loadLatestSummary` 调用下沉后的共享方法，确保行为不变

## 2. 报告 API 数据模型

- [x] 2.1 重构 `PlanReportResponse`：`ScenarioReport.rounds` 替换为 `presets`，新增 `PresetReport`、`ThreadGroupRowReport` 等 record
- [x] 2.2 `MetricTick` 改为携带 `labels[]`，移除对 `overall` 的依赖
- [x] 2.3 同步更新 `frontend/src/api/reports.ts` 类型定义

## 3. 报告数据聚合

- [x] 3.1 重写 `ReportDataService.buildScenarioReport`：从 `threadGroupConfigsJson` 分组 preset，无配置返回空 `presets`
- [x] 3.2 每个 preset 查找最新匹配执行，裁剪 per-TG summary 与 aggregateRows
- [x] 3.3 多 TG preset 计算汇总行 summary（对齐 `aggregatePresetSummary` 加权逻辑）
- [x] 3.4 `buildMetricSeries` 返回 per-label ticks，过滤 preset 下 sampler labels；failures 按 label 过滤
- [x] 3.5 添加 `ReportDataService` 单元测试：单 TG、多 TG+汇总、无配置、已删除 preset 过滤、最新执行选取

## 4. 报告预览页

- [x] 4.1 梯度对比总览改为按 preset 分块表格，列与 `ScenarioRowPanel` 一致，多 TG 展示汇总行
- [x] 4.2 「各梯度详细结果」改为按 preset 折叠展开，展示 scoped aggregateRows
- [x] 4.3 图表渲染改用 `tick.labels` 逐接口画线，过滤 preset labels（参考 `TaskMonitoringCharts`）
- [x] 4.4 更新 `totalRounds` / `concurrencyLevels` 等 computed 适配 `presets` 结构

## 5. 验证

- [x] 5.1 手动验证：多 TG 场景报告梯度总览与任务计划场景执行记录一致（各 TG 行 + 汇总行）
- [x] 5.2 手动验证：无配置场景、无执行数据的 preset 展示占位符
- [x] 5.3 确认 Word 导出按钮仍可用（本期不验证导出内容正确性）
