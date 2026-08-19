## Context

M2（`add-agent-facade-contract`，已归档）已提供：agent 面 `/api/agent/**`、唯一强制业务入口 `DataFacade`/`FacadeGuard`、统一响应封套 `ApiResponse`（`truncated`/`nextCursor`/`warnings` 字段已定义但恒 null）、稳定错误码 `AgentErrorCode`（含 `QUERY_TOO_LARGE`/`DATA_SOURCE_UNAVAILABLE`/`TIMEOUT`）、分层守护测试。

现有数据源查询形态各异：
- 聚合/秒级：`AggregateReportService` 一次返回整个 `TaskExecutionResult`（聚合行 `AggregateRow` + 秒级 `MetricSeries`/`MetricSeriesPoint`），无分页。
- 失败样本：`FailureSampleStore.querySummaries` 用 page/pageSize/OFFSET；另有 `listSummariesAfter(lastEventId, limit)` 为 id keyset（游标雏形）。样本 DB 一 execution 一文件。
- Prometheus：`PrometheusQueryClient.queryRange(promql, start, end, step)`，无预算/可用性。
- 监控绑定：`ExecutionMonitorBindingService` 持有 execution 与监控目标的绑定。

M3 在此之上补「有界」与「关联」两层，不改上述 service 的既有行为。

## Goals / Non-Goals

**Goals:**
- 统一游标分页协议（opaque cursor + 三维预算），复用 M2 封套字段。
- 统一数据可用性语义，缺失显式暴露、禁止静默替代。
- agent 面暴露四条只读查询端点，全部经 Facade、带游标/预算/可用性。
- 建立统一关联键 `CorrelationKey` 与 `evidence` 内部适配层，把各源对齐到同一 execution+时间窗。
- 时钟对齐采「假设同步+偏移告警」的轻量策略。

**Non-Goals:**
- 数据补齐：主动补采（T8/T9）、接入新深度证据源（T11）。
- 确定性分析算法（T7）。
- 脱敏/审计/限流（T10）；接入裸 promql 能力（安全考量，永不做）。
- 改动 UI 面失败样本 offset 分页、前端、`PlatformExceptionHandler`。
- evidence 对外端点（留 T7/T11）。

## Decisions

### D1: 游标为 opaque base64，内部按源类型编码
- 失败样本：keyset `{lastId}`（id ASC），复用 `listSummariesAfter`；避免 OFFSET 深翻页问题。
- 聚合行/秒级：时间戳 keyset（`AggregateRow` 时间点 / `MetricSeriesPoint.ts`）。
- Prometheus：时间窗切分，游标记下一段 `start`（step 对齐）。
- 对外统一：调用方只见不透明字符串，`truncated=true` 表示有后续，回传 `nextCursor` 续拉。
- 备选：统一 OFFSET 分页——否决，深翻页慢且失败样本已有 keyset 雏形；游标语义也更贴合流式证据。

### D2: 三维响应预算 `PageBudget(maxItems, maxBytes, maxMillis)`
- 默认 `maxItems=1000`、`maxBytes=1MB`、`maxMillis=3000`；任一触顶即停止取数、置 `truncated=true`、给 `nextCursor`、在 `warnings` 标注触顶维度（如 `budget:items`）。
- 超预算是「正常截断」而非错误；仅当调用方显式传入非法预算参数才 `VALIDATION_FAILED`。
- 备选：仅条数上限——否决，单条失败样本含 request/response body 可能极大，需字节+耗时兜底。

### D3: 数据可用性语义 `Availability`
字段：`present`(bool) / `timeRange`(实际覆盖 from,to) / `granularity`(粒度或 null) / `truncated`(bool) / `sourceRef`(来源定位，如 DB 文件名+id 段 / promql+step / executionId) / `missingReason`(枚举：`SOURCE_UNAVAILABLE`/`NO_DATA`/`DELETED`/null)。
- 硬约束：源不可达→`present=false`+`missingReason=SOURCE_UNAVAILABLE`+错误码 `DATA_SOURCE_UNAVAILABLE`，MUST NOT 返回空成功伪装为「无数据」。
- 缺失 MUST NOT 用其他时段/实例/源的数据替代。

### D4: 统一关联键 `CorrelationKey`
`executionId`(long) + `timeWindow`(绝对 from/to `Instant`) + `targetInstance`(host/instance/service，可多值) + `requestLabel`(String，可选) + `traceId`(String，可选，现无来源留空)。
- 每个证据源在适配时 MUST 声明它能填充关联键的哪些维度；无法绑定 `executionId` 或时间窗的源不接入。

### D5: 时钟对齐 = 假设同步 + 偏移告警（不校正）
- 每条 `EvidenceSummary` 记录 `sourceClock`（`load`/`target`/`prometheus`）。
- 检测：execution 的 start/end 窗与 Prometheus 实际返回覆盖窗错位超过阈值（默认 step 的 2 倍）→ 追加 `warnings` `clock:skew-suspected`，不修改任何时间戳。
- 备选：静态 offset 配置 / 拐点自动推导——否决（YAGNI + 误判风险），需要时再升级。

### D6: `evidence` 适配层为纯内部
- `EvidenceSummary`：`correlationKey` + `sourceType`(execution/aggregate/series/failure-sample/prometheus) + `availability` + 摘要字段 + `sourceRef`（只存定位，不存原始大数据）。
- `EvidenceSource` 接口：`supports(CorrelationKey)` + `summarize(CorrelationKey, PageBudget)`；新源实现接口并声明绑定维度。
- 供 T7 分析层调用，M3 不加对外端点。
- execution 删除→对应证据 `present=false`+`missingReason=DELETED`。

### D7: 端点与 Facade 分工
- 四个新 controller 仅依赖 `DataFacade` 新增方法；游标编解码、预算裁剪、可用性组装在 facade/支持类内完成。
- Prometheus 端点入参为 execution + 指标选择（枚举/绑定项）+ 时间窗+粒度，facade 内借 `ExecutionMonitorBindingService` 拼 promql，MUST NOT 透传裸 promql。
- 分层守护测试扩展覆盖四个新 controller。

## Risks / Trade-offs

- [游标跨源语义不一] → 对外统一 opaque + `truncated`/`nextCursor`，内部各源独立编码，测试覆盖每源续拉正确性。
- [字节预算难精确（序列化后才知大小）] → 以「累加已序列化条目字节」近似，触顶即停并回退最后一条到游标，宁可少给不可超发。
- [时钟偏移仅告警不校正，Agent 可能误读] → `warnings` 显式标注 + 文档说明；确定性分析（T7）据此决定是否采信，符合「如实暴露」原则。
- [Prometheus 拒绝裸 promql 限制灵活性] → 以监控绑定+指标选择覆盖当前场景；扩展指标走绑定配置而非开放注入。
- [失败样本 keyset 与 UI offset 并存] → agent 面独立端点与方法，UI 面零改动，回归测试守护。

## Migration Plan

- 纯增量：新增端点、facade 方法、`evidence` 包；不改既有 service 行为与 UI 接口。
- 回滚：移除新端点与 `evidence` 包即可，无数据结构迁移。

## Open Questions

- 无（关键分叉已在 explore 阶段定：单 change 打包、四端点全暴露、时钟假设同步、evidence 纯内部）。
