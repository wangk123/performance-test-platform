---
name: perf-platform-design
description: 在性能测试平台上设计并启动压测执行：预检、幂等启动、跟踪执行状态。用于发起一次压测。
---

# 压测设计（启动执行）

## 目的

对指定场景发起压测执行：先预检后启动，保证不重复启动，拿到稳定执行 ID 后转入观察。

## 操作顺序

1. 确认场景 ID 与执行命名（`executionName`），生成稳定幂等键（`idempotencyKey`，
   同一逻辑执行复用同一键；不提供时平台按参数哈希自动幂等）。
2. 调用 `start_execution`（`scenarioId` 必填，其余可选）。
3. 若 `precheck` 不通过（`valid=false`），停止并展示 `errors`/`warnings`，不要启动。
4. 记录返回的 `execution.executionId` 与 `execution.replayed`：
   - `replayed=true` 表示该幂等键已有执行，不得重复发起。
5. 转入 `perf-platform-observe` 技能跟踪执行。

## 证据规范

- 引用 `precheck` 的线程数/时长/节点/排队长度（`queueAhead`）。
- 引用 `executionId`、`status`、`replayed`，不凭记忆复述。

## 停止条件

- 预检失败或工具 `isError=true` → 停止，不启动。
- 同一幂等键重复调用返回 `replayed=true` → 不得再次启动。
- 平台排队中（`queueAhead>0`）→ 提示用户，是否继续由用户决定。
