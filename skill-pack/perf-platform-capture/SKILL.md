---
name: perf-platform-capture
description: 对已结束执行发起补充取证：声明目的、影响与成本，进入人工审批流。用于常规证据不足以定位问题时。
---

# 补充取证

## 目的

为已结束的执行申请一次有界证据快照（聚合/秒级/失败样本/监控），
按平台规则声明目的（purpose）、影响（impactLevel）与成本（costNote）。

## 操作顺序

1. 仅在常规证据（`collect_evidence`）不足时使用本技能。
2. 调用 `request_evidence_capture`：
   - `purpose`：为什么需要这次取证（必填）；
   - `impactLevel`：`NONE`/`LOW`/`MEDIUM`/`HIGH`，非 `NONE` 必须填 `costNote`。
3. 返回状态为 `PENDING_APPROVAL`：告知用户需人工在平台审批；**Agent 无权自行审批**。
4. 审批后（平台侧 `APPROVED`）由操作方调用 `POST /api/agent/evidence-captures/{id}/execute`，
   完成后引用 `bundleRef` 与各源摘要。

## 证据规范

- 引用 `captureId`、`status`、`bundleRef` 与 `sources` 的可用性字段。
- 未审批（`PENDING_APPROVAL`）不视为已取证。

## 停止条件

- 取证请求被拒（`REJECTED`）或未审批 → 停止，不得绕过审批重试。
- 工具返回 `isError=true` → 停止并展示错误码。
