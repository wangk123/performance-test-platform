---
name: perf-platform-observe
description: 观察压测执行状态与结果摘要：轮询状态、判断是否结束、读取吞吐/响应时间/错误率。用于执行期间与结束后的状态确认。
---

# 执行观察

## 目的

确认执行是否结束（成功/失败/取消/中断），获取可引用的结果摘要。

## 操作顺序

1. 调用 `inspect_execution`（`executionId` 必填）。
2. 读 `status.status`：`RUNNING`/`QUEUED`/`STOPPING` 时说明仍在进行，按用户要求间隔复查。
3. 终态（`SUCCESS`/`FAILED`/`CANCELLED`/`INTERRUPTED`）后，引用 `summary` 的
   `samples`、`throughput`、`avgRtMs`、`p95RtMs`、`errorRate` 与 `durationMs`。
4. `FAILED`/`INTERRUPTED` 时读取 `status.errorMessage` 并原样呈现。

## 证据规范

- 只引用本次调用返回的字段；禁止臆造指标值。
- 引用时注明数据来源（`inspect_execution` 返回值）。

## 停止条件

- 执行不存在（`NOT_FOUND`）→ 停止，请用户核对 executionId。
- 连续多次状态不变且用户未要求继续 → 停止轮询。
