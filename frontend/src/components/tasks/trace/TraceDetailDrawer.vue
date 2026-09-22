<template>
  <a-drawer
    :open="open"
    width="640"
    placement="right"
    :title="detail?.trace?.entry ?? '链路详情'"
    @close="emit('update:open', false)"
  >
    <a-spin :spinning="loading">
      <div v-if="trace" class="td-body">
        <div class="td-meta">
          <a-tag :color="trace.error ? 'error' : 'success'">{{ trace.error ? '错误' : '成功' }}</a-tag>
          <span class="td-tid">traceId: {{ trace.traceId }}</span>
          <a-button
            type="link"
            size="small"
            class="td-sw-open"
            @click="message.info('SkyWalking UI 定位链接待配置外跳地址后开放')"
          >在 SkyWalking 中打开 ↗</a-button>
        </div>

        <div class="td-strip">
          <div class="td-cell"><span>总耗时</span><strong>{{ formatDuration(trace.durationMs) }}</strong></div>
          <div class="td-cell"><span>Span</span><strong>{{ rows.length }}</strong></div>
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

        <div v-for="(item, i) in rows" :key="`${item.segmentId}-${item.spanId}-${i}`" class="wf-row">
          <div class="wf-label" :style="{ paddingLeft: `${item.level * 14}px` }">
            <span class="wf-l1">
              <span class="wf-swatch" :style="{ background: serviceColor(item.service) }" />{{ item.service }}
            </span>
            <span class="wf-l2">{{ item.endpointName }}</span>
          </div>
          <div class="wf-track">
            <span v-for="g in GRID_POSITIONS" :key="g" class="wf-grid" :style="{ left: g }" />
            <a-tooltip>
              <template #title>
                <div class="wf-tip-name">{{ item.service }} · {{ item.endpointName }}</div>
                <div>起始 +{{ windowOffsetOf(item) }}ms ｜ 自身耗时 {{ item.endTimeMillis - item.startTimeMillis }}ms</div>
                <div v-if="item.errorMessage">✕ {{ item.errorMessage }}</div>
              </template>
              <span class="wf-bar" :class="{ 'is-err': item.isError }" :style="barStyle(item)" />
            </a-tooltip>
            <span v-if="item.isError" class="wf-err-tag" :style="errTagStyle(item)">错误</span>
          </div>
        </div>

        <p class="wf-foot">
          条 = span（横向位置 = 起始偏移 / 长度 = 自身耗时）；缩进 = 父子层级；红框 + 「错误」标签 = 异常 span。
        </p>
      </div>

      <div v-else-if="detail && !detail.available" class="td-state">
        <p class="td-state-title">{{ unavailableTitle }}</p>
        <p>{{ unavailableText }}</p>
      </div>

      <div v-else class="td-state">
        <p>{{ loading ? '链路详情加载中…' : '暂无链路详情' }}</p>
      </div>
    </a-spin>
  </a-drawer>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { message } from 'ant-design-vue';
import type { ExecutionTraceDetail, TraceSpan } from '../../../types';
import { computeSpanLevels, traceServiceColor } from '../../../utils/traces-view';

const props = defineProps<{
  open: boolean;
  /** 面板预取的详情响应（available=false 时 trace 为 null，占位文案按 missingReason 区分）。 */
  detail: ExecutionTraceDetail | null;
  loading?: boolean;
}>();

const emit = defineEmits<{ (e: 'update:open', value: boolean): void }>();

const GRID_POSITIONS = ['25%', '50%', '75%'];

type WaterfallRow = TraceSpan & { level: number };

const trace = computed(() => props.detail?.trace ?? null);

/** 扁平 span + computeSpanLevels 推导的缩进层级（父链组装在后端语义里由 parentSpanId 表达）。 */
const rows = computed<WaterfallRow[]>(() => {
  const spans = trace.value?.spans ?? [];
  const levels = computeSpanLevels(spans);
  return spans.map((span, i) => ({ ...span, level: levels[i] ?? 0 }));
});

const serviceCount = computed(() => new Set(rows.value.map(s => s.service)).size);
const errorSpanCount = computed(() => rows.value.filter(s => s.isError).length);

// 瀑布几何按 span 实际时间窗计算（min 开始 – max 结束），保证条形完整落在 0–100% 区间。
const windowStartMs = computed(() => rows.value.reduce((min, s) => Math.min(min, s.startTimeMillis), Number.MAX_SAFE_INTEGER));
const windowTotalMs = computed(() =>
  rows.value.reduce((max, s) => Math.max(max, s.endTimeMillis), windowStartMs.value) - windowStartMs.value);

const axisTicks = computed(() => {
  const total = windowTotalMs.value || 1;
  return [0, 0.25, 0.5, 0.75, 1].map(p => ({
    left: `${p * 100}%`,
    label: `${Math.round(total * p)}ms`,
  }));
});

const unavailableTitle = computed(() =>
  props.detail?.missingReason === 'retention-expired' ? '详情已过保留期' : '链路详情暂不可用');
const unavailableText = computed(() => {
  if (props.detail?.missingReason === 'retention-expired') {
    return '该 trace 的 span 明细已超出 SkyWalking 保留期，无法绘制瀑布图；列表摘要仍来自执行终态快照。';
  }
  if (props.detail?.missingReason === 'unconfigured') {
    return '未配置链路数据源，无法查看 span 明细。';
  }
  return 'SkyWalking OAP 连接失败，无法获取 span 明细，请检查 OAP 服务与网络连通性。';
});

function serviceColor(service: string): string {
  return traceServiceColor(service);
}

function formatDuration(ms: number): string {
  return ms >= 1000 ? `${(ms / 1000).toFixed(2)}s` : `${ms}ms`;
}

function windowOffsetOf(item: TraceSpan): number {
  return item.startTimeMillis - windowStartMs.value;
}

function pct(value: number): number {
  return (value / (windowTotalMs.value || 1)) * 100;
}

function barStyle(item: TraceSpan) {
  return {
    left: `${pct(windowOffsetOf(item))}%`,
    width: `${Math.max(pct(item.endTimeMillis - item.startTimeMillis), 0.8)}%`,
    background: serviceColor(item.service),
  };
}

function errTagStyle(item: TraceSpan) {
  const right = pct(windowOffsetOf(item)) + Math.max(pct(item.endTimeMillis - item.startTimeMillis), 0.8);
  return { left: `calc(${right}% + 5px)` };
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

.td-state { padding: 40px 12px; text-align: center; }
.td-state-title { font-size: 14px; font-weight: 600; margin-bottom: 6px; }
.td-state p { font-size: 13px; color: #5c6b7a; line-height: 1.7; }

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
