## Why

平台当前不存在请求级身份：`/api/auth/login` 只校验密码并返回 `AuthenticatedUser`，**不签发任何 token**；`SecurityConfiguration` 对所有请求 `permitAll`，没有过滤器，业务层无法知道「当前是谁」。在这种状态下无法安全地把平台能力开放给外部 Agent。因此在暴露任何 agent 面能力之前，必须先建立认证底座。

本 change 只解决「认证」（身份是否有效），不解决「授权」（能否访问某资源）。细粒度授权按项目/角色/工具控制作为后续按需任务，不在本次范围。

## What Changes

- 新增**平台用户 token**：`login` 成功后签发服务端 opaque token（存 DB、可吊销），随响应返回。
- 新增**统一鉴权过滤器**：校验 token，解析为 human `Principal` 注入请求上下文；缺失/失效返回 401。
- 新增 **agent API Key**：独立签发 / 吊销 / 过期；同一过滤链识别 API Key，解析为 machine `Principal`。
- 新增 **agent API Key 前端管理页面**：查看列表、签发（一次性展示明文供复制）、吊销，仅 ADMIN 可用。
- 新增**统一 `Principal` 抽象**：区分 human（username/roles）与 machine（apiKeyId/预留 scope）两种主体。
- **BREAKING**：`SecurityConfiguration` 从 `permitAll` 改为默认 `authenticated`，白名单仅 `/api/auth/login`、健康检查、OpenAPI。现有前端所有请求必须携带 token。
- 前端登录态适配：存储 token 并在请求头统一注入。
- 明确不做：角色 / 项目 / 工具级授权判定（scope 字段仅预留，不参与判定）。

## Capabilities

### New Capabilities
- `platform-authentication`: 平台请求级身份认证——平台用户 token 与 agent API Key 两种凭据，经统一过滤链解析为统一 `Principal`，全站强制认证与白名单。

### Modified Capabilities
<!-- 无既有 auth 相关 spec，本次不修改现有 capability 的 requirement。 -->

## Impact

- 后端：`identity`（新增 token、API Key 实体与服务）、`config/SecurityConfiguration`（过滤链改造）、`api/AuthController`（登录返回 token）。
- 前端：`frontend` 登录态存储与请求头注入；新增 Agent API Key 管理页面（仿 LLM 配置页模式）。
- 数据：新增持久化表（用户 token、agent API Key）。
- **兼容性**：启用全站强制认证后，所有现有 REST 调用需携带凭据（BREAKING），需前端同步改造后才能上线。
- 依赖：复用现有 Spring Security，不新增第三方依赖。
