# 实现任务：agent Facade + 响应契约（M2）

> 全局约束：
> - Java 17，运行后端命令须 `JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/`。
> - 统一验证命令：`JAVA_HOME=<jdk17> ./gradlew :backend:test`（在仓库根执行）。
> - 不改动 `/api/**` UI 接口、`PlatformExceptionHandler`、前端。
> - 新增依赖仅 springdoc（T4 许可）。
> - agent 面包：`com.yr.perftest.platform.agent`；Facade 包：`com.yr.perftest.platform.facade`。
> - TDD：每组先写失败测试→跑红→最小实现→跑绿。

## 1. 响应契约基础（封套 + 错误码枚举）

- [x] 1.1 先失败测试：`ApiResponse` 结构
  - Files（Test）：`backend/src/test/java/com/yr/perftest/platform/agent/contract/ApiResponseTest.java`
  - 行为：`ApiResponse.success(requestId, schemaVersion, data)` 产出 `data` 非空、`error` 为 null、`truncated`/`nextCursor` 为 null、`warnings` 为空；`ApiResponse.error(requestId, schemaVersion, error)` 产出 `error` 非空、`data` 为 null。
  - 验证：`JAVA_HOME=<jdk17> ./gradlew :backend:test --tests '*ApiResponseTest'` → 编译失败/红。

- [x] 1.2 实现 `AgentErrorCode` 枚举（全集）+ `ApiResponse<T>` + `ApiErrorBody` 封套
  - Files（Create）：`backend/src/main/java/com/yr/perftest/platform/agent/contract/AgentErrorCode.java`、`.../contract/ApiResponse.java`、`.../contract/ApiErrorBody.java`
  - 行为：枚举含 `AUTHENTICATION_FAILED/ACCESS_DENIED/NOT_FOUND/DATA_SOURCE_UNAVAILABLE/QUERY_TOO_LARGE/TIMEOUT/RATE_LIMITED/IDEMPOTENCY_CONFLICT/EXECUTION_CONFLICT/VALIDATION_FAILED/INTERNAL_ERROR`；封套字段 `requestId/schemaVersion/data/error/warnings/truncated/nextCursor`，分页字段本变更恒 null。
  - 验证：`... --tests '*ApiResponseTest'` → 绿。

## 2. agent 面骨架 + 包作用域异常映射

- [x] 2.1 先失败测试：agent 面异常被封套、UI 面不受影响
  - Files（Test）：`backend/src/test/java/com/yr/perftest/platform/agent/AgentExceptionHandlerTest.java`
  - 行为：向 agent 面探针端点触发 `NOT_FOUND`/`VALIDATION_FAILED`/`AUTHENTICATION_FAILED` → 响应体为封套结构且 `error.code` 稳定映射、HTTP 状态分别 404/400/401；对照现有某 `/api/**` 接口同类异常仍为裸 `ApiError`。
  - 验证：`... --tests '*AgentExceptionHandlerTest'` → 红。

- [x] 2.2 实现包作用域 `@RestControllerAdvice(basePackages="com.yr.perftest.platform.agent")`
  - Files（Create）：`backend/src/main/java/com/yr/perftest/platform/agent/AgentExceptionHandler.java`
  - 行为：将 `AuthenticationException`→`AUTHENTICATION_FAILED`(401)、`ExecutionValidationException`/资源缺失→`NOT_FOUND`(404)、`MethodArgumentNotValidException`→`VALIDATION_FAILED`(400)、兜底→`INTERNAL_ERROR`(500)，均包成 `ApiResponse.error`；生成 `requestId`。现有 `PlatformExceptionHandler` 不动。
  - 验证：`... --tests '*AgentExceptionHandlerTest'` → 绿；且 `... --tests '*PlatformApiBehaviorTest'` 仍绿（UI 未受影响）。

## 3. Facade 层：主体校验 + 数据组入口

- [x] 3.1 先失败测试：Facade 主体校验
  - Files（Test）：`backend/src/test/java/com/yr/perftest/platform/facade/FacadeSubjectGuardTest.java`
  - 行为：无有效 `Principal` 时 Facade 调用抛认证语义异常、业务 service 不被触达；有效 `MachinePrincipal` 时放行。
  - 验证：`... --tests '*FacadeSubjectGuardTest'` → 红。

- [x] 3.2 实现 Facade 基础设施 + `DataFacade`
  - Files（Create）：`backend/src/main/java/com/yr/perftest/platform/facade/FacadeContext.java`（当前主体 + 钩子占位）、`.../facade/FacadeGuard.java`（主体校验入口，审计/授权钩子占位）、`.../facade/DataFacade.java`
  - 行为：`FacadeGuard` 在调用前校验主体（复用 T1 `Principal`），审计钩子与授权钩子留空占位（授权待 T2）；`DataFacade.getExecutionSummary(executionId)` 经 guard 后调用既有 `ScenarioExecutionService`。
  - 验证：`... --tests '*FacadeSubjectGuardTest'` → 绿。

## 4. 执行摘要只读切片（端到端）

- [x] 4.1 先失败测试：`GET /api/agent/executions/{id}/summary`
  - Files（Test）：`backend/src/test/java/com/yr/perftest/platform/agent/AgentExecutionSummaryApiTest.java`
  - 行为：带有效 API Key 请求存在的 execution → 200 + 封套 `data` 含状态/时序/聚合指标；不存在 id → 404 + `error.code=NOT_FOUND`；无身份 → 401 + `AUTHENTICATION_FAILED`。
  - 验证：`... --tests '*AgentExecutionSummaryApiTest'` → 红。

- [x] 4.2 实现 `ExecutionSummary` DTO + `AgentExecutionController`
  - Files（Create）：`backend/src/main/java/com/yr/perftest/platform/agent/execution/ExecutionSummary.java`（标注 `schemaVersion`）、`.../agent/execution/AgentExecutionController.java`（`@RequestMapping("/api/agent/executions")`）
  - 行为：controller 仅依赖 `DataFacade`；`DataFacade` 组合 `ScenarioExecutionService.getExecution`+`getResult` → `ExecutionSummary`；返回 `ApiResponse.success` 封套。
  - 验证：`... --tests '*AgentExecutionSummaryApiTest'` → 绿。

## 5. 分层约束守护

- [x] 5.1 先失败测试：agent 包不直连底层资源
  - Files（Test）：`backend/src/test/java/com/yr/perftest/platform/agent/AgentLayeringConstraintTest.java`
  - 行为：断言 `com.yr.perftest.platform.agent` 包内类不直接依赖 `*Repository`、`java.nio.file` 文件 API、Prometheus 客户端（只允许经 `facade`）。可用反射/源码扫描或 ArchUnit（若已在依赖内，否则用简单源码/字节码扫描，不新增依赖）。
  - 验证：`... --tests '*AgentLayeringConstraintTest'` → 红（若初始实现有越界）或直接绿（若切片已合规，仍保留为回归守护）。

- [x] 5.2 修正越界依赖（若有），使约束测试通过
  - Files（Modify）：按测试反馈调整 `agent` 包内 import
  - 验证：`... --tests '*AgentLayeringConstraintTest'` → 绿。

## 6. OpenAPI（springdoc）

- [x] 6.1 先失败测试：OpenAPI 可生成且含 agent 面接口
  - Files（Test）：`backend/src/test/java/com/yr/perftest/platform/agent/AgentOpenApiTest.java`
  - 行为：访问 OpenAPI 端点返回可解析 spec，且包含 `/api/agent/executions/{executionId}/summary`。
  - 验证：`... --tests '*AgentOpenApiTest'` → 红。

- [x] 6.2 引入 springdoc + agent 分组，暴露 OpenAPI 端点
  - Files（Modify）：`backend/build.gradle`（加 springdoc 依赖）、`backend/src/main/java/com/yr/perftest/platform/config/SecurityConfiguration.java`（OpenAPI/swagger 端点白名单）
  - Files（Create）：`backend/src/main/java/com/yr/perftest/platform/agent/AgentOpenApiConfig.java`（`GroupedOpenApi` 匹配 `/api/agent/**`）
  - 行为：OpenAPI 端点可达；agent 面归入独立分组。
  - 验证：`... --tests '*AgentOpenApiTest'` → 绿。

## 7. 收尾验证

- [x] 7.1 全量后端测试通过
  - 验证：`JAVA_HOME=<jdk17> ./gradlew :backend:test` 全绿（含既有 UI/认证用例，证明零回归）。
- [x] 7.2 `openspec validate add-agent-facade-contract --strict` 通过
