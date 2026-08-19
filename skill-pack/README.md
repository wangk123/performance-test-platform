# Performance Test Platform — Claude Code Skill Pack（T13）

面向外部 Agent（Claude Code）的性能平台技能包。技能只规定**操作顺序、证据规范与停止条件**；
权限与风险全部由平台强制（认证/脱敏/审计/限流/取证审批在服务端生效）。

## 组件

| 技能 | 阶段 | 主要 MCP 工具 |
|------|------|--------------|
| `perf-platform-navigate` | 导航 | `list_projects` |
| `perf-platform-design` | 设计/执行 | `start_execution`、`inspect_execution` |
| `perf-platform-observe` | 观察 | `inspect_execution` |
| `perf-platform-diagnose` | 诊断 | `analyze_execution`、`collect_evidence` |
| `perf-platform-capture` | 补充取证 | `request_evidence_capture` |
| `perf-platform-verify` | 优化验证 | `register_change`、`verify_change` |

## 安装

1. 平台启动后，管理员签发 Agent API Key（平台「系统配置 → Agent API Key」），
   全流程写操作用普通 scope（如 `ops`）；只读演练可用 `readonly` scope。
2. 将技能目录复制到 `~/.claude/skills/`（或 `$CLAUDE_CODE_SKILLS` 指向本目录）：

   ```bash
   mkdir -p ~/.claude/skills
   cp -R skill-pack/perf-platform-* ~/.claude/skills/
   ```

3. 配置 Claude Code 连接平台 MCP（Streamable HTTP），例如 `~/.claude.json`：

   ```json
   {
     "mcpServers": {
       "perf-platform": {
         "type": "http",
         "url": "http://127.0.0.1:8080/mcp",
         "headers": { "X-API-Key": "<agent api key>" }
       }
     }
   }
   ```

## 验收

- 协议级冒烟：`skill-pack/verify/acceptance-smoke.sh`（initialize / tools/list / 只读调用 / 审计轨迹校验）。
- 人工走查：见 `docs/agent-platform-claude-code-acceptance.md`。

## 安全边界（平台强制，技能不依赖）

- 无有效 API Key 的请求一律 401；MCP 只接受机器身份（`X-API-Key`）。
- `readonly` scope 主体调用写工具会被拒（`ACCESS_DENIED`）。
- 输出边界强制脱敏（密码/令牌/密钥/敏感头），请求与执行操作落审计库。
- 取证必须先声明目的/影响/成本并经人工审批；验证结论只输出事实与三态判定，不下根因。
