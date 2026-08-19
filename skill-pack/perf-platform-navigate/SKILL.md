---
name: perf-platform-navigate
description: 在性能测试平台中导航：列出项目、理解测试资产归属。用于开始任何平台相关任务时的第一步。
---

# 性能平台导航

## 目的

找到目标被测系统的项目，确认其负责人与状态，为后续设计/诊断/验证确定资产归属。

## 操作顺序

1. 调用 `list_projects`（可选 `includeArchived=false`），读取项目列表。
2. 若项目不存在或已归档，停止并告知用户，不要猜测或创建项目。
3. 记录项目 ID 与名称，作为后续对话的上下文。

## 证据规范

- 引用结果中的 `code`、`name`、`ownerUsername`、`status` 字段。
- 归档项目（`ARCHIVED`）不用于新任务。

## 停止条件

- 找不到目标项目 → 停止，向用户确认项目编码。
- 工具返回 `isError=true` → 停止，原样呈现错误码与消息（如 `NOT_FOUND` / `AUTHENTICATION_FAILED`）。
