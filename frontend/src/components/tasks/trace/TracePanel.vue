<template>
  <div class="panel trace-panel">
    <div class="panel-header">
      <div>
        <h2>
          链路追踪
          <a-tag v-if="availabilityMeta.text" class="trace-head-tag" :color="availabilityMeta.color">{{ availabilityMeta.text }}</a-tag>
          <a-tag class="trace-head-tag" :color="profileMeta.color">{{ profileMeta.text }}</a-tag>
        </h2>
      </div>
      <span class="trace-window">{{ windowText }}</span>
    </div>

    <div class="trace-toolbar">
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
      <a-button size="small" :loading="loading" @click="fetchTraces">刷新</a-button>
      <span class="trace-count">{{ countText }}</span>
    </div>

    <template v-if="panelState === 'ok'">
      <div class="trace-breakdown">
        <span class="trace-bd-label">服务耗时分布</span>
        <div class="trace-bd-bar">
          <a-tooltip v-for="item in serviceBreakdown" :key="item.service">
            <template #title>
              <div><strong>{{ item.service }}</strong></div>
              <div>耗时占比 {{ item.sharePct.toFixed(1) }}%（{{ formatDuration(item.totalMs) }}）</div>
              <div>trace {{ traces.length }} 条中错误 {{ item.errorTraces }} 条</div>
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
        <span class="trace-bd-hint">点击色块按入口服务筛选</span>
      </div>
      <a-table
        class="workspace-table trace-table"
        :columns="columns"
        :data-source="traces"
        :pagination="false"
        :row-key="(record: TraceListItem) => record.traceId"
        :custom-row="rowEvents"
        :loading="loading"
        size="small"
        :locale="{ emptyText: '当前筛选无匹配 trace' }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'time'">
            <span class="trace-mono">{{ formatTime(record.time) }}</span>
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
            <span class="trace-mono trace-span-count">{{ record.spanCount }}</span>
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
        :total="total"
        :page-size="pageSize"
        :page-size-options="['10', '20', '50']"
        show-size-changer
        show-less-items
        size="small"
        :show-total="paginationShowTotal"
        @change="onPageChange"
      />
    </template>

    <div v-else-if="panelState === 'loading'" class="trace-state">
      <a-spin size="small" />
      <p>链路数据加载中…</p>
    </div>

    <div v-else-if="panelState === 'unconfigured'" class="trace-state">
      <p class="trace-state-title">未配置链路数据源</p>
      <p>在后端配置中开启 trace 深度源后，可在此按执行时间窗下钻慢/错请求链路：</p>
      <code>platform.evidence.deep.kinds.trace.enabled=true</code>
    </div>

    <div v-else class="trace-state">
      <p class="trace-state-title">链路数据源不可达（SOURCE_UNAVAILABLE）</p>
      <p>SkyWalking OAP 连接失败，请检查 OAP 服务与网络连通性。已结束的执行仍可查看终态固化的 trace 摘要列表。</p>
    </div>

    <TraceDetailDrawer v-model:open="drawerOpen" :detail="selectedDetail" :loading="detailLoading" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import type { TableColumnsType, TableProps } from 'ant-design-vue';
import { message } from 'ant-design-vue';
import { CopyOutlined } from '@ant-design/icons-vue';
import type { ExecutionTraceDetail, ExecutionTracesPage, ObservabilityProfile, TraceListItem } from '../../../types';
import { formatDate } from '../../../utils/format';
import { aggregateServiceShare, panelStateOf, traceServiceColor } from '../../../utils/traces-view';
import { getExecutionTraceDetailApi, getExecutionTracesApi } from '../../../api/traces';
import TraceDetailDrawer from './TraceDetailDrawer.vue';

const props = defineProps<{
  executionId: number;
  windowStart: string | null;
  windowEnd: string | null;
  observabilityProfile?: ObservabilityProfile | null;
}>();

const serviceFilter = ref<string | undefined>(undefined);
const endpointFilter = ref<string | undefined>(undefined);
const onlyError = ref(false);
const minDuration = ref(0);
const sortBy = ref<'duration' | 'time'>('duration');
const page = ref(1);
const pageSize = ref(20);
const loading = ref(false);
const response = ref<ExecutionTracesPage | null>(null);
const drawerOpen = ref(false);
const detailLoading = ref(false);
const selectedDetail = ref<ExecutionTraceDetail | null>(null);

const panelState = computed(() => (response.value ? panelStateOf(response.value) : 'loading'));
const traces = computed(() => response.value?.traces ?? []);
const total = computed(() => response.value?.total ?? 0);
// 可用性 tag 仅在三态就绪后展示（加载中不误导）。
const AVAILABILITY_META = {
  ok: { text: '已连接 SkyWalking', color: 'success' as const },
  unconfigured: { text: '未配置', color: 'default' as const },
  down: { text: 'OAP 不可达', color: 'warning' as const },
};
const availabilityMeta = computed(() =>
  AVAILABILITY_META[panelState.value as keyof typeof AVAILABILITY_META] ?? { text: '', color: 'default' as const });

// 轮次徽标：诊断轮全量采样；容量轮/OFF 低采样（spec trace-integration §5）。
const profileMeta = computed(() => props.observabilityProfile === 'DIAGNOSTIC'
  ? { text: '诊断轮 · 全量采样', color: 'processing' as const }
  : { text: '容量轮 · 低采样', color: 'default' as const });

const windowText = computed(() =>
  `时间窗 ${formatDate(props.windowStart ?? '')} – ${formatDate(props.windowEnd ?? '')}（执行区间）`);

// 筛选词表来自当前页数据（Phase 1 无独立 vocab 端点）。
const serviceNames = computed(() => [...new Set(traces.value.map(t => t.service))].sort());
const endpointOptions = computed(() =>
  [...new Set(traces.value.map(t => t.entry))].sort()
    .map(name => ({ label: name, value: name })));

// 服务耗时分布：入口服务维度聚合当前页（span 维度为 Phase 2 升级）。
const serviceBreakdown = computed(() => aggregateServiceShare(traces.value));

function toggleServiceFilter(service: string) {
  serviceFilter.value = serviceFilter.value === service ? undefined : service;
}

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)));
const countText = computed(() => `共 ${total.value} 条 · 第 ${page.value}/${totalPages.value} 页`);

let fetchSeq = 0;

async function fetchTraces() {
  const seq = ++fetchSeq;
  loading.value = true;
  try {
    const data = await getExecutionTracesApi(props.executionId, {
      service: serviceFilter.value,
      endpoint: endpointFilter.value,
      onlyError: onlyError.value || undefined,
      minDurationMs: minDuration.value || undefined,
      sort: sortBy.value,
      page: page.value,
      size: pageSize.value,
    });
    if (seq !== fetchSeq) return; // 丢弃过期响应（连续筛选切换）
    response.value = data;
  } catch (error) {
    if (seq !== fetchSeq) return;
    message.error(error instanceof Error ? error.message : '链路数据加载失败');
  } finally {
    if (seq === fetchSeq) loading.value = false;
  }
}

onMounted(fetchTraces);
watch([serviceFilter, endpointFilter, onlyError, minDuration, sortBy], () => {
  page.value = 1;
  void fetchTraces();
});

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

/** 面板负责详情拉取（持 loading 态），抽屉只渲染结果——retention-expired 等不可用态在抽屉内呈现。 */
async function openTrace(traceId: string) {
  drawerOpen.value = true;
  detailLoading.value = true;
  selectedDetail.value = null;
  try {
    selectedDetail.value = await getExecutionTraceDetailApi(props.executionId, traceId);
  } catch (error) {
    drawerOpen.value = false;
    message.error(error instanceof Error ? error.message : '链路详情加载失败');
  } finally {
    detailLoading.value = false;
  }
}

function onPageChange(next: number, size: number) {
  page.value = next;
  pageSize.value = size;
  void fetchTraces();
}

function paginationShowTotal(total: number) {
  return `共 ${total} 条`;
}

function serviceColor(service: string): string {
  return traceServiceColor(service);
}

function formatTime(epochMs: number): string {
  const d = new Date(epochMs);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
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
