# Claude Code 端到端验收走查（T13）

> 目标：用真实 Claude Code 按 Skill Pack 完成「压测 → 诊断 → 取证 → 验证」全流程，
> 产出可追溯诊断，并用平台审计库重建操作轨迹。

## 前置条件

1. 平台运行中（`gradle :backend:bootRun`，默认 `http://127.0.0.1:8080`）。
2. 管理员签发 Agent API Key（`ops` scope）。
3. Claude Code 已安装，MCP 配置指向平台 `/mcp`（见 `skill-pack/README.md`）。
4. 至少有一条已结束的执行（`executionId`）可用于诊断。

## 走查步骤

1. **导航**：让 Claude Code 执行 `perf-platform-navigate` 技能，确认目标项目可见。
2. **设计/执行**：按 `perf-platform-design` 启动一次执行，记录 `executionId`；
   再次以相同幂等键调用，确认返回 `replayed=true` 且未重复启动。
3. **观察**：按 `perf-platform-observe` 等到执行结束，引用摘要指标。
4. **诊断**：按 `perf-platform-diagnose` 对已结束执行运行 `analyze_execution` 与
   `collect_evidence`，确认输出带算法版本与证据定位、缺失源显式声明。
5. **补充取证**：按 `perf-platform-capture` 发起取证（`PENDING_APPROVAL`），
   平台侧用管理员账号审批，再执行并确认 `bundleRef`。
6. **优化验证**：按 `perf-platform-verify` 登记变更并 `verify_change`，
   确认结论为三态之一且 `reasons` 可读。

## 审计校验

- `GET /api/agent/audit/requests?limit=200`：应能按时间序重建全部 MCP 调用
  （方法/路径/主体/状态码/耗时）。
- `GET /api/agent/audit/executions?executionId={id}`：应能看到该执行的
  `START`/`STOP`/`CANCEL` 操作与操作主体。
- 协议级冒烟可先跑 `skill-pack/verify/acceptance-smoke.sh`。

## 兼容性记录

- 平台 MCP 协议版本：`2025-06-18`（Streamable HTTP）。
- 验证环境：Claude Code 需支持 `type: http` MCP 配置（较新版本支持；老版本
  仅支持 `stdio` 时，可借助 `mcp-remote` 或等价代理转发到本平台 `/mcp`）。

## 停止条件（走查判定）

- 任一技能步骤命中其 SKILL.md 中的停止条件，走查即视为发现缺陷，记录并修复后重跑。
- 全部步骤证据留档（对话记录 + 审计轨迹截图/导出）后视为通过。
