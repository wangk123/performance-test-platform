## Why

平台当前 18 个 controller 直接返回裸 DTO、直连各 service/Repository，缺少面向外部 Agent 的稳定业务入口与统一响应契约。M2 要在认证底座（T1，已完成）之上，立起唯一强制业务入口 Facade 与面向 Agent 的统一响应封套/错误码，让后续数据链、分析、MCP 接入有稳定地基。

## What Changes

- 新建并行 agent 面 `/api/agent/**`（独立包），现有 `/api/**` UI 接口、`PlatformExceptionHandler`、前端**零改动**。
- 新建 `facade` 层，作为 agent 面唯一强制业务入口；M2 只立**数据组**入口 + Facade 基础设施（主体校验入口、审计钩子占位、授权钩子占位预留 T2），其余能力组用到再加。
- 打通一条只读垂直切片：`GET /api/agent/executions/{executionId}/summary`，Facade 复用现有 `ScenarioExecutionService`（不复制业务规则），不直连 Repository/文件/监控。
- 定义面向 Agent 的统一响应封套（`requestId`/`schemaVersion`/`data|error`/`warnings`/`truncated`/`nextCursor`，分页字段先定义后休眠）。
- 定义稳定错误码全枚举；M2 仅映射可达场景（认证/权限/不存在/参数校验/内部错）。
- 新增包作用域 `@RestControllerAdvice`（仅 agent 包），产出封套式错误；UI 面不受影响。
- 引入 springdoc 生成 OpenAPI（T4 明确许可的新依赖），agent 面单独分组。

## Capabilities

### New Capabilities

- `agent-facade`: agent 面唯一强制业务入口层，统一装配主体校验与钩子；约束调用方不得绕过 Facade 直连 Repository/文件/监控；含一条只读能力（执行摘要）作为骨架验证。
- `agent-response-contract`: 面向 Agent 的统一响应封套、稳定错误码枚举、包作用域异常映射与 OpenAPI 生成。

### Modified Capabilities

<!-- 无：不改动现有 spec 的既有需求 -->

## Impact

- 新增代码：`backend` 新增 agent 面 controller 包、`facade` 包、响应契约与错误码、包作用域 `@RestControllerAdvice`。
- 复用不改：`ScenarioExecutionService`、`identity`（T1 Principal/API Key）保持现状。
- 新增依赖：springdoc（仅此一项，T4 许可）。
- 不影响：现有 `/api/**` UI 接口契约、`PlatformExceptionHandler`、前端。
