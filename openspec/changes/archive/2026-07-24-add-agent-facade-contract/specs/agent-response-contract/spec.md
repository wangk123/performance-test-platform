## ADDED Requirements

### Requirement: 统一响应封套

agent 面（`/api/agent/**`）所有响应 MUST 使用统一封套 `ApiResponse`，包含字段：`requestId`、`schemaVersion`、`data`、`error`、`warnings`、`truncated`、`nextCursor`。成功响应 MUST 填充 `data` 且 `error` 为 null；失败响应 MUST 填充 `error` 且 `data` 为 null。分页相关字段 `truncated`、`nextCursor` 在本能力范围内 MUST 恒为 null（预留给后续有界查询能力），`warnings` MUST 为空列表或省略。

#### Scenario: 成功响应结构

- **WHEN** agent 面某只读请求成功
- **THEN** 响应 MUST 含非空 `requestId` 与 `schemaVersion`
- **AND** `data` MUST 非空、`error` MUST 为 null
- **AND** `truncated` 与 `nextCursor` MUST 为 null

#### Scenario: 每个响应带唯一 requestId

- **WHEN** 连续两次请求 agent 面
- **THEN** 两次响应的 `requestId` MUST 互不相同

### Requirement: 稳定错误码枚举

系统 SHALL 定义面向 Agent 的稳定错误码枚举，MUST 包含全集：`AUTHENTICATION_FAILED`、`ACCESS_DENIED`、`NOT_FOUND`、`DATA_SOURCE_UNAVAILABLE`、`QUERY_TOO_LARGE`、`TIMEOUT`、`RATE_LIMITED`、`IDEMPOTENCY_CONFLICT`、`EXECUTION_CONFLICT`、`VALIDATION_FAILED`、`INTERNAL_ERROR`。错误码 MUST 与业务异常稳定映射，同一场景 MUST 返回同一错误码。

#### Scenario: 认证失败映射

- **WHEN** agent 面请求身份无效
- **THEN** 响应 `error.code` MUST 为 `AUTHENTICATION_FAILED`
- **AND** HTTP 状态 MUST 为 401

#### Scenario: 资源不存在映射

- **WHEN** agent 面请求不存在的资源
- **THEN** 响应 `error.code` MUST 为 `NOT_FOUND`
- **AND** HTTP 状态 MUST 为 404

#### Scenario: 参数校验失败映射

- **WHEN** agent 面请求参数非法
- **THEN** 响应 `error.code` MUST 为 `VALIDATION_FAILED`
- **AND** HTTP 状态 MUST 为 400

### Requirement: agent 面异常映射隔离

系统 SHALL 以包作用域异常处理器（仅作用于 agent 面包）将异常映射为封套式 `error`。现有 UI 面（`/api/**`）异常处理与裸 `ApiError` 响应 MUST 不受影响。

#### Scenario: agent 面错误被封套

- **WHEN** agent 面请求触发业务异常
- **THEN** 响应体 MUST 为统一封套结构（含 `requestId`/`schemaVersion`/`error`）

#### Scenario: UI 面响应不变

- **WHEN** 现有 `/api/**` UI 接口触发同类异常
- **THEN** 响应体 MUST 保持原有裸 `ApiError(code, message)` 结构

### Requirement: OpenAPI 生成

系统 SHALL 通过 springdoc 生成 OpenAPI 规范，agent 面 MUST 归入独立分组（路径匹配 `/api/agent/**`）。领域 DTO SHALL 标注 `schemaVersion`。

#### Scenario: 可生成完整 OpenAPI

- **WHEN** 访问 OpenAPI 端点
- **THEN** MUST 返回可解析的完整 OpenAPI spec
- **AND** MUST 包含 agent 面执行摘要接口的定义
