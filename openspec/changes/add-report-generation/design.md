## Context

当前 `ExecutionDetailView.vue` 提供了执行结果的在线查看（聚合报告表、时序图、监控面板、失败样本），但这些内容无法导出为正式文档。报告生成系统需要在此基础上新增下载和结构化预览能力。

业务背景：
- 压测执行 → 产生 AggregateReport + MetricSeries + FailureSamples + 监控数据
- 用户需要将结果整理为报告文档，可能添加结论/建议文字
- 文档格式：HTML 在线预览、Word (.docx) 下载
- 模板：一期仅默认模板，自定义模板延后

技术现状：
- 后端 Spring Boot 3.x，已有 AggregateReportService、ExecutionMetricSeriesService、FailureSampleStore、PrometheusQueryClient
- 前端 Vue 3 + Element Plus + ECharts，已有 MetricChart 组件
- Word 导出需要引入新依赖 XDocReport

## Goals / Non-Goals

**Goals:**
- 提供报告数据聚合 API，一次返回报告所需的全部数据
- HTML 在线报告预览页面，含 ECharts 动态图表
- 报告正文富文本编辑区，用户可添加结论/备注
- Word (.docx) 报告下载，默认模板生成
- 报告页面可从执行详情页导航进入

**Non-Goals:**
- 自定义模板系统（下期）
- Word 文档在线编辑 / OnlyOffice 集成（下期）
- Markdown 导出（需求不明确，暂不实现）
- PDF 导出（下期）
- 历史对比报告（下期）
- 自动邮件发送（下期）

## Decisions

### 1. 报告渲染架构

**决策**: 混合架构 — HTML 预览在前端渲染，Word 导出在后端生成。

```
前端 (HTML 报告)                    后端 (Word 导出)
┌─────────────────────────┐    ┌──────────────────────────┐
│ ReportPreviewPage.vue   │    │ ReportExportController   │
│ ├─ 数据: GET /api/      │    │ POST /api/reports/       │
│ │   reports/{execId}/   │    │   {execId}/export/word   │
│ │   data                │    │                          │
│ ├─ 图表: ECharts         │    │ XDocReport 引擎          │
│ └─ 编辑: TipTap          │    │ ├─ word-template.docx    │
│                          │    │ └─ 数据 + 图表截图       │
│ [下载 Word] 按钮 ────────┼───▶│ → 返回 .docx 二进制流    │
└─────────────────────────┘    └──────────────────────────┘
```

**备选方案**: 服务端渲染 HTML→全部后端生成。被否决，因为前端的 ECharts 交互式图表无法在后端等价位实现。

### 2. Word 生成引擎

**决策**: XDocReport + Freemarker 模板语法。

| 备选 | 否决原因 |
|------|---------|
| Apache POI 手写 | 代码量大，布局调整需改 Java 代码，非技术人员无法修改模板 |
| docx4j | 较低层 API，学习曲线陡峭 |
| JasperReports | 需要 .jrxml 设计器，输出以 PDF 为主，Word 支持弱 |
| docx-stamper | 功能与 XDocReport 类似但社区更小 |

XDocReport 优势：
- 模板即普通 .docx 文件，用 Word 即可编辑
- 支持 @foreach 表格循环、@if 条件、@image 图片嵌入
- Apache 2.0 协议，Maven 依赖简单
- Freemarker 语法团队熟悉度高

### 3. 图表在 Word 中的实现

**决策**: 前端 ECharts 渲染 → `getDataURL()` 导出 Base64 PNG → 后端嵌入 Word。

```
前端                              后端
ECharts.render()
    │
    ▼
chart.getDataURL({
  type: 'png',
  pixelRatio: 2     // 2x 分辨率确保打印清晰
})
    │
    ▼
base64 PNG 字符串
    │
    └── POST /api/reports/{id}/export/word
        { chartImages: { responseTime: "data:image/png;base64,..." } }
                                    │
                                    ▼
                              XDocReport 模板
                              «@image:responseTime»
```

**备选方案**: 后端用 JFreeChart 重新绑制图表。被否决，因为需要维护两套图表代码且样式不一致。

### 4. 报告数据 API 设计

**决策**: 新建 `GET /api/reports/{executionId}/data`，聚合以下数据源：

```
ReportData
├── execution:       ExecutionDetail (来自 ScenarioExecutionService)
├── aggregateReport: TaskExecutionResult (来自 AggregateReportService)
├── metricSeries:    List<MetricTick>  (来自 ExecutionMetricSeriesService)
├── failureSamples:  List<FailureSample> (来自 FailureSampleStore)
├── monitoring:      TargetMonitoringData (来自 TargetMetricsService)
└── scriptConfig:    ScriptVersion (来自 ScriptService)
```

一次返回全量数据，前端按需使用。不拆分为多个接口以减少请求次数。

### 5. 富文本编辑器

**决策**: TipTap (基于 ProseMirror)。

| 备选 | 否决原因 |
|------|---------|
| Quill | 维护不活跃，TypeScript 支持一般 |
| TinyMCE | 功能过重，有商业授权限制 |
| Tiptap | Vue 3 一等支持、TypeScript 友好、轻量可扩展 |

报告编辑区仅需基础格式化（标题、加粗、列表、段落），不需要表格/图片上传等复杂功能。

### 6. 模板管理

**决策**: 一期仅内置默认模板，存储在文件系统。

- HTML 报告布局：`frontend/src/components/reports/ReportPreviewPage.vue`（硬编码布局）
- Word 模板：`backend/src/main/resources/templates/report/word-template.docx`
- 模板配置文件：`backend/src/main/resources/templates/report/template.json`（节可见性配置）

未来支持自定义模板时，再从文件系统加载外部模板。

## Risks / Trade-offs

- **[Risk] 图表截图依赖前端渲染完成**：ECharts 渲染有异步时序问题。
  → **Mitigation**: 下载按钮禁用直到所有图表的 `finished` 事件触发。

- **[Risk] 大量失败样本时报告体积过大**：如果执行有数万条错误，报告 JSON 可能很大。
  → **Mitigation**: 报告错误只包含前 100 条 + 统计摘要。

- **[Risk] XDocReport 社区活跃度**：不算最活跃，但功能稳定。
  → **Mitigation**: 核心功能（表格填充、图片嵌入）是 XDocReport 最成熟的 feature，风险可控。

- **[Trade-off] Word 图表为静态图片**：无法像 HTML 端交互。
  → 可接受，Word 作为打印/分享格式，静态图表是标准做法。

## Open Questions

- 富文本编辑器编辑内容是否需要持久化存储（保存草稿）？——建议一期前端内存持有，刷新丢失
- 报告页面是否需要鉴权？——复用现有项目鉴权机制，报告页属于项目内页面
