## ADDED Requirements

### Requirement: 统一游标分页协议

系统 SHALL 为 agent 面跨数据源只读查询提供统一游标分页协议，复用 M2 封套字段 `truncated` 与 `nextCursor`。`nextCursor` MUST 为不透明（opaque）字符串，调用方 MUST NOT 依赖其内部结构。当结果被截断时 `truncated` MUST 为 true 且 `nextCursor` MUST 非空；未截断时两者 MUST 分别为 false/null。以同一 `nextCursor` 续拉 MUST 从上次截断位置之后继续，不重复、不遗漏。

#### Scenario: 结果被截断返回续拉游标

- **WHEN** 一次查询命中数据超过响应预算
- **THEN** 响应 MUST 返回 `truncated=true` 与非空 `nextCursor`
- **AND** 返回的数据条目 MUST 是有序前缀（不跳过中间数据）

#### Scenario: 用游标续拉不重不漏

- **WHEN** 调用方以上次响应的 `nextCursor` 再次请求同一查询
- **THEN** 响应 MUST 返回上次截断位置之后的数据
- **AND** 相邻两页之间 MUST NOT 出现重复或遗漏条目

#### Scenario: 结果完整无需分页

- **WHEN** 一次查询命中数据未触及任何预算上限
- **THEN** 响应 MUST 返回 `truncated=false` 且 `nextCursor` 为 null

### Requirement: 三维响应预算

系统 SHALL 对每类有界查询施加条数、字节、耗时三维预算（`maxItems`/`maxBytes`/`maxMillis`）。任一维度触顶时系统 MUST 停止继续取数、置 `truncated=true`、返回续拉游标，并在 `warnings` 中显式标注触顶维度。超出预算 MUST 被视为正常截断而非错误；仅当调用方传入非法预算参数时系统 MUST 返回 `VALIDATION_FAILED`。

#### Scenario: 条数触顶截断

- **WHEN** 命中条目数达到 `maxItems`
- **THEN** 系统 MUST 停止取数并返回 `truncated=true` + `nextCursor`
- **AND** `warnings` MUST 包含标识条数维度触顶的告警

#### Scenario: 字节触顶截断

- **WHEN** 已累计序列化字节达到 `maxBytes`（如失败样本含大 response body）
- **THEN** 系统 MUST 在不超发的前提下截断，返回 `truncated=true` + `nextCursor`
- **AND** `warnings` MUST 包含标识字节维度触顶的告警

#### Scenario: 非法预算参数被拒

- **WHEN** 调用方传入非法预算参数（如负数 `maxItems`）
- **THEN** 响应 MUST 返回 `VALIDATION_FAILED`
- **AND** MUST NOT 执行查询

### Requirement: 数据可用性语义

系统 SHALL 为每类有界查询返回数据可用性 `Availability`，包含存在性、实际时间覆盖、粒度、截断、来源定位（sourceRef）、缺失原因。当数据源不可达时系统 MUST 置 `present=false`、`missingReason=SOURCE_UNAVAILABLE` 并返回错误码 `DATA_SOURCE_UNAVAILABLE`，MUST NOT 返回伪装为「无数据」的空成功响应。系统 MUST NOT 用其他时段、实例或数据源的数据静默替代缺失数据。

#### Scenario: 数据源不可达显式暴露

- **WHEN** 查询所依赖的数据源不可达（如 Prometheus 连接失败）
- **THEN** 响应 MUST 返回 `DATA_SOURCE_UNAVAILABLE` 且 `Availability.present=false`、`missingReason=SOURCE_UNAVAILABLE`
- **AND** MUST NOT 返回空数据的成功响应

#### Scenario: 数据存在时声明覆盖与来源

- **WHEN** 查询命中数据
- **THEN** `Availability` MUST 给出实际时间覆盖 `timeRange` 与来源定位 `sourceRef`
- **AND** `present` MUST 为 true

#### Scenario: 禁止静默替代缺失数据

- **WHEN** 请求时间窗内某实例无数据
- **THEN** 系统 MUST 如实标注该窗/实例缺失
- **AND** MUST NOT 用相邻时段或其他实例的数据填充

### Requirement: agent 面有界只读查询端点

系统 SHALL 通过 agent 面提供四条只读查询端点，全部经 `DataFacade`、遵循游标分页协议、三维预算与可用性语义：`GET /api/agent/executions/{id}/aggregate`（聚合行）、`GET /api/agent/executions/{id}/metrics/series`（秒级指标，含时间窗+粒度）、`GET /api/agent/executions/{id}/failure-samples`（id keyset 游标）、`GET /api/agent/executions/{id}/prometheus`（按监控绑定+指标选择+时间窗）。Prometheus 端点 MUST NOT 接受调用方传入的裸 promql。

#### Scenario: 失败样本 keyset 游标分页

- **WHEN** 携带有效身份分页请求某 execution 的失败样本
- **THEN** 响应 MUST 以 id keyset 顺序返回样本并遵循预算截断
- **AND** 续拉 MUST 复用 keyset 游标不重不漏

#### Scenario: Prometheus 端点拒绝裸 promql

- **WHEN** 调用方尝试通过 Prometheus 端点传入自定义 promql 字符串
- **THEN** 系统 MUST NOT 执行该 promql
- **AND** 系统 MUST 仅按 execution 监控绑定与指标选择在平台内部生成查询

#### Scenario: 查询不存在的 execution

- **WHEN** 携带有效身份查询不存在 `executionId`
- **THEN** 响应 MUST 返回 `NOT_FOUND`
- **AND** MUST NOT 返回空成功响应

### Requirement: 有界查询端点分层守护

agent 面新增的有界查询 controller MUST 只依赖 `facade` 层，MUST NOT 直接依赖 `*Repository`、文件系统 API 或 Prometheus 客户端。分层约束测试 SHALL 覆盖全部新增端点。

#### Scenario: 新端点不直连底层资源

- **WHEN** 对 agent 面有界查询 controller 做依赖方向检查
- **THEN** 这些类 MUST NOT 出现对 `*Repository`、`java.nio.file` 文件 API 或 Prometheus 客户端的直接依赖
- **AND** 只允许依赖 `facade` 层入口
