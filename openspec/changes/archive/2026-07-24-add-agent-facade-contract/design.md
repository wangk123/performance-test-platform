## Context

M1 认证底座（T1）已完成：所有请求携带并校验身份，解析为统一 `Principal`（`HumanPrincipal` / `MachinePrincipal`），由 `AuthenticationFilter` 注入 `SecurityContext`。当前 18 个 controller 位于 `/api/**`，直接返回裸 DTO 并直连各 service/Repository；错误统一走 `PlatformExceptionHandler` → `ApiError(code, message)`；前端依赖裸 DTO 结构。

M2（T3 + T4）要在此之上立起：面向 Agent 的唯一强制业务入口 `facade`，以及统一响应封套 / 稳定错误码 / OpenAPI。约束是**不破坏现有 UI 契约与前端**，且**不做 18 个 controller 全量迁移**——只在接第一个 agent 只读能力时立起骨架。

现成可复用只读链路：`ScenarioExecutionService.getExecution(id)`（状态/时序/配置）+ `getResult(id)`（聚合结果 `TaskExecutionResult`：样本数/错误/RT/吞吐），天然构成「执行摘要」。

## Goals / Non-Goals

**Goals:**
- 立起并行 agent 面 `/api/agent/**`（独立包）与 `facade` 层，Facade 成为 agent 面唯一业务入口。
- 打通一条只读垂直切片（执行摘要），验证 Facade 边界与响应契约端到端。
- 定义面向 Agent 的响应封套（分页字段先定义后休眠）、稳定错误码全枚举、包作用域异常映射、OpenAPI 生成。

**Non-Goals:**
- 不迁移现有 18 个 controller，不改动 `/api/**` UI 契约、`PlatformExceptionHandler`、前端。
- 不做分页/游标/预算（T5）、不做数据链关联（T6）、不做授权策略（T2，仅预留钩子）、不做限流/脱敏/审计落地（T10，仅预留钩子）。
- 不做写操作、幂等、异步任务（T8）。

## Decisions

### D1：并行 agent 面而非改造现有接口
新建 `/api/agent/**`（独立包 `com.yr.perftest.platform.agent`），只有它走 Facade + 封套；现有 `/api/**` 完全不动。
- 理由：前端依赖裸 DTO，全量改造会破坏 UI 契约且超出 M2 范围。并行面隔离风险、可独立演进。
- 备选：改造现有 controller 全套封套（否决：破坏前端）；混合就地加（否决：命名空间混乱、边界不清）。

### D2：Facade 只立数据组 + 基础设施（YAGNI）
`facade` 包内先建 Facade 基础设施（主体校验入口、审计钩子占位、授权钩子占位）与一个数据组入口（`DataFacade`），其余 5 组（资产/执行/分析/取证/验证）用到再加。
- 理由：T3 明确「接第一个只读能力时立起骨架，不做全量」。空接口无消费者违反 YAGNI。
- 备选：6 组空骨架全建（否决：无消费者的空接口）。

### D3：Facade 复用既有 service，不复制业务规则
`DataFacade` 组合调用 `ScenarioExecutionService.getExecution` + `getResult`，产出 `ExecutionSummary`。Facade 只做装配（主体校验 + 钩子 + 组装），业务规则留在既有 service。
- 理由：避免业务逻辑双写；Facade 职责是入口收敛与横切装配。

### D4：唯一入口约束（分层边界）
agent controller 只依赖 `facade`，不得 import Repository / 文件 API / 监控客户端。以测试断言 agent 包不出现对 `*Repository` / `java.nio.file` / Prometheus 客户端的直接依赖来守护。
- 理由：T3 验收「调用方无法绕过 Facade 直连」。

### D5：响应封套字段先定全、分页休眠
`ApiResponse<T>`：`requestId`、`schemaVersion`、`data`、`error`、`warnings`、`truncated`、`nextCursor`。M2 单对象只读，`truncated`/`nextCursor` 恒为 null，`warnings` 恒为空。
- 理由：契约一步到位，T5 接分页时不改结构，避免 schemaVersion 破坏性升级。
- 备选：最小封套后续再加字段（否决：契约不稳定，Agent 侧要重复适配）。

### D6：错误码全枚举、M2 仅映射可达
`AgentErrorCode` 枚举含全集：`AUTHENTICATION_FAILED`、`ACCESS_DENIED`、`NOT_FOUND`、`DATA_SOURCE_UNAVAILABLE`、`QUERY_TOO_LARGE`、`TIMEOUT`、`RATE_LIMITED`、`IDEMPOTENCY_CONFLICT`、`EXECUTION_CONFLICT`、`VALIDATION_FAILED`、`INTERNAL_ERROR`。M2 仅映射：认证/权限/不存在/参数校验/内部错。
- 理由：稳定错误语义是 Agent 侧契约；后续任务只新增映射不改枚举。

### D7：包作用域异常映射
新增 `@RestControllerAdvice(basePackages = "com.yr.perftest.platform.agent")`，将异常映射为封套式 `error`；现有 `PlatformExceptionHandler` 保持默认作用域服务 UI 面。
- 理由：agent 面要封套错误，UI 面要裸 `ApiError`，包作用域天然隔离。
- 备选：改造现有 handler 按路径分支（否决：单类双契约，易错）；controller 内手动 try/catch（否决：重复、易漏）。

### D8：springdoc 生成 OpenAPI，agent 面分组
引入 springdoc，agent 面用独立 `GroupedOpenApi`（路径匹配 `/api/agent/**`）。领域 DTO 标注 `schemaVersion`。
- 理由：T4 明确许可该新依赖；分组避免与 UI 面接口混淆。

## Risks / Trade-offs

- [并行面导致同一读能力两处入口] → M2 仅一条只读切片，范围可控；长期以 Facade 为准，UI 面按需再收敛（不在本变更）。
- [封套预留字段 null 让 Agent 侧误判分页] → 在 spec/OpenAPI 明确 M2 恒为 null 的语义；schemaVersion 标注当前版本。
- [springdoc 与现有 Spring Security 过滤链冲突（OpenAPI 端点需可达）] → 将 OpenAPI/swagger 端点加入认证白名单或以受控方式暴露，并以测试验证可生成完整 spec。
- [分层约束仅靠约定易被绕过] → 用依赖方向测试（agent 包不依赖 Repository/文件/监控）自动守护。
