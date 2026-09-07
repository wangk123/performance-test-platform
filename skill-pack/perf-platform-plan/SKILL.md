---
name: perf-platform-plan
description: 在性能测试平台生成压测计划：对话梳理测试目的/范围/资源/约束，拉取模板本地渲染全文，经 MCP 同步为平台草稿计划，评审反馈后再修改。用于"帮我写压测计划/测试方案"类请求。
---

# 压测计划生成

## 目的

通过与用户对话梳理压测计划要素，基于平台模板本地生成完整 Markdown 计划，同步为平台草稿计划；评审反馈后拉回本地修改再同步，直到计划进入评审阶段交回平台。

## 操作顺序

1. **对话梳理**（逐项向用户确认，不自行编造）：
   - 测试目的与核心指标（每项指标挂到具体交易）
   - 测试范围与交易配比（范围内 / 范围外）
   - 测试资源（人员分工、环境部署信息表、执行节点、监控目标、时间窗口）
   - 测试约束（入口准则、出口准则）
   - 排期与协作（环节 / 时间 / 负责人）
2. `list_projects` 确认目标项目 ID。
3. `plan_templates({ "projectId": <id> })` 拉取模板清单，按 `sections` 与 `placeholders` 逐项填充。
4. 本地按模板章节结构（一、背景 … 十一、结论，11 章节规范序号）渲染完整 Markdown，向用户展示确认。
5. `plan_create({ "projectId", "title", "markdown" })` 同步平台 → 记录返回的 `planId` 与 `revision`，
   把平台链接给用户（`/projects/<projectId>/task-plans/<planId>`），引导用户进平台提交评审。
6. 评审反馈后修改：`plan_get({ "planId" })` 取全文与 `revision` → 本地修改 →
   `plan_update({ "planId", "markdown", "baseRevision": <revision> })`。

## 证据规范

- 引用计划时给出 `planId`、`revision`、`phase` / `status`。
- `plan_update` 冲突（`PLAN_REVISION_CONFLICT`）：用 `error.details.serverMarkdown` 与本地版逐节 diff，
  向用户呈现差异后三选一——保留平台版 / 采纳本地版（以 `details.currentRevision` 为新 base 重放全文）/
  手改合并后重提。
- `PLAN_STATE` 错误：原样呈现 `details.phase` / `status` / `allowedActions`，说明对应动作需用户回平台操作。

## 停止条件

- 计划 `phase` 不是 `DRAFT`（已提交评审及之后）→ 停止改稿；评审、批注、流转、发布均由平台承载。
- 工具返回 `isError=true` → 停止，原样呈现错误码与消息；写操作失败不要自动重试。
- 本 skill 不做：发布、删除、分享、评审流转、批注、场景与脚本管理。
