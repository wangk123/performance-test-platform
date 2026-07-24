## ADDED Requirements

### Requirement: agent 面唯一强制业务入口

系统 SHALL 提供 `facade` 层作为 agent 面（`/api/agent/**`）唯一强制业务入口。agent 面 controller MUST 只依赖 `facade`，MUST NOT 直接依赖 Repository、文件系统 API 或监控客户端。Facade MUST 复用既有 service 完成业务逻辑，MUST NOT 复制业务规则。

#### Scenario: agent controller 不直连底层资源

- **WHEN** 对 agent 面 controller 包做依赖方向检查
- **THEN** 该包 MUST NOT 出现对 `*Repository`、`java.nio.file` 文件 API 或 Prometheus 客户端的直接依赖
- **AND** 只允许依赖 `facade` 层入口

#### Scenario: Facade 复用既有 service

- **WHEN** Facade 处理执行摘要请求
- **THEN** 它 MUST 调用既有 `ScenarioExecutionService` 获取执行与结果数据
- **AND** MUST NOT 在 Facade 内重新实现执行状态或聚合结果的业务规则

### Requirement: Facade 统一装配主体校验与横切钩子

Facade MUST 在业务调用前统一装配请求主体校验（复用 T1 的 `Principal`），并 SHALL 预留审计钩子与授权钩子（授权钩子留待 T2，M2 不做策略判定）。当主体无效或缺失时，Facade MUST 阻断业务调用。

#### Scenario: 无效主体被阻断

- **WHEN** 请求未携带有效身份（无 token / 无 API Key / 已失效）到达 agent 面
- **THEN** Facade 保护的业务调用 MUST NOT 执行
- **AND** 响应 MUST 为认证失败语义（401）

#### Scenario: 有效机器主体放行

- **WHEN** 请求携带有效 agent API Key 解析为 `MachinePrincipal`
- **THEN** Facade MUST 允许业务调用继续

### Requirement: 执行摘要只读能力

系统 SHALL 通过 agent 面提供只读执行摘要能力 `GET /api/agent/executions/{executionId}/summary`。该能力经 Facade 组合执行元数据（状态/时序/配置）与聚合结果（样本数/错误率/响应时间/吞吐）产出 `ExecutionSummary`。

#### Scenario: 查询存在的执行

- **WHEN** 携带有效身份请求已存在 `executionId` 的摘要
- **THEN** 响应 MUST 返回包含执行状态、时序信息与聚合结果指标的执行摘要
- **AND** 该数据 MUST 经 Facade 而非 controller 直连获取

#### Scenario: 查询不存在的执行

- **WHEN** 携带有效身份请求不存在 `executionId` 的摘要
- **THEN** 响应 MUST 返回不存在语义的错误（`NOT_FOUND`）
- **AND** MUST NOT 返回空的成功响应
