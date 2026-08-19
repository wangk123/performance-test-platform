---
name: perf-platform-diagnose
description: 对已结束的压测执行做确定性诊断：趋势、异常区间、错误聚类、资源饱和与证据链下钻。只产出事实，不下根因结论。
---

# 性能诊断

## 目的

从平台确定性分析算法与证据链中收集**事实**，供用户（或后续取证/验证）定位问题。
平台只给事实与证据定位，不做根因推断——根因判断留给有依据的分析。

## 操作顺序

1. 先 `inspect_execution` 确认执行已结束并拿到时间窗。
2. 调用 `analyze_execution`（可选 `kinds`：`trend`/`anomaly`/`error-cluster`/`resource-saturation`）：
   - `trend`：前后半段响应时间/吞吐/错误率变化；
   - `anomaly`：异常区间与拐点（`kneePointMs`）；
   - `error-cluster`：失败按 标签×状态码×归一化消息 聚类 + 标签贡献度；
   - `resource-saturation`：资源饱和窗口与压测吞吐相关性（需要监控绑定）。
3. 需要更多上下文时调用 `collect_evidence`（`traceId`/时间窗），逐源读取 `availability`：
   - `present=false` 的源必须如实声明缺失，不得用其他数据替代。
4. 汇总时：每条事实附 `algorithmId/algorithmVersion` 与 `evidenceRefs`/`sourceRef`。

## 证据规范

- 引用事实时保留算法版本与证据定位；`truncated=true` 时注明数据被预算截断。
- 缺失数据显式说明（`SOURCE_UNAVAILABLE`/`NO_DATA`/`DELETED`），禁止静默填补。

## 停止条件

- 执行不存在或源不可达 → 停止，如实报告缺失原因。
- 证据不足以支撑任何判断 → 停止，建议 `perf-platform-capture` 补充取证。
- 不得输出「根因是 X」这类无证据结论。
