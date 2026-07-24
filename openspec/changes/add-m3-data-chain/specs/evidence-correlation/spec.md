## ADDED Requirements

### Requirement: 统一关联键

系统 SHALL 定义统一关联键 `CorrelationKey`，包含 `executionId`、绝对时间窗（from/to）、目标实例（host/instance/service，可多值）、请求标签（可选）、`traceId`（可选）。系统 SHALL 能将现有执行、聚合、秒级指标、失败样本、Prometheus 数据按同一 `CorrelationKey` 对齐到同一 execution 与时间窗。

#### Scenario: 跨源按 executionId + 时间窗对齐

- **WHEN** 以某 `executionId` 与其绝对时间窗构造 `CorrelationKey`
- **THEN** 系统 MUST 能据此定位到该 execution 的聚合、秒级指标、失败样本与对应 Prometheus 覆盖窗
- **AND** 各源返回的证据 MUST 携带同一 `CorrelationKey`

#### Scenario: 按目标实例细分对齐

- **WHEN** `CorrelationKey` 指定了目标实例
- **THEN** 系统 MUST 仅对齐该实例相关的证据
- **AND** MUST NOT 混入其他实例的数据

### Requirement: 证据源接入约束

每个证据源 MUST 声明其能填充关联键的哪些维度。无法绑定 `executionId` 或时间窗的数据源 MUST NOT 接入证据链。

#### Scenario: 未声明关联键的源被拒接入

- **WHEN** 一个证据源无法绑定 `executionId` 或时间窗
- **THEN** 系统 MUST NOT 将其纳入证据链
- **AND** MUST 要求其先声明关联键绑定

### Requirement: 时钟对齐与偏移告警

系统 SHALL 采「假设 NTP 已同步、不主动校正」策略。每条证据 MUST 记录其时间来源（source clock）。当 execution 时间窗与 Prometheus 实际覆盖窗错位超过阈值时，系统 MUST 通过 `warnings` 显式标注疑似时钟偏移，且 MUST NOT 修改任何原始时间戳。

#### Scenario: 检测到偏移显式告警

- **WHEN** execution 的 start/end 窗与 Prometheus 返回的实际覆盖窗错位超过阈值
- **THEN** 响应 MUST 在 `warnings` 中标注疑似时钟偏移
- **AND** MUST NOT 修正或平移任何时间戳

#### Scenario: 记录证据时间来源

- **WHEN** 生成任一 `EvidenceSummary`
- **THEN** 该证据 MUST 标明其时间来源（压测端 / 被测端 / Prometheus）

### Requirement: 证据摘要适配层

系统 SHALL 提供内部 `evidence` 适配层，将执行、聚合、秒级指标、失败样本、Prometheus 统一为带 `CorrelationKey` 的 `EvidenceSummary`。`EvidenceSummary` MUST 只包含摘要字段与来源定位（sourceRef），MUST NOT 内联原始大数据（如完整 response body）。该适配层 SHALL 供分析层（T7）调用，本变更 MUST NOT 为其新增对外端点。

#### Scenario: 证据摘要只存摘要与定位

- **WHEN** 适配层为某源生成 `EvidenceSummary`
- **THEN** 它 MUST 包含 `CorrelationKey`、可用性与来源定位
- **AND** MUST NOT 内联原始大数据

#### Scenario: 适配层不对外暴露端点

- **WHEN** 检查本变更新增的 agent 面端点
- **THEN** MUST NOT 存在直接返回 `EvidenceSummary` 的对外端点

### Requirement: 证据失效语义

当 execution 被删除时，系统 MUST 将其对应证据标注为失效，返回 `present=false` 且 `missingReason=DELETED`，MUST NOT 返回伪装为存在的空数据。

#### Scenario: execution 删除后证据失效

- **WHEN** 对已删除 execution 请求其证据摘要
- **THEN** 系统 MUST 返回 `present=false` 且 `missingReason=DELETED`
- **AND** MUST NOT 返回伪装存在的空证据
