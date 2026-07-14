## Context

平台文档已规划 AI 生成脚本与报告/日志分析，但代码中尚无 LLM 接入层。系统配置目前仅有用户/角色/权限 Mock，没有模型凭证与调用审计。

本期只建设平台级底座：提供商与模型分治、统一调用网关、调用记录。具体 AI 业务后续通过 `modelId` 与 `LlmGateway` 接入。

约束：
- 不新增第三方 LLM SDK，HttpClient 直调
- apiKey 存储级别对齐现有 sshPassword（本期不上 KMS）
- 权限暂不细化，入口挂系统配置
- 真实 DeepSeek 连通验收仅本地/手工，密钥不进仓库与 CI

## Goals / Non-Goals

**Goals:**
- 平台级提供商 / 模型分表管理，系统配置下「模型配置管理」三子页
- 模型携带 `api_type`（默认 OPENAI）；提供商提供默认 BaseUrl + 可选 Anthropic BaseUrl（未填回退）
- 自动拉取并导入模型列表；`available-models` 按提供商分组供业务选用
- `LlmGateway` 统一调用与落库；连通性测试产生调用记录
- 调用记录元数据必落，正文按开关可选落库

**Non-Goals:**
- Prompt 模板、AI 脚本生成、报告/日志 AI 分析
- 项目级模型配置、细粒度 RBAC、配额计费
- KMS/密钥轮换工作流、调用记录 TTL 清理
- 第三方 LLM SDK

## Decisions

### 1. 架构：网关 + Provider Adapter

**决策**: 配置 CRUD / 调用记录 / `LlmGateway` 分离；业务与连通性测试均只经 Gateway。

```
Settings UI → Provider/Model/CallRecord API
Test / 未来业务 → LlmGateway → OpenAiAdapter | AnthropicAdapter
                              ↘ ModelCallRecord
```

**备选**: 单服务内 if/else 直连 — 否决，后续多消费方易膨胀。完整 AI 平台（Prompt/配额/异步任务）— 否决，超出底座范围。

### 2. 提供商与模型分表；类型挂在模型上

**决策**:
- `model_provider`: `base_url`（必填）、`base_url_anthropic`（选填）、`api_key`、`store_body_default`、`enabled`
- `model_definition`: `provider_id`、`model_name`、`display_name?`、`api_type`（OPENAI|ANTHROPIC，默认 OPENAI）、`enabled`、`is_default`（全平台至多一条）

提供商不存单一 type。跨提供商允许相同 `model_name`；业务选用必须使用全局唯一的 `modelId`。

### 3. 双 BaseUrl 选址

**决策**:
- `OPENAI` → 始终用 `base_url`
- `ANTHROPIC` → 有 `base_url_anthropic` 用它，否则回退 `base_url`
- `fetch-models` / 测试随本次 `apiType` 或模型 `api_type` 选址，未填 Anthropic Url 不报错

**备选**: 未填 Anthropic Url 时 400 — 否决，与「选填 + 回退」产品决策不符。

### 4. 拉列表与导入两步

**决策**: `POST .../fetch-models` 返回候选；`POST .../import-models` 落库。导入模型的 `api_type` = 本次拉取协议。已存在同提供商下相同 `model_name` 则跳过或更新展示名（实现取跳过已存在）。

### 5. 删除提供商级联模型

**决策**: 有下属模型时未带确认参数返回可识别错误（如 409）；前端二次确认后带 cascade 删除提供商及其模型。调用记录不级联删除，保留名称快照。

### 6. 调用记录与正文

**决策**: 成功/失败均写记录；`storeBody` 可覆盖，null 时用提供商 `store_body_default`；字段建议 256KB 截断；apiKey 永不入记录。`scene` 本期含 `TEST_CONNECTION`，预留扩展。

### 7. 连通性测试选模

**决策**: `POST /api/llm/providers/{id}/test` 可选 `modelId`；未传则优先该提供商下默认且 enabled 的模型，否则任一 enabled；都没有 → 400。

### 8. UI 导航

**决策**: 系统配置侧栏增加「模型配置管理」分组，子页：提供商 / 模型 / 调用记录（如 `/settings/llm/providers|models|call-records`），不拆成与用户/角色同级的三个顶层 Tab。

### 9. 真实验收

**决策**: CI/单测用 MockWebServer 覆盖两种协议；本地手工用 DeepSeek（`https://api.deepseek.com/v1` + `deepseek-v4-flash`，OPENAI）跑通创建→导入/添加→测试→记录→available-models。密钥仅环境/UI，不进仓库。

## Risks / Trade-offs

- [apiKey 明文存库] → 与现有 sshPassword 同级；列表脱敏；后续可加加密而不改 API 形态
- [Anthropic 回退到 OpenAI BaseUrl 时对方不兼容] → 错误写入调用记录；UI 提示可选填独立 Url
- [调用正文体积膨胀] → 默认不落正文 + 截断上限
- [对话中曾暴露测试 Key] → 验收前要求轮换；文档与代码禁止固化密钥

## Migration Plan

1. 新增表与后端 API，前端挂系统配置子菜单
2. 无历史数据迁移
3. 回滚：下线菜单与 Controller；表可保留空数据

## Open Questions

（无；探索阶段已关闭）
