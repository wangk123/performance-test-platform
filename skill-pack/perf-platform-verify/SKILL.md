---
name: perf-platform-verify
description: 登记代码/配置变更并验证优化效果：基线 vs 候选三态结论（改善/退化/无法判定）。用于修改后的前后对比。
---

# 优化验证

## 目的

把一次修改（代码/配置）与前后两次执行关联，产出可追溯的三态验证结论。

## 操作顺序

1. 调用 `register_change`（`changeType`：`CODE`/`CONFIG`；`changeRef`：提交号或配置键；可选 `description`）。
2. 确认基线执行与候选执行均已完成（`inspect_execution`）。
3. 调用 `verify_change`（`baselineExecutionId`、`candidateExecutionId`、`changeRecordId`）。
4. 读取并解释 `verdict`：
   - `IMPROVED`：标签级一致改善且未触发护栏；
   - `REGRESSED`：存在 p95 退化或错误率护栏越限（`errorRateDelta`）；
   - `INCONCLUSIVE`：不可比、无显著变化或波动过大（`reasons` 说明原因）。
5. 引用 `labels` 中各标签的 `p95DeltaPct`/`throughputDeltaPct`/`errorRateDelta` 与 `verdict`。

## 证据规范

- 引用 `verificationId`、`algorithmId/algorithmVersion` 与 `reasons`。
- `INCONCLUSIVE` 时必须原样给出平台返回的 `reasons`，不得自行解释为改善或退化。

## 停止条件

- 基线/候选不可比（`reasons` 含 `different scenario`/时长差异等）→ 停止，不强行下结论。
- 结论只输出 IMPROVED / REGRESSED / INCONCLUSIVE 三态之一，不得扩展为「根因」。
