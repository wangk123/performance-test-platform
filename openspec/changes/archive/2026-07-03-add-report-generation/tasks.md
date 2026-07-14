## 1. 后端依赖与基础设施

- [x] 1.1 添加 XDocReport Maven 依赖（xdocreport-spring、xdocreport-docx、freemarker）
- [x] 1.2 创建 ReportExportRequest DTO（chartImages Map + editorContent String）
- [x] 1.3 创建 ReportDataResponse DTO（聚合所有报告数据字段）

## 2. 报告数据 API

- [x] 2.1 创建 ReportDataController，实现 GET /api/reports/{executionId}/data
- [x] 2.2 聚合 ExecutionMetadata（从 ScenarioExecutionService 获取执行元数据）
- [x] 2.3 聚合 AggregateReport（从 AggregateReportService 获取汇总和聚合行）
- [x] 2.4 聚合 MetricSeries（从 ExecutionMetricSeriesService 获取时序数据）
- [x] 2.5 聚合 FailureSamples（从 FailureSampleStore 获取失败样本，上限 100 条）
- [x] 2.6 聚合 ScriptConfig（从 ScriptService 获取线程组配置）
- [x] 2.7 聚合 MonitoringSnapshots（延后集成，现有 API 不支持按 executionId 批量查询）
- [ ] 2.8 编写 ReportDataController 单元测试

## 3. Word 导出功能

- [x] 3.1 创建 ReportExportController，实现 POST /api/reports/{executionId}/export/word
- [x] 3.2 创建 ReportTemplateService，从 classpath 加载默认 Word 模板
- [x] 3.3 实现 XDocReport 数据绑定：执行元数据、汇总数据、聚合表、用户编辑内容
- [x] 3.4 实现图表 Base64 解码并嵌入 Word 文档
- [x] 3.5 设计并创建默认 Word 模板 word-template.docx（含标准排版、Freemarker 占位符）
- [ ] 3.6 编写 ReportExportController 单元测试

## 4. 前端报告页面基础设施

- [x] 4.1 添加 Vue Router 路由 `/projects/:projectId/reports/:executionId`
- [x] 4.2 创建前端 API 封装 `frontend/src/api/reports.ts`（fetchReportData、exportWord）
- [x] 4.3 创建 ReportPreviewPage.vue 页面骨架（数据加载、章节容器）

## 5. HTML 报告核心章节

- [x] 5.1 实现报告头部（执行名称、脚本名称、状态标签、执行时间）
- [x] 5.2 实现 Executive Summary 指标卡片（Samples、Throughput、Avg RT、P95、Error Rate）
- [x] 5.3 实现聚合报告表格（使用 Ant Design a-table 组件）
- [x] 5.4 实现响应时间随时间变化图表（ECharts 折线图，从 metricSeries 渲染）
- [x] 5.5 实现吞吐量随时间变化图表（ECharts 折线图，从 metricSeries 渲染）
- [x] 5.6 实现错误率随时间变化图表（ECharts 折线图，从 metricSeries 渲染）
- [ ] 5.7 实现目标监控指标面板（延后，复用现有 TargetServerMetricsPanel/TargetJvmMetricsPanel）
- [x] 5.8 实现失败样本列表（使用 Ant Design a-table）

## 6. 富文本编辑区

- [ ] 6.1 安装 TipTap 依赖（延后，一期用 contenteditable 实现基础编辑）
- [x] 6.2 创建富文本编辑器组件（contenteditable + 基础格式工具栏）
- [x] 6.3 将编辑器嵌入 ReportPreviewPage.vue 的报告正文区域

## 7. Word 下载流程

- [x] 7.1 实现图表截图工具函数（调用 ECharts getDataURL 获取 Base64 PNG）
- [x] 7.2 实现下载按钮交互：等待图表就绪 → 收集截图 + 编辑器内容 → 调用 export API
- [x] 7.3 处理文件下载（接收 Blob 响应 → 触发浏览器下载）
- [x] 7.4 处理下载按钮状态（loading、disabled、错误提示）

## 8. 集成与导航

- [x] 8.1 在 ExecutionDetailView.vue 添加"生成报告"按钮，跳转到报告页面
- [x] 8.2 确保报告页面能正确处理执行状态（SUCCESS / FAILED / INTERRUPTED 的不同展示）
- [ ] 8.3 手动端到端验证（需运行应用）
