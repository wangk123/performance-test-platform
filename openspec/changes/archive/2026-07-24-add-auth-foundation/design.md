## Context

平台当前无请求级身份：`api/AuthController#login` 仅调用 `PersistentAuthenticationService.authenticate` 校验密码并返回 `AuthenticatedUser`，**不签发 token**；`config/SecurityConfiguration` 对所有请求 `permitAll`，禁用 formLogin/httpBasic，无任何认证过滤器。18 个 Controller 直连领域 Service，Service 层不接收「当前主体」。

约束：
- 复用现有 Spring Security，不新增第三方依赖（项目工程约束）。
- 沿用既有 JPA `Persistent*Record` + `Persistent*Repository` 持久化模式。
- 现有 `identity` 已有 `AuthenticatedUser`、`SystemRole`、`PersistentAuthenticationService`、`PersistentUserAccountRepository`，可直接复用。
- 启用全站强制认证会破坏现有前端（前端目前登录后无 token 可带），需前端同批改造。
- Java 17；后端 Gradle 模块 `backend`。

## Goals / Non-Goals

**Goals:**
- 平台从「无请求级身份」变为「所有请求携带并校验身份」。
- 平台用户用简单可吊销的 token 全站认证。
- agent 面用独立可吊销的 API Key 认证。
- 两种凭据经同一过滤链解析为统一 `Principal(human|machine)` 注入请求上下文。

**Non-Goals:**
- 不做任何授权判定（项目/角色/工具级策略后移，`scope` 字段仅预留）。
- 不做 token refresh、滑动过期、单点登录、多因子。
- 不改造现有 18 个 Controller 的业务逻辑（仅让其运行在强制认证之后）。
- 不引入 OpenAPI（属后续 T4）；白名单先只放登录与健康检查，OpenAPI 端点待其引入时补入白名单。

## Decisions

### 决策 1：平台用户 token 用服务端 opaque token 存 DB，而非 JWT
- **选择**：随机不透明字符串作为 token，服务端只存其哈希 + userId + 过期时间 + 创建时间；每请求查库校验；登出=删除记录。
- **理由**：可即时吊销（登出、禁用用户即失效）；契合既有 JPA 持久化模式；与 agent API Key 的存储/吊销机制统一；避免 JWT 的密钥管理与吊销黑名单复杂度。多实例部署下 DB 天然共享。
- **备选**：无状态 JWT——省去查库，但吊销困难、需密钥轮换，与「简单 + 可吊销」目标不符。

### 决策 2：平台 token 与 agent API Key 共用一条 SecurityFilterChain
- **选择**：单一 `SecurityFilterChain`，在其中放置一个认证过滤器，按请求头选择解析平台 token 或 API Key，产出统一 `Principal`。
- **理由**：两类主体下游一致对待（都注入 `Principal`），避免两套并行身份机制；符合 brainstorming 阶段结论。
- **备选**：为 agent 面单独一条链——增加重复与维护成本，当前无差异化需求。

### 决策 3：凭据传输用不同请求头区分主体来源
- **选择**：平台用户 token 用 `Authorization: Bearer <token>`；agent API Key 用独立头 `X-API-Key: <key>`。
- **理由**：来源与生命周期不同，独立头便于过滤器区分与审计；避免靠前缀猜测主体类型。
- **备选**：统一 `Authorization` 靠前缀区分——解析更脆弱。

### 决策 4：API Key 与 token 均只存哈希
- **选择**：签发时生成随机明文（API Key 带可见前缀用于识别，如 `pak_...`），持久化 SHA-256 哈希；明文仅返回一次。
- **理由**：泄库不暴露可用凭据；前缀便于运维识别与审计。

### 决策 5：统一 Principal 通过 Spring Security 上下文注入
- **选择**：认证过滤器构造 `Authentication`，其 principal 为统一 `Principal`（human/machine 子类型），写入 `SecurityContextHolder`；下游按需读取。
- **理由**：走 Spring Security 标准通道，Controller/未来 Facade 统一获取，不自造并行机制。

### 决策 6：token 过期采用固定 TTL，无 refresh
- **选择**：签发时设固定过期时间（具体值实现时定，默认较长以降低体验成本，如数小时到 1 天），过期即需重新登录；登出立即删除。
- **理由**：契合「简单实现」；refresh/滑动过期属后续增强。

## Risks / Trade-offs

- **BREAKING 破坏现有前端** → 缓解：后端实现与前端登录态适配同批上线；上线前联调；回滚方案为将 `SecurityConfiguration` 临时切回宽松放行。
- **每请求查库校验 token 带来开销** → 缓解：token 哈希列建唯一索引，查询轻量；如成瓶颈后续再加缓存（本次不做）。
- **明文凭据泄露** → 缓解：只存哈希、明文一次性返回、API Key 支持即时吊销。
- **白名单遗漏导致健康检查/静态资源被拦** → 缓解：明确白名单清单并加测试覆盖白名单端点免认证。
- **明文 API Key 一次性展示的可用性** → 前端签发后一次性弹窗展示并提供复制；刷新不再显示，避免明文落库或重复展示。

## Migration Plan

1. 后端实现 token 与 API Key 认证过滤器、实体与服务，`SecurityConfiguration` 改默认 `authenticated` + 白名单。
2. `AuthController#login` 返回体新增 token 字段（不破坏原有用户字段）。
3. 前端登录态适配：保存 token，请求拦截器统一注入 `Authorization` 头。
4. 前后端同批部署。
5. 回滚：`SecurityConfiguration` 切回宽松放行（过滤器可保留但不强制），前端回退注入逻辑。

## Open Questions

- token 固定 TTL 的具体取值（默认候选：8 小时 / 24 小时）——实现时定，不阻塞设计。
- （已定）agent API Key 通过前端管理页面 + 后端 REST 接口进行签发/吊销/查看，仅 ADMIN 主体可用。
- `Principal` 的 `scope` 字段未来授权用途结构，留待 T2 细粒度授权设计。
