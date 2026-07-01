## Why

当前平台执行完成后只能在 ExecutionDetailView 页面在线查看聚合报告和监控图表，无法将结果导出为正式文档分享给团队成员或留存归档。需要为每次执行生成结构化、可下载的性能测试报告。

## What Changes

- 新增报告数据 API，聚合执行的汇总指标、聚合表、时序数据、监控数据和执行配置
- 新增 HTML 在线报告预览页面，以独立页面形式展示完整报告（含动态 ECharts 图表）
- 新增 Word (.docx) 报告下载功能，基于 XDocReport 模板引擎生成
- 新增报告正文富文本编辑区，允许用户在报告中添加结论、备注等文字内容
- 内置默认报告模板（HTML 布局 + Word 模板），自定义模板留待下期

## Capabilities

### New Capabilities
- `report-data-api`: 新增报告数据聚合接口，返回执行结果、时序指标、监控数据等完整报告数据
- `report-html-preview`: HTML 在线报告预览页面，含动态图表和富文本编辑区
- `report-word-export`: Word (.docx) 报告导出，基于 XDocReport 模板引擎

### Modified Capabilities
<!-- 本期不修改已有 spec -->

## Impact

- **后端新增**: ReportDataController、ReportExportController、ReportTemplateService
- **后端依赖新增**: XDocReport (org.apache 2.0 协议)
- **前端新增**: ReportPreviewPage 页面及路由、报告相关组件
- **前端依赖新增**: 富文本编辑器 (TipTap 或 Quill)
- **存储新增**: Word 默认模板文件 (resources/templates/report/)
- **API 影响**: 新增 3 个接口，不影响已有 API
