# 监控采集与报告增强模块

## 1. 模块定位

监控采集与报告增强用于把压测结果和被测资源指标关联起来。该模块不进入 MVP，必须在执行闭环和基础报告稳定后再建设。

## 2. 职责边界

| 负责 | 不负责 |
|------|--------|
| 监控目标管理 | 启停 JMeter 任务 |
| CPU、内存、磁盘、网络指标采集 | 项目成员权限模型 |
| 任务执行期间指标关联 | 原始 JMX 管理 |
| 报告趋势图和对比报告 | AI 自动结论 |
| Word/PDF 导出 | 分布式执行调度 |

## 3. 功能范围

1. 监控目标管理，包括服务器名称、IP、标签、认证方式和项目归属。
2. CPU、内存、磁盘、网络等基础指标采集。
3. 任务执行期间自动关联监控目标和指标时间范围。
4. 报告中展示资源趋势图和关键时间点。
5. 支持 Word/PDF 导出和多次报告对比。

## 4. 关键实体

| 实体 | 关键字段 | 说明 |
|------|----------|------|
| `monitor_target` | `id`, `project_id`, `name`, `host`, `labels`, `auth_type`, `status` | 监控目标 |
| `metric_sample` | `id`, `target_id`, `metric_name`, `metric_value`, `sample_time`, `tags` | 指标采样 |
| `execution_monitor_binding` | `execution_id`, `target_id`, `start_time`, `end_time` | 执行与监控目标关系 |
| `report_compare` | `id`, `base_report_id`, `target_report_id`, `summary` | 报告对比记录 |

## 5. 接口草案

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/projects/{projectId}/monitor-targets` | 监控目标列表 |
| `POST` | `/api/projects/{projectId}/monitor-targets` | 创建监控目标 |
| `GET` | `/api/executions/{executionId}/metrics` | 查询执行期间指标 |
| `POST` | `/api/reports/compare` | 创建报告对比 |
| `GET` | `/api/reports/{reportId}/download?format=pdf` | 下载 PDF |
| `GET` | `/api/reports/{reportId}/download?format=word` | 下载 Word |

## 6. 详细设计调整点

1. 初期优先考虑轻量 SSH 或 Agent 采集，避免一开始强依赖 Prometheus 和 Grafana。
2. 指标查询接口按时间范围和指标名过滤，保留后续接入 Prometheus 的适配层。
3. 指标采样频率、保留周期和最大存储量必须可配置。
4. 监控数据不得阻塞压测任务执行，采集失败只影响报告增强部分。
5. 报告增强要复用基础报告数据模型，不复制执行配置和脚本版本信息。
