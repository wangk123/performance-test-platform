## Why

M2 已立起 agent 面 Facade 与统一响应契约（`ApiResponse` 含 `truncated`/`nextCursor`/`warnings` 字段但恒 null）。M3 要在此之上建「数据链底座」：让跨数据源查询有界（游标+预算+可用性），并把执行/聚合/秒级/失败样本/Prometheus 统一到同一条关联键上——这是后续确定性分析（T7）、补充取证（T9）、MCP 接入（T12）的确定性事实地基。本底座只负责「如实关联 + 如实暴露缺失」，不做数据补齐（补采属 T8/T9，接新源属 T11）。

## What Changes

- 定义统一游标分页协议：opaque `nextCursor` + 三维响应预算（条数/字节/耗时），任一触顶即 `truncated=true` 并给续拉游标，复用 M2 封套字段。
- 定义数据可用性语义 `Availability`：存在性/时间覆盖/粒度/截断/来源定位(sourceRef)/缺失原因，硬约束「缺失显式暴露，禁止用其他时段/实例/源静默替代，禁止空成功伪装」。
- 新增 agent 面只读查询端点（全部经 Facade，游标+预算+可用性）：
  - `GET /api/agent/executions/{id}/aggregate`（聚合行）
  - `GET /api/agent/executions/{id}/metrics/series`（秒级指标，时间窗+粒度）
  - `GET /api/agent/executions/{id}/failure-samples`（keyset 游标，复用现有 `listSummariesAfter` 雏形）
  - `GET /api/agent/executions/{id}/prometheus`（**不收裸 promql**：按 execution 监控绑定+指标选择+时间窗，平台内部拼 promql）
- 新建 `evidence` 适配层（纯内部，不暴露端点）：定义统一关联键 `CorrelationKey`（`executionId`+绝对时间窗+目标实例+请求标签+可选 `traceId`），将各源统一为带关联键的 `EvidenceSummary`（只存摘要+sourceRef）。
- 时钟对齐：假设 NTP 已同步不主动校正；每条证据记录 source clock；检测到 execution 窗与 Prometheus 覆盖窗明显错位时经 `warnings` 显式标注「疑似时钟偏移」。
- 接入约束：新数据源必须声明其绑定的关联键，否则不接入；execution 删除→证据失效提示。
- UI 面失败样本 `page/pageSize` 端点与前端**零改动**，agent 面另建 keyset 游标端点。

## Capabilities

### New Capabilities

- `bounded-query`: 跨数据源统一游标分页 + 三维响应预算 + 数据可用性语义；agent 面聚合/秒级/失败样本/Prometheus 四条只读查询端点经 Facade 暴露；扩展分层守护覆盖新端点。
- `evidence-correlation`: 统一关联键与时钟对齐策略；`evidence` 内部适配层将各源统一为带关联键的证据摘要；新源接入约束与证据失效语义。

### Modified Capabilities

<!-- 无：不改动 agent-facade / agent-response-contract 的既有需求，仅在其之上新增能力 -->

## Impact

- 新增代码：`backend` agent 面新增 aggregate/metrics/failure-samples/prometheus 四个只读端点；`facade` 层新增对应查询方法与预算/可用性/游标基础设施；新建 `evidence` 包（关联键、证据摘要、适配层）。
- 复用不改：`AggregateReportService`、`FailureSampleStore`（含 `listSummariesAfter`）、`PrometheusQueryClient`、`ExecutionMonitorBindingService`、`ScenarioExecutionService`。
- 不影响：现有 `/api/**` UI 接口（含失败样本 offset 分页）、`PlatformExceptionHandler`、前端。
- 依赖：不新增第三方依赖。
- 明确不做：数据补齐（补采 T8/T9、接新源 T11）、确定性分析（T7）、脱敏/审计/限流（T10）。
