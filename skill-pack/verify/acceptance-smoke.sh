#!/usr/bin/env bash
# T13 协议级验收冒烟脚本：对运行中的平台执行 MCP Streamable HTTP 端到端检查。
# 用法：PLATFORM_URL=http://127.0.0.1:8080 API_KEY=<agent-api-key> ./acceptance-smoke.sh
set -euo pipefail

PLATFORM_URL="${PLATFORM_URL:-http://127.0.0.1:8080}"
API_KEY="${API_KEY:?API_KEY required}"
MCP_ENDPOINT="$PLATFORM_URL/mcp"
PROTOCOL_VERSION="2025-06-18"
SESSION_ID=""
PASS=0
FAIL=0

step() { echo; echo "== $1"; }

fail() { echo "FAIL: $1"; FAIL=$((FAIL + 1)); }
ok() { echo "ok: $1"; PASS=$((PASS + 1)); }

rpc() { # method params
  local method="$1" params="$2"
  local headers=(-H "Content-Type: application/json" \
                 -H "Accept: application/json, text/event-stream" \
                 -H "MCP-Protocol-Version: $PROTOCOL_VERSION" \
                 -H "X-API-Key: $API_KEY")
  if [ -n "$SESSION_ID" ]; then
    headers+=(-H "Mcp-Session-Id: $SESSION_ID")
  fi
  curl -sS -m 30 -D /tmp/mcp-headers.txt "${headers[@]}" \
       -d "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"$method\",\"params\":$params}" \
       "$MCP_ENDPOINT"
}

# 1. 无身份 -> 401
step "无身份请求必须 401"
HTTP_CODE=$(curl -sS -m 10 -o /dev/null -w '%{http_code}' \
  -H "Content-Type: application/json" -H "Accept: application/json, text/event-stream" \
  -H "MCP-Protocol-Version: $PROTOCOL_VERSION" \
  -d "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"$PROTOCOL_VERSION\",\"capabilities\":{},\"clientInfo\":{\"name\":\"smoke\",\"version\":\"1\"}}}" \
  "$MCP_ENDPOINT")
if [ "$HTTP_CODE" = "401" ]; then ok "未认证 401"; else fail "期望 401，实际 $HTTP_CODE"; fi

# 2. initialize
step "initialize"
INIT=$(rpc "initialize" "{\"protocolVersion\":\"$PROTOCOL_VERSION\",\"capabilities\":{},\"clientInfo\":{\"name\":\"smoke\",\"version\":\"1\"}}")
if echo "$INIT" | grep -q '"serverInfo"'; then ok "initialize 成功"; else fail "initialize 响应异常: $INIT"; fi
SESSION_ID=$(grep -i '^mcp-session-id:' /tmp/mcp-headers.txt | tr -d '\r' | awk '{print $2}')
echo "session: ${SESSION_ID:-<stateless>}"

# 3. tools/list
step "tools/list"
LIST=$(rpc "tools/list" "{}")
for tool in list_projects start_execution inspect_execution analyze_execution collect_evidence \
            request_evidence_capture register_change verify_change; do
  if echo "$LIST" | grep -q "\"name\":\"$tool\""; then ok "工具可见: $tool"; else fail "工具缺失: $tool"; fi
done

# 4. 只读工具调用（list_projects）
step "tools/call list_projects"
CALL=$(rpc "tools/call" '{"name":"list_projects","arguments":{}}')
if echo "$CALL" | grep -q '"isError":false'; then ok "list_projects 调用成功"; else fail "list_projects 失败: $CALL"; fi
if echo "$CALL" | grep -q '"items"'; then ok "结果含 items"; else fail "结果缺少 items"; fi

# 5. 审计轨迹校验：本次调用应出现在请求审计中
step "审计轨迹校验"
AUDIT=$(rpc "tools/call" '{"name":"list_projects","arguments":{}}' >/dev/null; \
  curl -sS -m 30 -H "X-API-Key: $API_KEY" "$PLATFORM_URL/api/agent/audit/requests?limit=20")
if echo "$AUDIT" | grep -q '"/mcp"'; then ok "MCP 调用已入审计库"; else fail "审计库未记录 MCP 调用: $AUDIT"; fi

echo
echo "== 结果：通过 $PASS，失败 $FAIL"
[ "$FAIL" -eq 0 ]
