# platform-authentication

## Purpose

平台请求级身份认证：平台用户 opaque token 与 agent API Key 两种凭据，经统一过滤链解析为 `Principal(human|machine)`，全站强制认证（白名单除外）。

## Requirements

### Requirement: 平台用户登录签发 Token

平台用户使用用户名和密码登录成功后，系统 SHALL 签发一个服务端 opaque token 并随响应返回。密码校验失败时 SHALL 返回认证失败且不签发 token。

#### Scenario: 登录成功签发 token
- **WHEN** 用户以正确的用户名和密码调用 `POST /api/auth/login`
- **THEN** 系统返回该用户身份信息与一个新签发的 token

#### Scenario: 密码错误不签发 token
- **WHEN** 用户以错误的密码调用 `POST /api/auth/login`
- **THEN** 系统返回认证失败错误，且不签发任何 token

### Requirement: 平台用户 Token 请求认证

除白名单端点外，所有请求 SHALL 携带有效的平台用户 token 才能访问。系统 MUST 校验 token 存在且未过期、未被吊销，并解析出对应用户主体。

#### Scenario: 携带有效 token 访问
- **WHEN** 请求头携带有效且未过期的平台用户 token 访问受保护端点
- **THEN** 系统放行请求，并在请求上下文中提供该用户主体

#### Scenario: 缺失 token 访问受保护端点
- **WHEN** 请求未携带任何凭据访问受保护端点
- **THEN** 系统返回 401 未认证

#### Scenario: 失效 token 访问
- **WHEN** 请求携带已过期或已吊销的 token 访问受保护端点
- **THEN** 系统返回 401 未认证

#### Scenario: 登出后 token 失效
- **WHEN** 用户登出使其 token 失效后，再用该 token 访问受保护端点
- **THEN** 系统返回 401 未认证

### Requirement: Agent API Key 管理

系统 SHALL 支持为 agent 主体签发、吊销 API Key，并支持设置过期时间。API Key 明文 MUST 仅在签发时返回一次，服务端只持久化其哈希，不可逆存明文。

#### Scenario: 签发 API Key
- **WHEN** 管理者签发一个新的 agent API Key
- **THEN** 系统返回一次性明文 API Key，并持久化其哈希与元数据（标识、过期时间、创建时间）

#### Scenario: 吊销 API Key
- **WHEN** 管理者吊销一个已存在的 API Key
- **THEN** 该 API Key 后续用于认证时不再被接受

### Requirement: Agent API Key 请求认证

agent 请求 SHALL 通过独立的 API Key 请求头进行认证。系统 MUST 校验 API Key 哈希匹配、未过期、未被吊销，并解析出对应的 machine 主体。

#### Scenario: 携带有效 API Key 访问
- **WHEN** 请求携带有效且未过期的 API Key 访问受保护端点
- **THEN** 系统放行请求，并在请求上下文中提供 machine 主体

#### Scenario: 已吊销 API Key 访问
- **WHEN** 请求携带已吊销或已过期的 API Key 访问受保护端点
- **THEN** 系统返回 401 未认证

### Requirement: Agent API Key 管理界面

平台 SHALL 提供前端管理界面用于查看、签发和吊销 agent API Key。签发成功后界面 MUST 一次性展示明文 API Key 供复制，刷新后不再显示明文；列表 MUST 不包含明文。管理界面 SHALL 仅对 ADMIN 主体可用。

#### Scenario: 查看 API Key 列表
- **WHEN** 管理者打开 Agent API Key 管理页面
- **THEN** 界面展示各 Key 的前缀、创建时间、过期时间与状态（有效/已吊销/已过期），且不含明文

#### Scenario: 签发并一次性展示明文
- **WHEN** 管理者在页面签发新的 API Key
- **THEN** 界面一次性展示明文 Key 供复制，关闭或刷新后不再显示明文

#### Scenario: 吊销 API Key
- **WHEN** 管理者在页面吊销某个 API Key
- **THEN** 该 Key 状态变为已吊销，且后续用于认证时不再被接受

### Requirement: 统一 Principal 主体

系统 SHALL 将平台用户 token 与 agent API Key 两种凭据解析为统一的 `Principal` 抽象，区分 human（携带 username 与 roles）与 machine（携带 apiKeyId 与预留 scope）两类主体，并注入请求上下文供下游使用。本 change 不基于 Principal 做任何授权判定。

#### Scenario: human 主体解析
- **WHEN** 请求通过平台用户 token 认证
- **THEN** 请求上下文中的 Principal 类型为 human，且包含 username 与 roles

#### Scenario: machine 主体解析
- **WHEN** 请求通过 agent API Key 认证
- **THEN** 请求上下文中的 Principal 类型为 machine，且包含 apiKeyId

### Requirement: 全站强制认证与白名单

系统 SHALL 默认要求所有端点通过认证，仅白名单端点（登录、健康检查、OpenAPI 文档）免认证。此为对现有行为的 BREAKING 变更：现有前端所有请求须携带 token。

#### Scenario: 访问白名单端点免认证
- **WHEN** 未认证请求访问白名单端点（如 `POST /api/auth/login`）
- **THEN** 系统正常放行，不返回 401

#### Scenario: 访问非白名单端点须认证
- **WHEN** 未认证请求访问任一非白名单业务端点
- **THEN** 系统返回 401 未认证
