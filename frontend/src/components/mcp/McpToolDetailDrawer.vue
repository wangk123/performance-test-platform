<template>
  <aside class="drawer" role="dialog" aria-modal="true" :aria-label="`工具详情：${tool.name}`">
    <div class="drawer-hd">
      <div class="titles">
        <div class="tool-name">{{ tool.name }}</div>
        <div class="tool-title">{{ tool.title }}</div>
        <div class="badges">
          <span class="badge" :class="`st-${tool.stage}`">{{ mcpStageLabel(tool.stage) }} · {{ tool.stage }}</span>
          <span v-if="tool.requiresWriteScope" class="badge b-write">
            <svg
              class="ic xs"
              viewBox="0 0 24 24"
              aria-hidden="true"
            ><circle cx="7.5" cy="15.5" r="4"/><path d="m10.5 12.5 9-9M17 4l3 3M14 7l2.5 2.5"/></svg>
            需写权限
          </span>
          <span v-else class="badge b-read">只读</span>
          <span
            class="st-ic"
            :class="enabled ? 'ok' : 'off'"
            role="img"
            :aria-label="enabled ? '可用' : '不可用'"
            :title="enabled ? '可用' : '不可用'"
          >
            <svg
              v-if="enabled"
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
        </div>
      </div>
      <button class="drawer-close" type="button" aria-label="关闭详情" @click="emit('close')">
        <svg
          class="ic"
          viewBox="0 0 24 24"
          aria-hidden="true"
        ><path d="M6 6l12 12M18 6 6 18"/></svg>
      </button>
    </div>

    <div class="drawer-bd">
      <section class="sec">
        <h3>
          <svg
            class="ic"
            viewBox="0 0 24 24"
            aria-hidden="true"
          ><path d="M2 4h6a4 4 0 0 1 4 4v13a3 3 0 0 0-3-3H2z"/><path d="M22 4h-6a4 4 0 0 0-4 4v13a3 3 0 0 1 3-3h7z"/></svg>
          功能说明
        </h3>
        <p class="desc">{{ tool.description }}</p>
      </section>

      <section class="sec">
        <h3>
          <svg
            class="ic"
            viewBox="0 0 24 24"
            aria-hidden="true"
          ><path d="M4 6h9M17 6h3M4 12h3M11 12h9M4 18h9M17 18h3"/><circle cx="15" cy="6" r="2"/><circle cx="9" cy="12" r="2"/><circle cx="15" cy="18" r="2"/></svg>
          入参
        </h3>
        <table class="params">
          <thead>
            <tr>
              <th style="width: 26%">参数</th>
              <th style="width: 14%">类型</th>
              <th style="width: 12%">必填</th>
              <th>说明</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="param in parameters" :key="param.name">
              <td class="p-name">{{ param.name }}</td>
              <td class="p-type">{{ param.type }}</td>
              <td>
                <span v-if="param.required" class="req">必填</span>
                <span v-else class="opt">可选</span>
              </td>
              <td>{{ param.description }}</td>
            </tr>
          </tbody>
        </table>
      </section>

      <section class="sec">
        <h3>
          <svg
            class="ic"
            viewBox="0 0 24 24"
            aria-hidden="true"
          ><path d="M4 17l6-5-6-5"/><path d="M12 19h8"/></svg>
          使用示例
        </h3>
        <div v-if="tool.usageExample" class="codebox">
          <pre>{{ tool.usageExample }}</pre>
          <button class="copy-btn" type="button" :class="{ done: copied }" @click="copyExample">
            <svg
              class="ic xs"
              viewBox="0 0 24 24"
              aria-hidden="true"
            ><rect x="9" y="9" width="12" height="12" rx="2"/><path d="M5 15V5a2 2 0 0 1 2-2h10"/></svg>
            {{ copied ? '已复制' : '复制示例' }}
          </button>
        </div>
        <p v-else class="no-example">该工具暂未提供使用示例。</p>
      </section>
    </div>

    <div class="drawer-ft">
      <svg
        class="ic"
        viewBox="0 0 24 24"
        aria-hidden="true"
      ><circle cx="7.5" cy="15.5" r="4"/><path d="m10.5 12.5 9-9M17 4l3 3M14 7l2.5 2.5"/></svg>
      <span>
        调用本工具需 Agent API Key{{ tool.requiresWriteScope ? '（写权限工具需非只读 scope）' : '' }} ·
        <RouterLink to="/settings">前往系统配置申请</RouterLink>
      </span>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import type { McpToolSummary } from '../../types';
import { mcpStageLabel } from '../../utils/format';
import { copyToClipboard } from '../../utils/clipboard';

type ToolParameter = { name: string; type: string; required: boolean; description: string };

const props = defineProps<{ tool: McpToolSummary }>();
const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'copied', message: string): void;
}>();

const enabled = computed(() => props.tool.status !== 'DISABLED');
const copied = ref(false);

const parameters = computed<ToolParameter[]>(() => {
  const schema = props.tool.inputSchema as {
    properties?: Record<string, { type?: string | string[]; description?: string }>;
    required?: string[];
  } | null;
  const properties = schema?.properties ?? {};
  const required = new Set(schema?.required ?? []);
  const entries = Object.entries(properties);
  if (entries.length === 0) {
    return [{ name: '—', type: 'void', required: false, description: '无入参' }];
  }
  return entries.map(([name, property]) => ({
    name,
    type: Array.isArray(property.type) ? property.type.join(' | ') : String(property.type ?? 'any'),
    required: required.has(name),
    description: property.description ?? '',
  }));
});

async function copyExample() {
  if (await copyToClipboard(props.tool.usageExample)) {
    copied.value = true;
    emit('copied', '示例已复制到剪贴板');
    window.setTimeout(() => {
      copied.value = false;
    }, 1800);
  }
}
</script>

<style scoped>
.drawer {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  width: 520px;
  max-width: 92vw;
  background: var(--surface);
  box-shadow: -12px 0 32px rgba(26, 35, 50, 0.14);
  z-index: 100;
  display: flex;
  flex-direction: column;
  animation: mcp-drawer-in 0.24s cubic-bezier(0.32, 0.72, 0.35, 1);
}

@keyframes mcp-drawer-in {
  from { transform: translateX(102%); }
  to { transform: translateX(0); }
}

.drawer .ic {
  width: 16px;
  height: 16px;
  flex: none;
  stroke: currentColor;
  fill: none;
  stroke-width: 1.7;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.drawer .ic.xs { width: 12px; height: 12px; }

.drawer-hd {
  padding: 18px 22px 14px;
  border-bottom: 1px solid var(--line);
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.drawer-hd .titles { min-width: 0; }

.drawer-hd .tool-name {
  font-family: var(--font-data);
  font-size: 16px;
  font-weight: 600;
  color: var(--ink);
  word-break: break-all;
}

.drawer-hd .tool-title { margin-top: 2px; font-size: 13px; font-weight: 600; color: var(--ink); }

.drawer-hd .badges { margin-top: 8px; display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }

.drawer-close {
  margin-left: auto;
  width: 30px;
  height: 30px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--muted);
  transition: all 0.18s;
  flex: none;
  border: none;
  background: none;
  cursor: pointer;
}

.drawer-close:hover { background: var(--surface-soft); color: var(--ink); }

.drawer-bd {
  overflow-y: auto;
  padding: 20px 22px 28px;
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.drawer .sec h3 {
  margin: 0 0 10px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 1px;
  color: var(--muted);
  text-transform: uppercase;
  display: flex;
  align-items: center;
  gap: 7px;
}

.drawer .sec h3 .ic { width: 14px; height: 14px; color: var(--primary-dark); }

.drawer .sec .desc { margin: 0; font-size: 13.5px; color: var(--ink); }

table.params {
  width: 100%;
  border-collapse: collapse;
  font-size: 12.5px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.params th {
  background: var(--surface-soft);
  text-align: left;
  font-weight: 600;
  color: var(--muted);
  padding: 8px 12px;
  border-bottom: 1px solid var(--line);
  font-size: 12px;
}

.params td {
  padding: 8px 12px;
  border-bottom: 1px solid var(--line);
  vertical-align: top;
  color: var(--ink);
}

.params tr:last-child td { border-bottom: none; }

.params td.p-name { font-family: var(--font-data); font-size: 12px; white-space: nowrap; }

.params td.p-type { font-family: var(--font-data); font-size: 11.5px; color: var(--muted); white-space: nowrap; }

.params .req { color: var(--danger); font-weight: 700; }

.params .opt { color: var(--muted); }

.drawer .codebox { position: relative; }

.drawer .codebox pre {
  margin: 0;
  background: #0e1720;
  color: #d7e3ec;
  border-radius: var(--radius-sm);
  padding: 14px 16px;
  overflow: auto;
  font-family: var(--font-data);
  font-size: 12.5px;
  line-height: 1.7;
}

.drawer .copy-btn {
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

.drawer .copy-btn:hover { background: rgba(255, 255, 255, 0.16); }

.drawer .copy-btn.done { color: #a7d8a0; }

.drawer .no-example { margin: 0; font-size: 12.5px; color: var(--muted); }

.drawer-ft {
  border-top: 1px solid var(--line);
  padding: 14px 24px;
  background: var(--surface-soft);
  font-size: 12.5px;
  color: var(--muted);
  display: flex;
  align-items: center;
  gap: 9px;
}

.drawer-ft .ic { color: var(--primary-dark); width: 15px; height: 15px; }

.drawer-ft a { font-weight: 600; color: var(--primary-dark); }

/* 徽标与状态图标（与页面同名约定，scoped 独立） */
.drawer .badge {
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

.drawer .st-PLAN { background: var(--accent-soft); color: var(--primary-dark); }
.drawer .st-NAVIGATE { background: #e8effd; color: #2563eb; }
.drawer .st-DESIGN { background: #f1eafd; color: #7c3aed; }
.drawer .st-OBSERVE { background: #eef2f6; color: #475569; }
.drawer .st-DIAGNOSE { background: var(--warning-soft); color: var(--warn); }
.drawer .st-VERIFY { background: var(--ok-soft); color: var(--ok); }
.drawer .st-CAPTURE { background: #fdeaf3; color: #be185d; }

.drawer .b-write {
  background: var(--warning-soft);
  border: 1px solid var(--warning-border);
  color: var(--warn);
}

.drawer .b-read { background: var(--surface); border: 1px solid var(--line); color: var(--muted); }

.drawer .st-ic { display: inline-flex; align-items: center; justify-content: center; flex: none; }
.drawer .st-ic .ic { width: 15px; height: 15px; }
.drawer .st-ic.ok { color: var(--ok); }
.drawer .st-ic.off { color: #8aa0b0; }

@media (max-width: 720px) {
  .drawer { width: 100vw; max-width: 100vw; }
}

@media (prefers-reduced-motion: reduce) {
  .drawer { animation: none !important; }
}
</style>
