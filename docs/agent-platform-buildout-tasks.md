# Agent 化性能平台建设任务清单

> 目标策略：共同底座优先 + 先走方案 2（平台作为工具集，经 REST/MCP/SKILL 被外部 Agent 驱动）。
> 安全策略：认证先行、授权后移。平台用户用简单 token 全站认证；agent 面用独立 API Key；细粒度授权（项目/角色/工具级）待需要时再做。
> 明确不做：平台内通用 Agent Runtime、Tool Calling、多轮会话、上下文压缩（交给外部 Agent）。
> 本清单基于实际代码库现状编写，不引用头脑风暴期的 openspec change 文档。

---

## L0 里程碑总览

| 里程碑 | 目标 | 任务 | 状态 |
|---|---|---|---|
| M1 认证底座 | 平台与 agent 面获得请求级身份（简单 token + API Key） | T1 | ✅ 完成（`add-auth-foundation` 已归档） |
| M2 业务入口与契约 | 业务入口收敛到 Facade，agent 面统一响应契约 | T3 T4 | ✅ 完成（`add-agent-facade-contract` 已归档） |
| M3 数据链与分析 | 数据可关联、可下钻，产出确定性事实 | T5 T6 T7 T8 | 进行中 |
| M4 闭环与治理 | 取证/验证闭环 + 脱敏/审计/限流 | T9 T10 T11(起步) | 待开始 |
| M5 外部 Agent 接入 | MCP + Skill，真实 Claude Code 端到端验收 | T12 T13 T11(持续) | 待开始 |

> 按需/后移：T2 细粒度授权。不绑定里程碑，待真正需要按项目/角色/工具控制访问时再启动。

### 任务索引

| # | 任务 | 涉及模块 | 依赖 | 类型 | 状态 |
|---|---|---|---|---|---|
| T1 | 认证底座：平台 token + agent API Key + 统一 Principal | `identity` `config` `api` | — | 阻塞 | ✅ 完成 |
| T3 | Agent-ready Facade | `facade`(新) | T1 | 阻塞 | ✅ 完成 |
| T4 | 统一响应契约 + 错误码 + OpenAPI | `agent` `facade` `config` | T3 | 底座 | ✅ 完成 |
| T5 | 有界查询 / 分页 / 数据可用性 | `execution` `monitoring` `report` | T4 | 底座 | ✅ 完成 |
| T6 | 数据链关联骨架（关联键 + 时钟对齐） | `evidence`(新) | T5 | 底座 | ✅ 完成 |
| T7 | 确定性分析 | `analysis`(新) | T6 | 底座 | ✅ 完成 |
| T8 | 压测执行工具化（幂等 / 预检 / 异步任务） | `execution` `task` | T4 | 底座 | ✅ 完成 |
| T9 | 补充取证 + 优化验证 | `execution` `facade` | T7 T8 | 底座 | 待开始 |
| T10 | 治理（脱敏 / 审计 / 限流） | 跨模块 | T3 | 底座 | 待开始 |
| T11 | 深度证据逐源接入 | `evidence` `monitoring` | T6 | 底座(渐进) | 待开始 |
| T12 | MCP Server | `mcp`(新) | T4 T5 T10 | 方案2 | 待开始 |
| T13 | Skill Pack + Claude Code 验收 | 交付物 | T12 | 方案2 | 待开始 |
| T2 | 细粒度授权（项目/角色/工具级策略） | `project` `identity` `facade` | T1 T3 | 按需·后移 | 后移 |

> 排除项（不列为任务）：`llm` 层扩建 Agent Runtime / Tool Calling / 多轮会话。现有 `LlmGateway` 单次调用保留作平台自身轻量用途（如报告摘要）。

---

## M1 认证底座

### T1 认证底座：平台 token + agent API Key + 统一 Principal —— [✅ 完成]
- 目标：平台从当前「无请求级身份」变为所有请求携带并校验身份。平台用户用简单 token 全站认证；agent 面用独立 API Key；两者共用同一条 Spring Security 过滤链，解析为统一 `Principal(human|machine)` 注入下游。
- 涉及：`identity` `config` `api/AuthController` + `frontend`(登录态适配)。依赖：无。
- 交付：OpenSpec change `add-auth-foundation`（已归档至 `openspec/changes/archive/2026-07-24-add-auth-foundation/`）；主 spec `openspec/specs/platform-authentication/spec.md`。
- 实现要点：服务端 opaque token（存哈希，TTL 24h）；`Authorization: Bearer` / `X-API-Key` 双头；ADMIN 可管理 Agent API Key（前端系统配置 tab）。
- 本任务不含授权：只判断「身份是否有效」，不判断「能否访问某资源」（授权见 T2，后移）。
- 验收：登录拿 token→带 token 访问通过；无/失效 token→401；签发 API Key→带 Key 访问通过；吊销后→401。

- [x] 定义统一 `Principal` 抽象：human（username/roles）与 machine（apiKeyId/预留 scope）两种主体，注入请求上下文
- [x] 平台用户：`login` 成功后签发服务端 opaque token，随响应返回
- [x] 平台用户：鉴权过滤器校验 token，解析为 human Principal；缺失/失效→401
- [x] agent：新增 API Key 实体 + 仓库，支持签发 / 吊销 / 过期（scope 字段预留，暂不做策略判定）
- [x] agent：同一 `SecurityFilterChain` 内识别 API Key，解析为 machine Principal
- [x] `SecurityConfiguration` 改为默认 `authenticated`，白名单 `/api/auth/login`、`/actuator/health`
- [x] 对外统一 401/未授权稳定语义；真实原因最小化记录（完整审计复用 T10）
- [x] 前端：登录态适配——存储 token 并在请求头统一注入；401 清会话并跳登录
- [x] Agent API Key 管理页（系统配置 tab，仅 ADMIN）：列表 / 签发一次性明文 / 吊销
- [x] 先失败测试：无 token→401、失效 token→401、有效 token→200；无 Key→401、有效/已吊销 Key→200/401
- [x] 验证：后端认证相关测试通过；前端联调通过

---

## M2 业务入口与契约

### T3 Agent-ready Facade —— [✅ 完成]
- 目标：新建 `facade` 层，成为 agent 面唯一强制业务入口；接第一个 agent 只读能力时立起骨架，不做 18 个 controller 全量迁移。
- 涉及：`facade`(新) `agent`(新)。依赖：T1。
- 交付：OpenSpec change `add-agent-facade-contract`（已归档至 `openspec/changes/archive/2026-07-24-add-agent-facade-contract/`）；主 spec `openspec/specs/agent-facade/spec.md`。
- 实现要点：并行 agent 面 `/api/agent/**`；`FacadeGuard` 主体校验 + 审计/授权钩子占位；`DataFacade`（数据组，其余组用到再加）；只读切片 `GET /api/agent/executions/{id}/summary`。
- 验收：调用方无法绕过 Facade 直连 Repository/文件/Prometheus；执行摘要经 Facade 端到端打通。

- [x] 新建 `facade` 包，立起数据组入口（资产/执行/分析/取证/验证用到再加）
- [x] Facade 内统一装配：主体校验(T1)、审计钩子占位、授权钩子预留给 T2
- [x] 先打通一条只读能力垂直切片（执行摘要查询），验证边界
- [x] 约束校验：agent 包不直连 Repository/文件/监控（分层守护测试）
- [x] 先失败测试：Facade 主体校验失败路径（无效身份）
- [x] 验证：`gradle :backend:test` + 分层约束测试通过

### T4 统一响应契约 + 错误码 + OpenAPI —— [✅ 完成]
- 目标：面向 Agent 的统一响应封套与稳定错误语义 + 自动 OpenAPI。
- 涉及：`agent` `config` `build.gradle`。依赖：T3。
- 交付：同 change `add-agent-facade-contract`；主 spec `openspec/specs/agent-response-contract/spec.md`。
- 实现要点：`ApiResponse` 封套（分页字段先定义后休眠）；`AgentErrorCode` 全枚举；包作用域 `AgentExceptionHandler`（UI 面 `PlatformExceptionHandler` 不动）；springdoc 分组 `/v3/api-docs/agent`。
- 验收：Agent 接口响应含契约字段；OpenAPI 可生成。

- [x] 定义响应封套：`requestId` `schemaVersion` `data|error` `warnings` `truncated` `nextCursor`（分页字段本阶段恒 null）
- [x] 定义稳定错误码枚举：认证/权限/不存在/数据源不可用/查询过大/超时/限流/幂等冲突/执行冲突/参数校验/内部错
- [x] 包作用域 `@RestControllerAdvice` 映射到统一封套与错误码（不改 UI 面 handler）
- [x] 引入 springdoc，生成 OpenAPI；领域 DTO 标注 `schemaVersion`
- [x] 先失败测试：各错误场景返回对应稳定码 + 封套结构；UI 面裸 `ApiError` 不变
- [x] 验证：访问 `/v3/api-docs/agent` 可得完整 spec；`gradle :backend:test` 通过

---

## M3 数据链与分析

### T5 有界查询 / 分页 / 数据可用性 —— [✅ 完成]
- 目标：跨数据源统一游标分页 + 响应预算 + 可用性语义。
- 涉及：`execution` `monitoring` `report` `execution/failure`。依赖：T4。
- 验收：大结果集返回 `truncated`+`nextCursor`，每类数据显式声明可用性。

- [x] 定义统一游标分页协议（条数/字节/耗时预算），复用 T4 封套字段
- [x] 聚合/秒级指标查询接入游标 + 预算
- [x] 失败样本（SQLite）查询接入游标 + 预算（复用现有 cursor 雏形）
- [x] Prometheus 指标查询接入时间窗 + 粒度 + 预算
- [x] 定义数据可用性返回：存在性/时间覆盖/采样/截断/可回溯/缺失原因
- [x] 约束：禁止用其他时段/实例/数据源静默替代缺失数据
- [x] 先失败测试：超预算→截断+游标；缺失源→显式缺失而非空成功
- [x] 验证：`./gradlew :backend:test` 有界查询与可用性用例通过

### T6 数据链关联骨架 —— [✅ 完成]
- 目标：先建关联键与时钟对齐，再谈接新源；分析依赖此层。
- 涉及：`evidence`(新)。依赖：T5。
- 验收：现有指标/样本/Prometheus 能按统一关联键对齐到同一 execution+时间窗。

- [x] 定义统一关联键：`executionId` + 绝对时间窗 + 目标实例(host/instance/service) + 请求标签 + 可选 `traceId`
- [x] 时钟对齐：压测端 / 被测端 / Prometheus 三方时间基准校正策略
- [x] 新建 `evidence` 适配层：将现有执行/指标/样本/Prometheus 统一为带关联键的证据摘要
- [x] 接入约束：新数据源必须声明其绑定的关联键，否则不接入
- [x] 数据保留/失效：执行删除→证据失效提示；只存摘要+来源定位
- [x] 先失败测试：跨源按 executionId+时间窗对齐正确；时钟偏移被校正
- [x] 验证：`./gradlew :backend:test` 关联用例通过

### T7 确定性分析 —— [底座]
- 目标：可重复算法产出事实，不出 LLM 根因。
- 涉及：`analysis`(新)。依赖：T6。
- 验收：黄金数据集上结果可重复；输出带算法版本与证据定位。

- [x] 新建 `analysis` 模块，算法带版本号
- [x] 趋势分析：响应时间/吞吐/错误率变化
- [x] 异常区间 / 性能拐点检测
- [x] 错误聚类 + 请求/接口贡献度
- [x] 资源饱和（CPU/内存/GC/线程/连接池）+ 压测与资源指标时间相关性
- [x] 执行间可比性与差异（基线 vs 候选）
- [x] 输出结构：事实 + 算法版本 + 输入范围 + 完整度 + 证据定位（不含根因）
- [x] 建固定「黄金数据集」+ 可重复性测试
- [x] 验证：`./gradlew :backend:test` 黄金样本重复运行结果一致

### T8 压测执行工具化 —— [底座]
- 目标：给现有执行能力补 Agent 驱动语义。
- 涉及：`execution` `execution/distributed` `task`。依赖：T4。
- 验收：写操作幂等；异步任务返回稳定 ID；可预检/取消。

- [x] 写操作（创建/启动）支持客户端幂等键，重试返回原结果不重复启动
- [x] 执行预检 + 影响评估接口
- [x] 启动/停止/取消统一语义
- [x] 异步任务返回稳定任务 ID + 状态查询工具（不依赖长同步连接）
- [x] 先失败测试：同一幂等键重复请求只启动一次；取消后状态一致
- [x] 验证：`./gradlew :backend:test` 幂等与异步状态用例通过

---

## M4 闭环与治理

### T9 补充取证 + 优化验证 —— [底座]
- 目标：诊断期取证 与 修改后验证 两条流程，复用执行编排但语义分离。
- 涉及：`execution` `facade`。依赖：T7 T8。
- 验收：取证需审批；验证能输出「改善/退化/无法判定」。

- [ ] 取证动作预检（目的/影响/成本）+ 审批 + 执行 + 新数据回流
- [ ] 变更登记：代码/配置引用登记入口
- [ ] 验证：基线 vs 候选可比性检查 + 护栏指标
- [ ] 验证结论三态输出：改善 / 退化 / 无法判定（波动过大→无法判定）
- [ ] 语义分离校验：取证与验证不混为同一流程
- [ ] 先失败测试：未审批取证被拒；不可比执行→无法判定
- [ ] 验证：`./gradlew :backend:test` 取证/验证用例通过

### T10 治理（脱敏 / 审计 / 限流） —— [底座]
- 目标：横切治理，进模型/出平台前强制生效。
- 涉及：跨模块（挂在 Facade / 鉴权层）。依赖：T3。
- 验收：敏感字段在输出前脱敏；请求/执行可审计重建；超配额被限流。

- [ ] 脱敏：请求体/响应体/SQL 参数/日志/环境，在 Facade 输出前强制（不依赖 SKILL）
- [ ] 审计：请求审计 + 执行审计，可重建平台侧操作轨迹
- [ ] 限流：并发/超时/配额/速率限制
- [ ] 先失败测试：敏感字段未脱敏则测试失败；超速率→429
- [ ] 验证：`./gradlew :backend:test` 治理用例通过

### T11 深度证据逐源接入 —— [底座 · 渐进]
- 目标：按数据域逐个接入深度证据，每源带可用性 + 关联键。可跨 M4/M5 持续。
- 涉及：`evidence` `monitoring`。依赖：T6。
- 验收：每接入一源即可按 executionId/时间窗/traceId 下钻，并声明可用性。

- [ ] 中间件/DB 业务指标增强（连接池/慢查询计数/锁等待，基于现有 exporter）
- [ ] Trace/APM 接入（OTel 或 SkyWalking 择一），按 `traceId` ↔ 请求关联 —— 高影响，需审批
- [ ] 应用日志接入（时间窗+服务+traceId 过滤）
- [ ] 慢 SQL + 执行计划(explain)
- [ ] Profiling/JFR/火焰图（摘要→热点→调用路径下钻）
- [ ] 每源单独：可用性语义 + 保留/失效策略
- [ ] 验证：每源接入后端到端下钻用例通过

---

## M5 外部 Agent 接入

### T12 MCP Server —— [方案2]
- 目标：Streamable HTTP MCP Server，机器身份接入，调用 Facade。
- 涉及：`mcp`(新)。依赖：T4 T5 T10。
- 验收：Claude Code 能完成 MCP 初始化与工具发现；工具复用 Facade 不复制业务规则。

- [ ] 新建 `mcp` 模块：Streamable HTTP MCP Server
- [ ] 机器身份接入（复用 T1 的 API Key）
- [ ] 任务型工具设计（非 REST 机械 1:1；写操作单一明确语义）
- [ ] 动态工具发现：按项目能力/角色/阶段（导航→设计→诊断→验证）分批暴露
- [ ] 工具响应复用 T4 封套 + T5 预算/游标 + 稳定错误码
- [ ] 先失败测试：越权工具不可见/不可调；写操作幂等；截断返回游标
- [ ] 验证：Claude Code 实际连接完成 initialize + tools/list + 一次只读调用

### T13 Skill Pack + Claude Code 验收 —— [方案2]
- 目标：发布 Skill Pack，真实项目端到端验收。
- 涉及：交付物（Skill 文档 + 验收脚本）。依赖：T12。
- 验收：Claude Code 按 Skill 完成 压测→诊断→取证→验证 全流程。

- [ ] Skill：平台导航 / 压测设计 / 执行观察 / 性能诊断 / 补充取证 / 优化验证
- [ ] Skill 只规定操作顺序/证据规范/停止条件；权限与风险由平台强制
- [ ] 真实项目端到端走查：从已结束执行发起，产出可追溯诊断
- [ ] Claude Code 版本升级兼容性测试
- [ ] 审计校验：工具调用能重建平台侧操作轨迹
- [ ] 验证：端到端演练记录 + 审计轨迹留档

---

## 后移 / 按需任务

### T2 细粒度授权（项目/角色/工具级策略） —— [按需·后移]
- 目标：在认证（T1）之上做细粒度授权。**近期不做**，待需要按项目/角色/工具控制访问时再启动。
- 涉及：`project` `identity` `facade`。依赖：T1 T3（授权钩子挂在 Facade）。
- 验收：跨项目越权访问被拒；工具/能力级策略可按 用户×角色×项目×环境 配置。

- [ ] 定义授权模型：主体 × 项目 × 环境 × 能力(工具) → 允许/拒绝/需确认
- [ ] 实现授权检查组件（供 Facade 调用），非硬编码禁止清单
- [ ] 定义高风险操作策略开关（是否需确认令牌，可按角色/环境豁免）
- [ ] 先失败测试：非成员访问项目资源→403；PROJECT_MEMBER 无高级能力→403
- [ ] 验证：`./gradlew :backend:test` 授权用例通过
