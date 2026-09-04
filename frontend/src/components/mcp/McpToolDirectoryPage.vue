<template>
  <section class="mcp-directory">
    <div class="page-head">
      <div>
        <h1>MCP 工具目录</h1>
        <p class="sub">
          平台全部已注册 MCP 工具的能力清单——本地 Agent（Claude Code / DSH）接入后即可调用。
          工具随版本发布自动上下线，本页无需配置。
        </p>
      </div>
      <div class="head-chips">
        <span class="chip chip-ok"><span class="dot"></span>MCP 服务在线</span>
        <span v-if="directory" class="chip chip-neutral">
          {{ directory.server.toolCount }} 个工具 · {{ directory.stages.length }} 个阶段
        </span>
      </div>
    </div>

    <section class="access" aria-label="接入指引">
      <div class="access-hd">
        <svg
          class="ic"
          viewBox="0 0 24 24"
          aria-hidden="true"
        ><path d="M9 3v6M15 3v6"/><path d="M7 9h10v3a5 5 0 0 1-10 0z"/><path d="M12 17v4"/></svg>
        <h2>本地 Agent 接入指引</h2>
        <span class="hint">三步完成，全程无需找人</span>
      </div>
      <div class="access-bd">
        <div class="steps">
          <div class="step">
            <span class="step-no">1</span>
            <div>
              <b>申请 Agent API Key</b>
              <p>由管理员在「系统配置 → Agent API Key」签发；只读演练用 <code>readonly</code> scope，含写操作的全流程用普通 scope。</p>
              <RouterLink class="link-btn" to="/settings">
                前往系统配置申请
                <svg
                  class="ic sm"
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                ><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/><path d="M15 3h6v6M10 14 21 3"/></svg>
              </RouterLink>
            </div>
          </div>
          <div class="step">
            <span class="step-no">2</span>
            <div>
              <b>复制 Agent 配置片段</b>
              <p>粘贴到本地 Agent 的 MCP 配置文件，地址与鉴权头已按当前环境填好。</p>
            </div>
          </div>
          <div class="step">
            <span class="step-no">3</span>
            <div>
              <b>开箱即用</b>
              <p>Agent 重启后自动发现下方全部工具；写权限工具需非只读 API Key 调用。</p>
            </div>
          </div>
        </div>
        <div class="config">
          <div class="config-top">
            <div class="seg" role="tablist" aria-label="选择 Agent 类型">
              <button
                type="button"
                role="tab"
                :aria-selected="agentKind === 'cc'"
                :class="{ on: agentKind === 'cc' }"
                @click="agentKind = 'cc'"
              >Claude Code</button>
              <button
                type="button"
                role="tab"
                :aria-selected="agentKind === 'dsh'"
                :class="{ on: agentKind === 'dsh' }"
                @click="agentKind = 'dsh'"
              >DSH</button>
            </div>
            <span class="endpoint"><span class="method">POST</span> {{ mcpEndpoint }}</span>
          </div>
          <div class="codebox">
            <!-- eslint-disable-next-line vue/no-v-html -- 内容为本组件生成的转义高亮文本，无用户输入 -->
            <pre v-html="highlightedConfig"></pre>
            <button class="copy-btn" type="button" :class="{ done: configCopied }" @click="copyConfig">
              <svg
                class="ic xs"
                viewBox="0 0 24 24"
                aria-hidden="true"
              ><rect x="9" y="9" width="12" height="12" rx="2"/><path d="M5 15V5a2 2 0 0 1 2-2h10"/></svg>
              {{ configCopied ? '已复制' : '复制配置' }}
            </button>
          </div>
          <p class="scope-note">
            <svg
              class="ic sm"
              viewBox="0 0 24 24"
              aria-hidden="true"
            ><circle cx="12" cy="12" r="9"/><path d="M12 11v5M12 8h.01"/></svg>
            认证方式为请求头 <code>X-API-Key</code>；平台仅接受机器身份访问 MCP，Web 账号不能直连。
          </p>
        </div>
      </div>
    </section>

    <div v-if="directory" class="toolbar">
      <div class="tabs" role="tablist" aria-label="按阶段筛选">
        <button
          v-for="tab in stageTabs"
          :key="tab.stage"
          type="button"
          role="tab"
          class="tab"
          :class="{ on: currentStage === tab.stage }"
          :aria-selected="currentStage === tab.stage"
          @click="currentStage = tab.stage"
        >{{ tab.label }}<span class="n">{{ tab.count }}</span></button>
      </div>
      <label class="search">
        <svg
          class="ic"
          viewBox="0 0 24 24"
          aria-hidden="true"
        ><circle cx="11" cy="11" r="7"/><path d="m21 21-4.3-4.3"/></svg>
        <input
          v-model="keyword"
          type="search"
          placeholder="搜索工具名称 / 说明…"
          aria-label="搜索工具"
        />
      </label>
      <span class="result-count">{{ filteredTools.length }} / {{ tools.length }} 个工具</span>
      <span class="legend" aria-label="状态图例">
        <span class="st-ic ok" role="img" aria-label="可用" title="可用">
          <svg
            class="ic"
            viewBox="0 0 24 24"
            aria-hidden="true"
          ><circle cx="12" cy="12" r="9" fill="currentColor" stroke="none"/><path d="m8.3 12.4 2.6 2.6 4.9-5.4" stroke="#fff" stroke-width="2"/></svg>
        </span>
        <span class="st-ic off" role="img" aria-label="不可用" title="不可用">
          <svg
            class="ic"
            viewBox="0 0 24 24"
            aria-hidden="true"
          ><circle cx="12" cy="12" r="8.4"/><path d="M6.4 6.4 17.6 17.6"/></svg>
        </span>
      </span>
    </div>

    <div v-if="directory" class="grid-area">
      <div v-if="filteredTools.length" class="grid">
        <button
          v-for="tool in filteredTools"
          :key="tool.name"
          type="button"
          class="card"
          :class="{ off: tool.status === 'DISABLED' }"
          aria-haspopup="dialog"
          @click="openTool(tool)"
        >
          <div class="card-top">
            <span class="tool-name">{{ tool.name }}</span>
            <span class="badge stage" :class="`st-${tool.stage}`">{{ mcpStageLabel(tool.stage) }}</span>
          </div>
          <div class="tool-title">{{ tool.title }}</div>
          <p class="tool-desc">{{ tool.description }}</p>
          <div class="badges">
            <span v-if="tool.requiresWriteScope" class="badge b-write">
              <svg
                class="ic xs"
                viewBox="0 0 24 24"
                aria-hidden="true"
              ><circle cx="7.5" cy="15.5" r="4"/><path d="m10.5 12.5 9-9M17 4l3 3M14 7l2.5 2.5"/></svg>
              需写权限
            </span>
            <span v-else class="badge b-read">只读</span>
          </div>
          <div class="card-foot">
            <span class="card-stage">
              <span
                class="st-ic"
                :class="tool.status === 'DISABLED' ? 'off' : 'ok'"
                role="img"
                :aria-label="tool.status === 'DISABLED' ? '不可用' : '可用'"
                :title="tool.status === 'DISABLED' ? '不可用' : '可用'"
              >
                <svg
                  v-if="tool.status !== 'DISABLED'"
                  class="ic"
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                ><circle cx="12" cy="12" r="9" fill="currentColor" stroke="none"/><path d="m8.3 12.4 2.6 2.6 4.9-5.4" stroke="#fff" stroke-width="2"/></svg>
                <svg
                  v-else
                  class="ic"
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                ><circle cx="12" cy="12" r="8.4"/><path d="M6.4 6.4 17.6 17.6"/></svg>
              </span>
              阶段：{{ mcpStageLabel(tool.stage) }}
            </span>
            <span class="detail-hint">
              查看详情
              <svg
                class="ic xs"
                viewBox="0 0 24 24"
                aria-hidden="true"
              ><path d="M4 12h15M13 6l6 6-6 6"/></svg>
            </span>
          </div>
        </button>
      </div>
      <div v-else class="empty">
        <svg
          class="ic lg"
          viewBox="0 0 24 24"
          aria-hidden="true"
        ><circle cx="11" cy="11" r="7"/><path d="m21 21-4.3-4.3"/></svg>
        <p>未找到匹配「{{ keyword.trim() }}」的工具</p>
        <button type="button" @click="clearSearch">清除搜索</button>
      </div>
    </div>

    <div v-if="loading" class="state-note">正在加载工具清单…</div>
    <div v-else-if="error" class="state-note error">
      {{ error }}
      <button class="retry-btn" type="button" @click="load">重试</button>
    </div>

    <footer class="page-ft">
      <span class="ft-left">
        <svg
          class="ic sm"
          viewBox="0 0 24 24"
          aria-hidden="true"
        ><circle cx="12" cy="12" r="9"/><path d="M12 11v5M12 8h.01"/></svg>
        工具清单由服务注册表实时生成，随版本发布自动上下线，本页无需任何配置
      </span>
      <span class="ft-right">performance-test-platform · MCP</span>
    </footer>

    <div class="toast" :class="{ show: toastVisible }" role="status">
      <svg
        class="ic sm"
        viewBox="0 0 24 24"
        aria-hidden="true"
      ><path d="m4 12.5 5 5L20 6.5"/></svg>
      <span>{{ toastText }}</span>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { fetchMcpDirectoryApi } from '../../api/mcp-directory';
import type { McpDirectory, McpToolSummary } from '../../types';
import { copyToClipboard } from '../../utils/clipboard';
import { mcpStageLabel } from '../../utils/format';

const directory = ref<McpDirectory | null>(null);
const loading = ref(false);
const error = ref('');
const currentStage = ref('全部');
const keyword = ref('');
const selectedTool = ref<McpToolSummary | null>(null);
const agentKind = ref<'cc' | 'dsh'>('cc');
const configCopied = ref(false);
const toastVisible = ref(false);
const toastText = ref('');
let toastTimer: number | undefined;

const mcpEndpoint = computed(() => `${window.location.origin}/mcp`);

const tools = computed(() => directory.value?.tools ?? []);

const stageTabs = computed(() => {
  const tabs = [{ stage: '全部', label: '全部', count: tools.value.length }];
  for (const stage of directory.value?.stages ?? []) {
    tabs.push({
      stage,
      label: mcpStageLabel(stage),
      count: tools.value.filter((tool) => tool.stage === stage).length,
    });
  }
  return tabs;
});

const filteredTools = computed(() => {
  const kw = keyword.value.trim().toLowerCase();
  return tools.value.filter((tool) => {
    if (currentStage.value !== '全部' && tool.stage !== currentStage.value) {
      return false;
    }
    if (!kw) {
      return true;
    }
    return `${tool.name}\n${tool.title}\n${tool.description}\n${tool.stage}`.toLowerCase().includes(kw);
  });
});

const configText = computed(() =>
  agentKind.value === 'cc' ? claudeCodeConfig(mcpEndpoint.value) : dshConfig(mcpEndpoint.value),
);

const highlightedConfig = computed(() =>
  agentKind.value === 'cc' ? highlightClaudeCode(configText.value) : highlightDsh(configText.value),
);

function claudeCodeConfig(endpoint: string): string {
  return [
    '~/.claude.json — mcpServers 配置：',
    '{',
    '  "mcpServers": {',
    '    "perf-platform": {',
    '      "type": "http",',
    `      "url": "${endpoint}",`,
    '      "headers": { "X-API-Key": "<agent-api-key>" }',
    '    }',
    '  }',
    '}',
  ].join('\n');
}

function dshConfig(endpoint: string): string {
  return [
    'DSH MCP 接入配置（Streamable HTTP）：',
    `endpoint: ${endpoint}`,
    'headers:',
    '  X-API-Key: <agent-api-key>',
  ].join('\n');
}

function escapeHtml(text: string): string {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function highlightClaudeCode(code: string): string {
  const [firstLine, ...rest] = code.split('\n');
  const body = escapeHtml(rest.join('\n')).replace(
    /"([^"\n]+)"(\s*:)?/g,
    (_match, value: string, colon?: string) =>
      colon
        ? `<span class='tok-key'>"${value}"</span>${colon}`
        : `<span class='tok-str'>"${value}"</span>`,
  );
  return `<span class='tok-cmt'>${escapeHtml(firstLine)}</span>\n${body}`;
}

function highlightDsh(code: string): string {
  return escapeHtml(code)
    .replace(/^(#.*)$/gm, "<span class='tok-cmt'>$1</span>")
    .replace(/^(\s*[\w-]+)(:)(.*)$/gm, "<span class='tok-key'>$1</span>$2<span class='tok-str'>$3</span>");
}

function showToast(message: string) {
  toastText.value = message;
  toastVisible.value = true;
  if (toastTimer !== undefined) clearTimeout(toastTimer);
  toastTimer = window.setTimeout(() => {
    toastVisible.value = false;
  }, 1600);
}

async function copyConfig() {
  if (await copyToClipboard(configText.value)) {
    configCopied.value = true;
    showToast('配置已复制到剪贴板');
    window.setTimeout(() => {
      configCopied.value = false;
    }, 1800);
  }
}

function clearSearch() {
  keyword.value = '';
}

function openTool(tool: McpToolSummary) {
  selectedTool.value = tool;
}

function closeDrawer() {
  selectedTool.value = null;
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    closeDrawer();
  }
}

async function load() {
  loading.value = true;
  error.value = '';
  try {
    directory.value = await fetchMcpDirectoryApi();
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败';
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  void load();
  document.addEventListener('keydown', onKeydown);
});

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown);
  if (toastTimer !== undefined) clearTimeout(toastTimer);
});
</script>

<style scoped>
.mcp-directory {
  --shadow-pop: 0 8px 24px rgba(26, 35, 50, 0.1), 0 2px 6px rgba(26, 35, 50, 0.06);
  font-size: 14px;
}

.mcp-directory .ic {
  width: 16px;
  height: 16px;
  flex: none;
  stroke: currentColor;
  fill: none;
  stroke-width: 1.7;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.mcp-directory .ic.sm { width: 14px; height: 14px; }
.mcp-directory .ic.xs { width: 12px; height: 12px; }

/* ===== 页头 ===== */
.mcp-directory .page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
  flex-wrap: wrap;
}

.mcp-directory .page-head h1 {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.2px;
  color: var(--ink);
}

.mcp-directory .page-head .sub {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--muted);
}

.mcp-directory .head-chips { display: flex; gap: 8px; }

.mcp-directory .chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  border-radius: 16px;
  font-size: 12px;
  font-weight: 600;
  border: 1px solid transparent;
}

.mcp-directory .chip .dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex: none;
}

.mcp-directory .chip-ok { background: var(--ok-soft); color: var(--ok); }

.mcp-directory .chip-ok .dot {
  background: var(--ok);
  box-shadow: 0 0 0 3px rgba(47, 155, 106, 0.15);
  animation: mcp-pulse 2.4s infinite;
}

@keyframes mcp-pulse {
  0%, 100% { box-shadow: 0 0 0 3px rgba(47, 155, 106, 0.15); }
  50% { box-shadow: 0 0 0 5px rgba(47, 155, 106, 0.05); }
}

.mcp-directory .chip-neutral { background: var(--surface); color: var(--muted); border-color: var(--line); }

/* ===== 接入指引横幅 ===== */
.mcp-directory .access {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  margin-bottom: 20px;
  overflow: hidden;
}

.mcp-directory .access-hd {
  padding: 14px 20px;
  border-bottom: 1px solid var(--line);
  display: flex;
  align-items: center;
  gap: 10px;
}

.mcp-directory .access-hd .ic { color: var(--primary-dark); }

.mcp-directory .access-hd h2 { margin: 0; font-size: 15px; font-weight: 600; color: var(--ink); }

.mcp-directory .access-hd .hint { margin-left: auto; font-size: 12px; color: var(--muted); }

.mcp-directory .access-bd { display: grid; grid-template-columns: 1fr 1.35fr; }

.mcp-directory .steps {
  padding: 18px 20px;
  border-right: 1px solid var(--line);
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.mcp-directory .step { display: flex; gap: 12px; }

.mcp-directory .step-no {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--accent-soft);
  color: var(--primary-dark);
  font-size: 12px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
  margin-top: 2px;
}

.mcp-directory .step b { font-size: 13px; font-weight: 600; display: block; margin-bottom: 2px; color: var(--ink); }

.mcp-directory .step p { margin: 0; font-size: 12.5px; color: var(--muted); }

.mcp-directory .step .link-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-top: 6px;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--primary-dark);
}

.mcp-directory .step .link-btn:hover { text-decoration: underline; }

.mcp-directory .config { padding: 18px 20px; background: var(--surface-soft); }

.mcp-directory .config-top {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.mcp-directory .seg {
  display: inline-flex;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  padding: 2px;
}

.mcp-directory .seg button {
  padding: 4px 12px;
  border-radius: 4px;
  font-size: 12.5px;
  font-weight: 500;
  color: var(--muted);
  border: none;
  background: none;
  cursor: pointer;
  transition: all 0.18s;
}

.mcp-directory .seg button.on { background: var(--accent); color: var(--primary-ink); }

.mcp-directory .endpoint {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-family: var(--font-data);
  font-size: 12px;
  color: var(--ink);
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  padding: 4px 10px;
}

.mcp-directory .endpoint .method { color: var(--primary-dark); font-weight: 700; }

.mcp-directory .codebox { position: relative; }

.mcp-directory .codebox pre {
  margin: 0;
  background: #0e1720;
  color: #d7e3ec;
  border-radius: var(--radius-sm);
  padding: 14px 16px;
  overflow: auto;
  font-family: var(--font-data);
  font-size: 12.5px;
  line-height: 1.7;
  max-height: 218px;
  text-align: left;
}

.mcp-directory .codebox :deep(.tok-key) { color: #7fd4dc; }
.mcp-directory .codebox :deep(.tok-str) { color: #a7d8a0; }
.mcp-directory .codebox :deep(.tok-cmt) { color: #5c7080; font-style: italic; }

.mcp-directory .copy-btn {
  position: absolute;
  top: 8px;
  right: 8px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 10px;
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.08);
  color: #c9d7e2;
  font-size: 12px;
  border: none;
  cursor: pointer;
  transition: background 0.18s;
}

.mcp-directory .copy-btn:hover { background: rgba(255, 255, 255, 0.16); }

.mcp-directory .copy-btn.done { color: #a7d8a0; }

.mcp-directory .scope-note {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--muted);
  display: flex;
  gap: 6px;
  align-items: flex-start;
}

.mcp-directory .scope-note .ic { margin-top: 2px; color: var(--warn); width: 14px; height: 14px; }

/* ===== 状态提示 ===== */
.mcp-directory .state-note { padding: 48px 0; text-align: center; color: var(--muted); font-size: 13.5px; }

.mcp-directory .state-note.error { color: var(--danger); }

.mcp-directory .retry-btn {
  margin-left: 10px;
  font-size: 13px;
  color: var(--primary-dark);
  font-weight: 600;
  background: none;
  border: none;
  cursor: pointer;
}

.mcp-directory .retry-btn:hover { text-decoration: underline; }

/* ===== 页脚 ===== */
.mcp-directory .page-ft {
  margin-top: 34px;
  padding-top: 16px;
  border-top: 1px solid var(--line);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  font-size: 12.5px;
  color: var(--muted);
}

.mcp-directory .page-ft .ft-left { display: inline-flex; align-items: center; gap: 7px; }

.mcp-directory .page-ft .ft-left .ic { color: var(--primary-dark); width: 14px; height: 14px; }

.mcp-directory .page-ft .ft-right { font-family: var(--font-data); font-size: 11.5px; letter-spacing: 0.5px; }

/* ===== Toast ===== */
.mcp-directory .toast {
  position: fixed;
  bottom: 28px;
  left: 50%;
  transform: translate(-50%, 16px);
  background: var(--ink);
  color: #fff;
  border-radius: var(--radius-sm);
  padding: 9px 18px;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 8px;
  opacity: 0;
  pointer-events: none;
  transition: all 0.22s;
  z-index: 200;
  box-shadow: var(--shadow-pop);
}

.mcp-directory .toast.show { opacity: 1; transform: translate(-50%, 0); }

.mcp-directory .toast .ic { color: #7fd4dc; width: 15px; height: 15px; }

/* ===== 工具栏 ===== */
.mcp-directory .toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.mcp-directory .tabs { display: flex; gap: 6px; flex-wrap: wrap; }

.mcp-directory .tab {
  padding: 6px 13px;
  border-radius: 16px;
  border: 1px solid var(--line);
  background: var(--surface);
  font-size: 12.5px;
  font-weight: 500;
  color: var(--muted);
  display: inline-flex;
  align-items: center;
  gap: 6px;
  transition: all 0.18s;
  cursor: pointer;
}

.mcp-directory .tab:hover { border-color: var(--line-strong); color: var(--ink); }

.mcp-directory .tab.on {
  background: var(--accent-soft);
  border-color: var(--active-bg-strong);
  color: var(--primary-dark);
  font-weight: 600;
}

.mcp-directory .tab .n {
  font-family: var(--font-data);
  font-size: 11px;
  background: rgba(26, 35, 50, 0.06);
  border-radius: 9px;
  padding: 0 6px;
  line-height: 17px;
}

.mcp-directory .tab.on .n { background: rgba(11, 127, 138, 0.14); }

.mcp-directory .search {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  padding: 7px 12px;
  width: 250px;
  transition: border-color 0.18s;
}

.mcp-directory .search:focus-within { border-color: var(--accent); }

.mcp-directory .search .ic { color: var(--muted); width: 15px; height: 15px; }

.mcp-directory .search input {
  border: none;
  outline: none;
  font: inherit;
  font-size: 13px;
  width: 100%;
  background: transparent;
  color: var(--ink);
}

.mcp-directory .result-count { font-size: 12.5px; color: var(--muted); white-space: nowrap; }

/* ===== 徽标体系 ===== */
.mcp-directory .badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 9px;
  border-radius: 11px;
  font-size: 11.5px;
  font-weight: 600;
  letter-spacing: 0.2px;
  white-space: nowrap;
}

.mcp-directory .st-PLAN { background: var(--accent-soft); color: var(--primary-dark); }
.mcp-directory .st-NAVIGATE { background: #e8effd; color: #2563eb; }
.mcp-directory .st-DESIGN { background: #f1eafd; color: #7c3aed; }
.mcp-directory .st-OBSERVE { background: #eef2f6; color: #475569; }
.mcp-directory .st-DIAGNOSE { background: var(--warning-soft); color: var(--warn); }
.mcp-directory .st-VERIFY { background: var(--ok-soft); color: var(--ok); }
.mcp-directory .st-CAPTURE { background: #fdeaf3; color: #be185d; }

.mcp-directory .b-write {
  background: var(--warning-soft);
  border: 1px solid var(--warning-border);
  color: var(--warn);
}

.mcp-directory .b-read { background: var(--surface); border: 1px solid var(--line); color: var(--muted); }

/* 状态图标：两态纯图标（可用 / 不可用） */
.mcp-directory .st-ic { display: inline-flex; align-items: center; justify-content: center; flex: none; }
.mcp-directory .st-ic .ic { width: 15px; height: 15px; }
.mcp-directory .st-ic.ok { color: var(--ok); }
.mcp-directory .st-ic.off { color: #8aa0b0; }
.mcp-directory .legend { display: inline-flex; align-items: center; gap: 8px; padding-left: 2px; }

/* ===== 卡片网格：单一平铺，不做阶段分组 ===== */
.mcp-directory .grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(310px, 1fr));
  gap: 14px;
}

.mcp-directory .card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 16px 18px 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  text-align: left;
  position: relative;
  transition: box-shadow 0.18s, border-color 0.18s, transform 0.18s;
  cursor: pointer;
  font: inherit;
  color: inherit;
}

.mcp-directory .card:hover {
  box-shadow: var(--shadow-pop);
  border-color: var(--line-strong);
  transform: translateY(-1px);
}

.mcp-directory .card.off { opacity: 0.6; background: var(--surface-soft); }
.mcp-directory .card.off:hover { opacity: 1; }

.mcp-directory .card-top { display: flex; align-items: flex-start; gap: 10px; }

.mcp-directory .tool-name {
  font-family: var(--font-data);
  font-size: 14.5px;
  font-weight: 600;
  word-break: break-all;
  color: var(--ink);
}

.mcp-directory .card-top .stage { margin-left: auto; }

.mcp-directory .tool-title { font-size: 13px; font-weight: 600; color: var(--ink); }

.mcp-directory .tool-desc {
  margin: 0;
  font-size: 12.5px;
  color: var(--muted);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 40px;
}

.mcp-directory .badges {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: auto;
  padding-top: 8px;
}

.mcp-directory .card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px dashed var(--line);
  padding-top: 9px;
  margin-top: 2px;
  font-size: 12px;
  color: var(--muted);
}

.mcp-directory .card-stage { display: inline-flex; align-items: center; gap: 6px; }

.mcp-directory .detail-hint {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--primary-dark);
  font-weight: 500;
}

.mcp-directory .detail-hint .ic { width: 13px; height: 13px; transition: transform 0.18s; }

.mcp-directory .card:hover .detail-hint .ic { transform: translateX(2px); }

/* ===== 空状态 ===== */
.mcp-directory .empty { text-align: center; padding: 64px 0; color: var(--muted); }

.mcp-directory .empty .ic { width: 34px; height: 34px; margin: 0 auto 12px; color: var(--line-strong); display: block; }

.mcp-directory .empty p { margin: 0; font-size: 13.5px; }

.mcp-directory .empty button {
  margin-top: 12px;
  font-size: 13px;
  color: var(--primary-dark);
  font-weight: 600;
  background: none;
  border: none;
  cursor: pointer;
}

.mcp-directory .empty button:hover { text-decoration: underline; }

/* ===== 响应式 & 动效偏好 ===== */
@media (max-width: 1080px) {
  .mcp-directory .access-bd { grid-template-columns: 1fr; }
  .mcp-directory .steps { border-right: none; border-bottom: 1px solid var(--line); }
}

@media (max-width: 720px) {
  .mcp-directory .search { width: 100%; margin-left: 0; }
  .mcp-directory .grid { grid-template-columns: 1fr; }
}

@media (prefers-reduced-motion: reduce) {
  .mcp-directory *,
  .mcp-directory *::before,
  .mcp-directory *::after {
    transition: none !important;
    animation: none !important;
  }
}
</style>
