## 1. 数据模型与持久化

- [x] 1.1 新增 `model_provider` / `model_definition` / `model_call_record` 表（或等价 JPA schema）及实体、Repository
- [x] 1.2 实现提供商领域服务：CRUD、apiKey 脱敏、更新时 apiKey 空=不改、默认 BaseUrl 必填、Anthropic BaseUrl 选填
- [x] 1.3 实现模型领域服务：CRUD、apiType 默认 OPENAI、全平台默认模型唯一、同提供商 modelName 唯一、跨提供商同名允许
- [x] 1.4 实现提供商级联删除：无模型直接删；有模型且未 cascade → 冲突；cascade → 删提供商及下属模型，不删调用记录

## 2. Adapter 与 Gateway

- [x] 2.1 实现 OpenAI-compatible Adapter（chat + listModels），HttpClient + MockWebServer 测试
- [x] 2.2 实现 Anthropic Adapter（chat + listModels），含 BaseUrl 回退规则的单元测试
- [x] 2.3 实现 `LlmGateway.invoke`：校验 enabled、按 apiType 选 Adapter、成功/失败均写调用记录、storeBody 开关与截断、apiKey 不入记录
- [x] 2.4 实现调用记录查询服务（分页 + providerId/modelId/scene/status 过滤）

## 3. REST API

- [x] 3.1 提供商 API：`/api/llm/providers` CRUD、`POST .../test`、`POST .../fetch-models`、`POST .../import-models`、DELETE cascade
- [x] 3.2 模型 API：`/api/llm/models` CRUD、设默认；`GET /api/llm/available-models` 按提供商分组且仅 enabled
- [x] 3.3 调用记录 API：`GET /api/llm/call-records` 分页过滤
- [x] 3.4 API 行为测试覆盖脱敏、默认模型、级联删除、available-models 跨提供商同名、Gateway 落库

## 4. 前端：模型配置管理

- [x] 4.1 系统配置侧栏增加「模型配置管理」分组及子路由：提供商 / 模型 / 调用记录
- [x] 4.2 提供商子页：列表、新建/编辑（BaseUrl 必填、Anthropic BaseUrl 选填、apiKey）、获取模型列表、导入、连通性测试、级联删除二次确认
- [x] 4.3 模型子页：按提供商筛选、新建/编辑（apiType 默认 OPENAI）、设默认、启用/禁用、删除
- [x] 4.4 调用记录子页：分页列表与过滤；可选展开查看正文（若有）

## 5. 验收

- [x] 5.1 本地用 DeepSeek（baseUrl `https://api.deepseek.com/v1`，模型 `deepseek-v4-flash`，apiType OPENAI）手工跑通：建提供商 → 拉/加模型 → 连通性测试 SUCCESS → 调用记录可见 → available-models 分组可见（密钥仅 UI/环境，不入库代码）
- [x] 5.2 确认 CI/单测仅依赖 Mock，仓库中无真实 apiKey
