## Why

后续 AI 生成脚本、报告分析等能力都需要统一的模型接入与可追溯调用。当前平台没有模型配置、协议适配和调用审计，业务侧无法复用同一套凭证与模型清单。先建设平台级底座，再接入具体 AI 场景。

## What Changes

- 新增平台级「模型提供商」管理：BaseUrl（必填）与 Anthropic BaseUrl（选填，未填回退默认）、apiKey 脱敏、启用状态、正文落库默认开关
- 新增「模型」管理：隶属于提供商；`api_type` 为 `OPENAI`（默认）或 `ANTHROPIC`；支持设默认；允许不同提供商下同名模型
- 支持按协议自动获取模型列表并导入；提供按提供商分组的可用模型列表 API（业务用 `modelId` 选用）
- 新增 `LlmGateway` + OpenAI / Anthropic Adapter；连通性测试走 Gateway 并写入调用记录
- 新增调用记录查询：元数据必落，请求/响应正文按开关可选落库
- 系统配置下新增「模型配置管理」菜单，含提供商 / 模型 / 调用记录三个子页
- 删除提供商时若存在下属模型需二次确认，确认后级联删除模型（调用记录保留快照）

## Capabilities

### New Capabilities
- `llm-provider-config`: 平台级提供商 CRUD、双 BaseUrl、密钥脱敏、级联删除、拉取/导入模型列表、连通性测试入口
- `llm-model-config`: 模型 CRUD、默认模型、`api_type`、按提供商分组的可用模型列表
- `llm-call-gateway`: 统一调用网关、协议适配、调用记录落库与查询

### Modified Capabilities

（无）

## Impact

- **后端新增**: `llm` 包（Provider/Model/CallRecord 实体与 API、`LlmGateway`、Adapter）；Flyway/建表脚本
- **前端新增**: 系统配置下「模型配置管理」及三个子页、相关 API client
- **依赖**: 本期不引入第三方 LLM SDK，使用 HttpClient 直调
- **安全**: apiKey 不入调用记录、不回显明文；真实 Key 仅本地/UI 录入，不进仓库与 CI
- **权限**: 本期不细化 RBAC，入口挂系统配置区
- **不影响**: 现有项目/脚本/执行/报告 API 行为
