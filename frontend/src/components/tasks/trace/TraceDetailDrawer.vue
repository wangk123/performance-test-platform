<template>
  <a-drawer
    :open="open"
    width="640"
    placement="right"
    :title="trace?.entry ?? '链路详情'"
    @close="emit('update:open', false)"
  >
    <div v-if="trace" class="td-body">
      <div class="td-meta">
        <a-tag :color="trace.error ? 'error' : 'success'">{{ trace.error ? '错误' : '成功' }}</a-tag>
        <span class="td-tid">traceId: {{ trace.traceId }}</span>
        <a-button
          type="link"
          size="small"
          class="td-sw-open"
          @click="message.info('将新窗口打开 SkyWalking UI 定位到该 trace（原型不外跳）')"
        >在 SkyWalking 中打开 ↗</a-button>
      </div>

      <div class="td-strip">
        <div class="td-cell"><span>总耗时</span><strong>{{ formatDuration(trace.durationMs) }}</strong></div>
        <div class="td-cell"><span>Span</span><strong>{{ trace.spans.length }}</strong></div>
        <div class="td-cell"><span>服务</span><strong>{{ serviceCount }}</strong></div>
        <div class="td-cell">
          <span>错误 Span</span>
          <strong :class="{ 'td-err': errorSpanCount > 0 }">{{ errorSpanCount }}</strong>
        </div>
      </div>

      <div class="wf-axis">
        <span
          v-for="tick in axisTicks"
          :key="tick.label"
          class="wf-tick"
          :style="{ left: tick.left }"
        >{{ tick.label }}</span>
      </div>

      <div v-for="(item, i) in trace.spans" :key="i" class="wf-row">
        <div class="wf-label" :style="{ paddingLeft: `${item.level * 14}px` }">
          <span class="wf-l1">
            <span class="wf-swatch" :style="{ background: serviceColor(item.service) }" />{{ item.service }}
          </span>
          <span class="wf-l2">{{ item.name }}</span>
        </div>
        <div class="wf-track">
          <span v-for="g in GRID_POSITIONS" :key="g" class="wf-grid" :style="{ left: g }" />
          <a-tooltip>
            <template #title>
              <div class="wf-tip-name">{{ item.service }} · {{ item.name }}</div>
              <div>起始 +{{ item.startMs }}ms ｜ 自身耗时 {{ item.durationMs }}ms</div>
              <div v-if="item.errorMessage">✕ {{ item.errorMessage }}</div>
            </template>
            <span class="wf-bar" :class="{ 'is-err': item.error }" :style="barStyle(item)" />
          </a-tooltip>
          <span v-if="item.error" class="wf-err-tag" :style="errTagStyle(item)">错误</span>
        </div>
      </div>

      <p class="wf-foot">
        条 = span（横向位置 = 起始偏移 / 长度 = 自身耗时）；缩进 = 父子层级；红框 + 「错误」标签 = 异常 span。
        Phase 1 本区域为摘要 + SkyWalking deep-link，本瀑布图为 Phase 2 目标态。
      </p>
    </div>
  </a-drawer>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { message } from 'ant-design-vue';
import { TRACE_SERVICE_COLORS, type TraceListItem, type TraceSpan } from './trace-mock';

const props = defineProps<{
  open: boolean;
  trace: TraceListItem | null;
}>();

const emit = defineEmits<{ (e: 'update:open', value: boolean): void }>();

const GRID_POSITIONS = ['25%', '50%', '75%'];

const serviceCount = computed(() => new Set(props.trace?.spans.map(s => s.service) ?? []).size);
const errorSpanCount = computed(() => props.trace?.spans.filter(s => s.error).length ?? 0);

const axisTicks = computed(() => {
  const total = props.trace?.durationMs ?? 0;
  return [0, 0.25, 0.5, 0.75, 1].map(p => ({
    left: `${p * 100}%`,
    label: `${Math.round(total * p)}ms`,
  }));
});

function serviceColor(service: string): string {
  return TRACE_SERVICE_COLORS[service] ?? '#8a97a5';
}

function formatDuration(ms: number): string {
  return ms >= 1000 ? `${(ms / 1000).toFixed(2)}s` : `${ms}ms`;
}

function pct(value: number): number {
  const total = props.trace?.durationMs ?? 1;
  return (value / total) * 100;
}

function barStyle(item: TraceSpan) {
  return {
    left: `${pct(item.startMs)}%`,
    width: `${Math.max(pct(item.durationMs), 0.8)}%`,
    background: serviceColor(item.service),
  };
}

function errTagStyle(item: TraceSpan) {
  return { left: `calc(${pct(item.startMs)}% + ${Math.max(pct(item.durationMs), 0.8)}% + 5px)` };
}
</script>

<style scoped>
.td-body { display: flex; flex-direction: column; }

.td-meta { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-bottom: 12px; }
.td-tid { font-family: var(--font-data, ui-monospace, Menlo, Consolas, monospace);
  font-size: 11px; color: #5c6b7a; word-break: break-all; }
.td-sw-open { margin-left: auto; }

.td-strip { display: grid; grid-template-columns: repeat(4, 1fr);
  border: 1px solid #e2e8ee; border-radius: 6px; overflow: hidden; margin-bottom: 14px; }
.td-cell { padding: 8px 12px; border-right: 1px solid #e2e8ee; }
.td-cell:last-child { border-right: none; }
.td-cell span { display: block; font-size: 11px; color: #5c6b7a; margin-bottom: 2px; }
.td-cell strong { font-size: 14px;
  font-family: var(--font-data, ui-monospace, Menlo, Consolas, monospace); }
.td-err { color: #d14343; }

.wf-axis { position: relative; height: 22px; margin-left: 216px; border-bottom: 1px solid #c8d3dc; }
.wf-tick { position: absolute; bottom: 0; transform: translateX(-50%); padding-bottom: 3px;
  font-family: var(--font-data, ui-monospace, Menlo, Consolas, monospace);
  font-size: 10px; color: #5c6b7a; }

.wf-row { display: flex; align-items: center; min-height: 30px;
  border-bottom: 1px solid #eef2f5; }
.wf-row:hover { background: #f6fafb; }
.wf-label { width: 216px; flex: none; display: flex; flex-direction: column; gap: 1px;
  padding: 4px 8px 4px 0; overflow: hidden; }
.wf-l1 { display: flex; align-items: center; gap: 6px; font-size: 11.5px; font-weight: 600;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.wf-swatch { width: 8px; height: 8px; border-radius: 2px; flex: none; }
.wf-l2 { font-size: 10.5px; color: #5c6b7a;
  font-family: var(--font-data, ui-monospace, Menlo, Consolas, monospace);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis; padding-left: 14px; }

.wf-track { flex: 1; position: relative; height: 100%; min-height: 30px; }
.wf-grid { position: absolute; top: 0; bottom: 0; width: 1px; background: #edf1f4; }
.wf-bar { position: absolute; top: 50%; transform: translateY(-50%); height: 13px;
  border-radius: 3px; min-width: 3px; cursor: pointer; }
.wf-bar:hover { filter: brightness(0.88); }
.wf-bar.is-err { box-shadow: 0 0 0 1.5px #d14343; }
.wf-err-tag { position: absolute; top: 50%; transform: translateY(-50%); font-size: 9.5px;
  font-weight: 700; color: #d14343; background: #fbecec; border-radius: 3px;
  padding: 1px 4px; white-space: nowrap; }

.wf-tip-name { font-weight: 600;
  font-family: var(--font-data, ui-monospace, Menlo, Consolas, monospace); }

.wf-foot { margin-top: 12px; font-size: 11px; color: #5c6b7a; line-height: 1.7;
  border-top: 1px dashed #e2e8ee; padding-top: 9px; }
</style>
