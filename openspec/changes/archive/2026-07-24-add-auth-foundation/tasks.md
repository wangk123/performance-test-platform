# 实现任务：认证底座（add-auth-foundation）

> 依据 `proposal.md` / `design.md` / `specs/platform-authentication/spec.md`。
> 约定：后端模块 `backend`，JUnit5 + spring-boot-starter-test，测试与主代码同包镜像。
> 运行测试：`JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ ./gradlew :backend:test`（下文简称「运行后端测试」）。
> 每个逻辑单元遵循 TDD：先写失败测试 → 实现 → 测试通过 → 提交。

## 1. 统一 Principal 抽象

- [x] 1.1 定义 `identity/Principal`（sealed interface）与 `HumanPrincipal`(username, roles) / `MachinePrincipal`(apiKeyId, scope 预留) 两个 record；本 change 不做授权判定
  - Files: `backend/src/main/java/.../identity/Principal.java`、`HumanPrincipal.java`、`MachinePrincipal.java`
  - Done: 两类主体可构造并暴露各自字段；`scope` 字段存在但不被消费
- [x] 1.2 先失败测试：`identity/PrincipalTest` 断言 human 含 username/roles、machine 含 apiKeyId
- [x] 1.3 实现至测试通过；提交（提交按仓库规则暂缓）

## 2. 平台用户 Token：持久化与服务

- [x] 2.1 先失败测试：`identity/AuthTokenServiceTest` 覆盖——签发返回明文且存哈希、按明文校验命中、过期/吊销后校验失败、登出删除后校验失败
  - Files(test): `backend/src/test/java/.../identity/AuthTokenServiceTest.java`
- [x] 2.2 新增持久化：`PersistentAuthTokenRecord`(id, userId, tokenHash 唯一索引, expiresAt, createdAt) + `PersistentAuthTokenRepository`
  - Files: `backend/src/main/java/.../identity/PersistentAuthTokenRecord.java`、`PersistentAuthTokenRepository.java`
- [x] 2.3 新增 `AuthTokenService`：`issue(userId)` 生成随机明文 + 存 SHA-256 哈希 + 固定 TTL；`resolve(plainToken)` → `Optional<HumanPrincipal>`（校验存在/未过期/未吊销）；`revoke(plainToken)`（登出）
  - Files: `backend/src/main/java/.../identity/AuthTokenService.java`
  - Done: 复用现有 `PersistentUserAccountRepository`/`SystemRole` 解析用户 roles 填充 `HumanPrincipal`
- [x] 2.4 运行后端测试至 2.1 全绿；提交（提交按仓库规则暂缓）

## 3. Agent API Key：持久化与服务

- [x] 3.1 先失败测试：`identity/AgentApiKeyServiceTest` 覆盖——签发返回带前缀明文且仅存哈希、有效 Key 解析出 MachinePrincipal、吊销/过期后解析失败
  - Files(test): `backend/src/test/java/.../identity/AgentApiKeyServiceTest.java`
- [x] 3.2 新增持久化：`PersistentAgentApiKeyRecord`(id, keyHash 唯一索引, prefix, scope 预留, expiresAt, revokedAt, createdAt) + `PersistentAgentApiKeyRepository`
  - Files: `backend/src/main/java/.../identity/PersistentAgentApiKeyRecord.java`、`PersistentAgentApiKeyRepository.java`
- [x] 3.3 新增 `AgentApiKeyService`：`issue(...)` 生成 `pak_` 前缀随机明文 + 存 SHA-256 哈希（明文仅返回一次）；`resolve(plainKey)` → `Optional<MachinePrincipal>`；`revoke(id)`
  - Files: `backend/src/main/java/.../identity/AgentApiKeyService.java`
- [x] 3.4 运行后端测试至 3.1 全绿；提交（提交按仓库规则暂缓）

## 4. 统一认证过滤器

- [x] 4.1 先失败测试：`config/AuthenticationFilterTest`（`@WebMvcTest` 或 MockMvc）——`Authorization: Bearer <token>` 解析 human；`X-API-Key: <key>` 解析 machine；两者都无 → 无 Principal（交由 SecurityConfig 拦截）
  - Files(test): `backend/src/test/java/.../config/AuthenticationFilterTest.java`
- [x] 4.2 实现 `config/AuthenticationFilter`（OncePerRequestFilter）：优先读 `Authorization: Bearer` 交 `AuthTokenService.resolve`，否则读 `X-API-Key` 交 `AgentApiKeyService.resolve`；命中则构造 `Authentication`（principal=统一 `Principal`）写入 `SecurityContextHolder`
  - Files: `backend/src/main/java/.../config/AuthenticationFilter.java`
- [x] 4.3 运行后端测试至 4.1 全绿；提交（提交按仓库规则暂缓）

## 5. SecurityConfiguration 全站强制认证 + 白名单

- [x] 5.1 先失败测试：`config/SecurityConfigurationTest`（MockMvc）——白名单端点(`POST /api/auth/login`、健康检查)未认证返回非 401；任一非白名单端点未认证返回 401；带有效 token/API Key 返回非 401
  - Files(test): `backend/src/test/java/.../config/SecurityConfigurationTest.java`
- [x] 5.2 改造 `config/SecurityConfiguration`：`anyRequest().authenticated()`，白名单 `permitAll`（`/api/auth/login`、健康检查、（OpenAPI 端点待 T4 引入时补入）），注册 `AuthenticationFilter`；保留 csrf disable
  - Files: `backend/src/main/java/.../config/SecurityConfiguration.java`
- [x] 5.3 运行后端测试至 5.1 全绿；提交（提交按仓库规则暂缓）

## 6. 登录返回 Token 与登出

- [x] 6.1 先失败测试：`api/AuthControllerTest`——登录成功响应含用户信息 + 非空 token；密码错误不签发（沿用现有认证失败语义）；`POST /api/auth/logout` 使 token 失效后再用该 token 访问受保护端点返回 401
  - Files(test): `backend/src/test/java/.../api/AuthControllerTest.java`
- [x] 6.2 改造 `api/AuthController#login`：认证成功后调用 `AuthTokenService.issue` 并在响应体新增 `token` 字段（保留原有用户字段，不破坏结构）；新增 `logout` 调用 `AuthTokenService.revoke`
  - Files: `backend/src/main/java/.../api/AuthController.java`
- [x] 6.3 运行后端测试至 6.1 全绿；提交（提交按仓库规则暂缓）

## 7. Agent API Key 管理入口（最小可用）

- [x] 7.1 先失败测试：`api/AgentApiKeyControllerTest`——签发返回一次性明文；吊销后该 Key 认证失败；管理端点自身受保护（需 ADMIN 主体）
  - Files(test): `backend/src/test/java/.../api/AgentApiKeyControllerTest.java`
- [x] 7.2 新增 `api/AgentApiKeyController`：签发 / 吊销 / 列出（不含明文）最小 REST 接口，调用 `AgentApiKeyService`
  - Files: `backend/src/main/java/.../api/AgentApiKeyController.java`
  - Done: 仅限制为 ADMIN 主体可调用（粗粒度，基于 Principal 类型/角色的最小判断，不属细粒度授权 T2）
- [x] 7.3 运行后端测试至 7.1 全绿；提交（提交按仓库规则暂缓）

## 8. 前端登录态适配

- [x] 8.1 登录成功后保存 token（现有登录流程处），并在 HTTP 客户端拦截器统一注入 `Authorization: Bearer <token>`
  - Files: `frontend/src/api/auth.ts`、`frontend/src/api/http.ts`、`frontend/src/composables/useAuth.ts`
- [x] 8.2 处理 401：清除本地 token 并跳转登录页
- [x] 8.3 手工验收：前端登录后正常访问受保护接口；登出或 token 失效后被拦截跳登录

## 9. 前端 Agent API Key 管理页面

> 遵循既有设计系统（ant-design-vue）与 `components/settings/*Panel.vue` 模式（参照 `LlmProvidersPanel.vue`）；实现前按前端 UI 规则加载 `frontend-design` skill。

- [x] 9.1 新增 `frontend/src/api/agent-api-keys.ts`：`listApiKeys` / `issueApiKey` / `revokeApiKey`，复用 `src/api/http.ts` 的 `request`
- [x] 9.2 新增 `frontend/src/components/settings/AgentApiKeysPanel.vue`（仿 `LlmProvidersPanel.vue`）：列表展示前缀/创建时间/过期时间/状态（有效/已吊销/已过期，不含明文）；签发弹窗一次性展示明文 Key + 复制按钮，关闭后不再显示；吊销二次确认
- [x] 9.3 挂载入口与路由：在系统配置 `SettingsView` 新增 tab「Agent API Key」，仅 ADMIN 可见
- [x] 9.4 手工验收：签发→一次性展示明文→列表出现该 Key→吊销后状态更新，且该 Key 再用于认证返回 401

## 10. 收尾验证

- [x] 10.1 运行后端全量测试全绿：`gradle :backend:test` BUILD SUCCESSFUL
- [x] 10.2 对照 `spec.md` 逐条 Scenario 核对已有对应测试覆盖（登录签发、缺失/失效/登出 token→401、API Key 有效/吊销、human/machine 主体解析、白名单免认证/非白名单 401、API Key 管理界面列表/签发/吊销）
- [x] 10.3 前后端联调验证 BREAKING 迁移路径（前端带 token 全流程可用）
