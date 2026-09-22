<template>
  <div class="panel trace-panel">
    <div class="panel-header">
      <div>
        <h2>
          链路追踪
          <a-tag class="trace-head-tag" :color="availabilityColor">{{ availabilityText }}</a-tag>
          <a-tag class="trace-head-tag" color="processing">诊断轮 · 全量采样</a-tag>
          <a-tag class="trace-head-tag" color="warning">演示数据</a-tag>
        </h2>
      </div>
      <span class="trace-window">{{ windowText }}</span>
    </div>

    <div class="trace-toolbar">
      <a-radio-group v-model:value="availability" size="small">
        <a-radio-button value="ok">已连接</a-radio-button>
        <a-radio-button value="unconfigured">未配置</a-radio-button>
        <a-radio-button value="down">OAP 不可达</a-radio-button>
      </a-radio-group>
      <a-divider type="vertical" />
      <span class="trace-filter-label">服务</span>
      <a-select
        v-model:value="serviceFilter"
        size="small"
        class="trace-filter-service"
        allow-clear
        placeholder="全部"
      >
        <a-select-option v-for="name in serviceNames" :key="name" :value="name">{{ name }}</a-select-option>
      </a-select>
      <span class="trace-filter-label">接口</span>
      <a-select
        v-model:value="endpointFilter"
        size="small"
        class="trace-filter-endpoint"
        show-search
        allow-clear
        placeholder="全部"
        option-filter-prop="label"
        :options="endpointOptions"
      />
      <span class="trace-filter-label">仅看错误</span>
      <a-switch v-model:checked="onlyError" size="small" />
      <span class="trace-filter-label">慢阈值</span>
      <a-select v-model:value="minDuration" size="small" class="trace-filter-dur">
        <a-select-option :value="0">不限</a-select-option>
        <a-select-option :value="200">≥ 200ms</a-select-option>
        <a-select-option :value="500">≥ 500ms</a-select-option>
        <a-select-option :value="1000">≥ 1s</a-select-option>
      </a-select>
      <span class="trace-filter-label">排序</span>
      <a-select v-model:value="sortBy" size="small" class="trace-filter-sort">
        <a-select-option value="duration">最慢优先</a-select-option>
        <a-select-option value="time">最新优先</a-select-option>
      </a-select>
      <a-button size="small" @click="message.info('已按当前筛选刷新（演示）')">刷新</a-button>
      <span class="trace-count">{{ countText }}</span>
    </div>

    <template v-if="availability === 'ok'">
      <div class="trace-breakdown">
        <span class="trace-bd-label">服务耗时分布</span>
        <div class="trace-bd-bar">
          <a-tooltip v-for="item in serviceBreakdown" :key="item.service">
            <template #title>
              <div><strong>{{ item.service }}</strong></div>
              <div>耗时占比 {{ item.sharePct.toFixed(1) }}%（{{ formatDuration(item.totalMs) }}）</div>
              <div>参与 trace {{ item.traceCount }} 条 · 错误 span {{ item.errorSpans }} 个</div>
            </template>
            <button
              type="button"
              class="trace-bd-seg"
              :class="{ 'is-active': serviceFilter === item.service }"
              :style="{ width: `${item.sharePct}%`, background: serviceColor(item.service) }"
              :aria-label="`按 ${item.service} 筛选`"
              @click="toggleServiceFilter(item.service)"
            />
          </a-tooltip>
        </div>
        <span class="trace-bd-hint">点击色块按服务筛选</span>
      </div>
      <a-table
        class="workspace-table trace-table"
        :columns="columns"
        :data-source="pagedTraces"
        :pagination="false"
        :row-key="(record: TraceListItem) => record.traceId"
        :custom-row="rowEvents"
        size="small"
        :locale="{ emptyText: '当前筛选无匹配 trace' }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'time'">
            <span class="trace-mono">{{ record.time }}</span>
          </template>
          <template v-else-if="column.key === 'service'">
            <span class="trace-svc">
              <span class="trace-swatch" :style="{ background: serviceColor(record.service) }" />{{ record.service }}
            </span>
          </template>
          <template v-else-if="column.key === 'entry'">
            <span class="trace-mono">{{ record.entry }}</span>
          </template>
          <template v-else-if="column.key === 'durationMs'">
            <span class="trace-dur" :class="durClass(record.durationMs)">{{ formatDuration(record.durationMs) }}</span>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="record.error ? 'error' : 'success'">{{ record.error ? '错误' : '成功' }}</a-tag>
          </template>
          <template v-else-if="column.key === 'spanCount'">
            <span class="trace-mono trace-span-count">{{ record.spans.length }}</span>
          </template>
          <template v-else-if="column.key === 'traceId'">
            <span class="trace-mono trace-tid">{{ record.traceId.slice(0, 10) }}…</span>
            <a-button
              type="text"
              size="small"
              class="trace-copy-btn"
              aria-label="复制 TraceId"
              @click.stop="copyTraceId(record.traceId)"
            >
              <CopyOutlined />
            </a-button>
          </template>
        </template>
      </a-table>
      <a-pagination
        class="trace-pagination"
        :current="page"
        :total="filteredTraces.length"
        :page-size="pageSize"
        :page-size-options="['10', '20', '50']"
        show-size-changer
        show-less-items
        size="small"
        :show-total="paginationShowTotal"
        @change="onPageChange"
      />
    </template>

    <div v-else-if="availability === 'unconfigured'" class="trace-state">
      <p class="trace-state-title">未配置链路数据源</p>
      <p>在后端配置中开启 trace 深度源后，可在此按执行时间窗下钻慢/错请求链路：</p>
      <code>platform.evidence.deep.kinds.trace.enabled=true</code>
    </div>

    <div v-else class="trace-state">
      <p class="trace-state-title">链路数据源不可达（SOURCE_UNAVAILABLE）</p>
      <p>SkyWalking OAP 连接失败，请检查 OAP 服务与网络连通性。已结束的执行仍可查看终态固化的 trace 摘要列表。</p>
    </div>

    <TraceDetailDrawer v-model:open="drawerOpen" :trace="selectedTrace" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { TableColumnsType, TableProps } from 'ant-design-vue';
import { message } from 'ant-design-vue';
import { CopyOutlined } from '@ant-design/icons-vue';
import { formatDate } from '../../../utils/format';
import TraceDetailDrawer from './TraceDetailDrawer.vue';
import { MOCK_TRACES, TRACE_SERVICE_COLORS, type TraceListItem } from './trace-mock';

const props = defineProps<{
  executionId: number;
  windowStart: string | null;
  windowEnd: string | null;
}>();

const availability = ref<'ok' | 'unconfigured' | 'down'>('ok');
const serviceFilter = ref<string | undefined>(undefined);
const endpointFilter = ref<string | undefined>(undefined);
const onlyError = ref(false);
const minDuration = ref(0);
const sortBy = ref<'duration' | 'time'>('duration');
const page = ref(1);
const pageSize = ref(20);
const drawerOpen = ref(false);
const selectedTrace = ref<TraceListItem | null>(null);

const AVAILABILITY_META = {
  ok: { text: '已连接 SkyWalking', color: 'success' as const },
  unconfigured: { text: '未配置', color: 'default' as const },
  down: { text: 'OAP 不可达', color: 'warning' as const },
};
const availabilityText = computed(() => AVAILABILITY_META[availability.value].text);
const availabilityColor = computed(() => AVAILABILITY_META[availability.value].color);

const windowText = computed(() =>
  `时间窗 ${formatDate(props.windowStart ?? '')} – ${formatDate(props.windowEnd ?? '')}（执行区间）`);

const serviceNames = computed(() => [...new Set(MOCK_TRACES.map(t => t.service))].sort());
const endpointOptions = computed(() =>
  [...new Set(MOCK_TRACES.map(t => t.entry))].sort()
    .map(name => ({ label: name, value: name })));

// 跨服务聚合：按服务累计 span 自身耗时/错误/参与链路数（不受「服务」筛选影响，点色块才过滤）。
// 语义：服务筛选 = 该服务参与的链路（对齐 SkyWalking queryBasicTraces(service=)），非仅入口服务。
const breakdownBase = computed(() => MOCK_TRACES.filter(t =>
  (!endpointFilter.value || t.entry === endpointFilter.value)
  && (!onlyError.value || t.error)
  && t.durationMs >= minDuration.value));

const serviceBreakdown = computed(() => {
  const stats = new Map<string, { totalMs: number; errorSpans: number; traceCount: number }>();
  for (const trace of breakdownBase.value) {
    for (const item of trace.spans) {
      const entry = stats.get(item.service) ?? { totalMs: 0, errorSpans: 0, traceCount: 0 };
      entry.totalMs += item.durationMs;
      if (item.error) entry.errorSpans += 1;
      stats.set(item.service, entry);
    }
    for (const service of new Set(trace.spans.map(s => s.service))) {
      stats.get(service)!.traceCount += 1;
    }
  }
  const grand = [...stats.values()].reduce((sum, v) => sum + v.totalMs, 0) || 1;
  return [...stats.entries()]
    .map(([service, v]) => ({ service, ...v, sharePct: (v.totalMs / grand) * 100 }))
    .sort((a, b) => b.totalMs - a.totalMs);
});

function toggleServiceFilter(service: string) {
  serviceFilter.value = serviceFilter.value === service ? undefined : service;
}

const ts = (time: string) => {
  const [h, m, s] = time.split(':').map(Number);
  return h * 3600 + m * 60 + s;
};

const filteredTraces = computed(() => {
  const list = MOCK_TRACES.filter(t =>
    (!serviceFilter.value || t.service === serviceFilter.value)
    && (!endpointFilter.value || t.entry === endpointFilter.value)
    && (!onlyError.value || t.error)
    && t.durationMs >= minDuration.value);
  return list.sort((a, b) =>
    sortBy.value === 'duration' ? b.durationMs - a.durationMs : ts(b.time) - ts(a.time));
});

const totalPages = computed(() => Math.max(1, Math.ceil(filteredTraces.value.length / pageSize.value)));
const pagedTraces = computed(() => {
  const start = (page.value - 1) * pageSize.value;
  return filteredTraces.value.slice(start, start + pageSize.value);
});
const countText = computed(() =>
  `共 ${MOCK_TRACES.length} 条 · 筛选后 ${filteredTraces.value.length} 条 · 第 ${page.value}/${totalPages.value} 页`);

watch([serviceFilter, endpointFilter, onlyError, minDuration, sortBy], () => { page.value = 1; });
watch(totalPages, value => { if (page.value > value) page.value = value; });

const columns: TableColumnsType<TraceListItem> = [
  { title: '时间', key: 'time', width: 84 },
  { title: '服务', key: 'service', width: 150 },
  { title: '入口接口', key: 'entry', ellipsis: true },
  { title: '耗时', key: 'durationMs', width: 90, align: 'right' },
  { title: '状态', key: 'status', width: 72 },
  { title: 'Span', key: 'spanCount', width: 60, align: 'right' },
  { title: 'TraceId', key: 'traceId', width: 158 },
];

const rowEvents: TableProps<TraceListItem>['customRow'] = record => ({
  onClick: () => { openTrace(record.traceId); },
});

function openTrace(traceId: string) {
  const target = MOCK_TRACES.find(t => t.traceId === traceId);
  if (!target) return;
  selectedTrace.value = target;
  drawerOpen.value = true;
}

function onPageChange(next: number, size: number) {
  page.value = next;
  pageSize.value = size;
}

function paginationShowTotal(total: number) {
  return `共 ${total} 条`;
}

function serviceColor(service: string): string {
  return TRACE_SERVICE_COLORS[service] ?? '#8a97a5';
}

function formatDuration(ms: number): string {
  return ms >= 1000 ? `${(ms / 1000).toFixed(2)}s` : `${ms}ms`;
}

function durClass(ms: number): string {
  if (ms >= 1000) return 'is-red';
  if (ms >= 500) return 'is-orange';
  return '';
}

function copyTraceId(traceId: string) {
  if (navigator.clipboard && window.isSecureContext) {
    navigator.clipboard.writeText(traceId).catch(() => {});
  }
  message.success('TraceId 已复制');
}

defineExpose({ openTrace });
</script>

<style scoped>
.trace-head-tag { margin-left: 8px; font-weight: 600; }
.trace-window { font-size: 12px; color: #5c6b7a; }

.trace-toolbar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
  padding: 10px 18px; background: #f7fafb; border-bottom: 1px solid #e2e8ee; }
.trace-filter-label { font-size: 12px; color: #5c6b7a; flex: none; }
.trace-filter-service { width: 150px; flex: none; }
.trace-filter-endpoint { width: 240px; flex: none; }
.trace-filter-dur { width: 96px; flex: none; }
.trace-filter-sort { width: 104px; flex: none; }
.trace-count { margin-left: auto; font-size: 12px; color: #5c6b7a; }

.trace-table :deep(tbody tr) { cursor: pointer; }
.trace-mono { font-family: var(--font-data, ui-monospace, Menlo, Consolas, monospace);
  font-size: 11.5px; }
.trace-svc { display: inline-flex; align-items: center; gap: 6px; font-size: 12px;
  white-space: nowrap; }
.trace-swatch { width: 8px; height: 8px; border-radius: 2px; flex: none; }
.trace-dur { font-family: var(--font-data, ui-monospace, Menlo, Consolas, monospace);
  font-weight: 600; }
.trace-dur.is-orange { color: #b97e0c; }
.trace-dur.is-red { color: #d14343; }
.trace-span-count { color: #5c6b7a; }
.trace-tid { color: #5c6b7a; }
.trace-copy-btn { padding: 0 4px; color: #5c6b7a; }
.trace-copy-btn:hover { color: var(--accent, #0b7f8a); }

.trace-pagination { padding: 10px 18px; }

.trace-state { padding: 34px 20px; text-align: center; }
.trace-state-title { font-size: 14px; font-weight: 600; margin-bottom: 6px; }
.trace-state p { font-size: 13px; color: #5c6b7a; line-height: 1.7; }
.trace-state code { font-family: var(--font-data, ui-monospace, Menlo, Consolas, monospace);
  font-size: 11.5px; background: #eef2f5; padding: 2px 6px; border-radius: 4px; }

.trace-breakdown { display: flex; align-items: center; gap: 10px; flex-wrap: wrap;
  padding: 10px 18px 4px; }
.trace-bd-label { font-size: 12px; color: #5c6b7a; flex: none; }
.trace-bd-bar { flex: 1; min-width: 260px; display: flex; height: 16px; gap: 1px;
  border-radius: 4px; overflow: hidden; }
.trace-bd-seg { border: none; min-width: 6px; height: 100%; cursor: pointer;
  opacity: 0.85; padding: 0; transition: opacity 0.15s; }
.trace-bd-seg:hover { opacity: 1; }
.trace-bd-seg.is-active { opacity: 1; box-shadow: inset 0 0 0 2px rgba(255, 255, 255, 0.85); }
.trace-bd-hint { font-size: 11px; color: #9aa6b1; flex: none; }
</style>
