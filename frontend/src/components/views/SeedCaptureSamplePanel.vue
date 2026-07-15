<template>
  <div>
    <template v-if="strategy">
      <div class="sample-toolbar">
        <a-space wrap>
          <a-select
            v-model:value="filters.statuses"
            mode="multiple"
            style="min-width: 240px"
            placeholder="按状态筛选"
            :options="statusOptions"
            max-tag-count="responsive"
          />
          <a-input v-model:value="filters.from" type="datetime-local" placeholder="开始时间" />
          <a-input v-model:value="filters.to" type="datetime-local" placeholder="结束时间" />
          <a-button type="primary" @click="applyFilters">筛选</a-button>
          <a-button @click="resetFilters">重置</a-button>
        </a-space>
      </div>

      <p class="detail-description">
        当前策略：{{ strategy.name }} · v{{ strategy.configVersion }}。采集进度来自持久化样本记录，刷新页面后仍可继续查看。
      </p>

      <a-table
        :columns="columns"
        :data-source="samples"
        :loading="loading"
        :pagination="pagination"
        row-key="id"
        @change="onPageChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'sample'">
            <strong>S{{ record.sampleSeq }}</strong>
            <div class="secondary-text">#{{ record.id }} · v{{ record.configVersion }}</div>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="record.incomplete ? 'orange' : statusColor(record.status)">
              {{ record.incomplete ? '不完整' : statusLabel(record.status) }}
            </a-tag>
            <div class="secondary-text">{{ record.phase }}</div>
          </template>
          <template v-else-if="column.key === 'progress'">
            <a-progress :percent="progressPercent(record)" size="small" :status="progressStatus(record)" />
            <div class="secondary-text">
              {{ record.completedTables }} / {{ record.totalTables || '?' }} 张表 ·
              {{ record.capturedRows.toLocaleString() }} 行
            </div>
          </template>
          <template v-else-if="column.key === 'time'">
            {{ formatDate(record.captureStartedAt) }}
            <div v-if="record.captureFinishedAt" class="secondary-text">
              至 {{ formatDate(record.captureFinishedAt) }}
            </div>
          </template>
          <template v-else-if="column.key === 'storage'">
            {{ formatBytes(record.writtenBytes) }}
            <div class="secondary-text">{{ record.activeWorkers }} 个 Worker</div>
          </template>
          <template v-else-if="column.key === 'error'">
            <span v-if="record.errorMessage" class="error-text">{{ record.errorMessage }}</span>
            <span v-else-if="retryGuidance(record)" class="retry-text">可删除后重新执行策略</span>
            <span v-else>—</span>
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-space>
              <a-button size="small" @click="openDetail(record)">详情</a-button>
              <a-button
                v-if="isActive(record)"
                size="small"
                :loading="cancelingId === record.id"
                @click="cancel(record)"
              >
                取消
              </a-button>
              <a-button
                v-if="canDelete(record)"
                size="small"
                danger
                :loading="deletingId === record.id"
                @click="remove(record)"
              >
                删除
              </a-button>
            </a-space>
          </template>
        </template>
      </a-table>

      <a-empty v-if="!loading && !samples.length" description="暂无符合条件的样本" />
    </template>
    <a-empty v-else description="请先创建或选择录制策略" />

    <SeedCaptureSampleDetail
      :open="detailOpen"
      :sample-id="detailSampleId"
      @close="detailOpen = false"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import type { TableColumnsType } from 'ant-design-vue';
import { Modal, message } from 'ant-design-vue';
import {
  cancelSeedCaptureSample,
  deleteSeedCaptureSample,
  getSeedCaptureSample,
  listSeedCaptureSamples,
  type SeedCaptureSample,
  type SeedCaptureStrategy,
} from '../../api/seed';
import SeedCaptureSampleDetail from './SeedCaptureSampleDetail.vue';

const props = defineProps<{
  strategy: SeedCaptureStrategy | null;
  reloadKey: number;
}>();

const route = useRoute();
const projectId = computed(() => Number(route.params.projectId) || 0);
const loading = ref(false);
const samples = ref<SeedCaptureSample[]>([]);
const currentPage = ref(0);
const pageSize = ref(20);
const total = ref(0);
const detailOpen = ref(false);
const detailSampleId = ref<number | null>(null);
const cancelingId = ref<number | null>(null);
const deletingId = ref<number | null>(null);
const filters = reactive({
  statuses: [] as string[],
  from: '',
  to: '',
});
let pollTimer: number | undefined;

const statusOptions = [
  { value: 'QUEUED', label: '排队中' },
  { value: 'PREPARING', label: '准备中' },
  { value: 'CAPTURING', label: '采集中' },
  { value: 'CANCEL_REQUESTED', label: '取消中' },
  { value: 'SUCCEEDED', label: '成功' },
  { value: 'FAILED', label: '失败' },
  { value: 'CANCELED', label: '已取消' },
  { value: 'INTERRUPTED', label: '已中断' },
];
const columns: TableColumnsType<SeedCaptureSample> = [
  { title: '样本', key: 'sample', width: 110 },
  { title: '状态', key: 'status', width: 110 },
  { title: '表级进度', key: 'progress', width: 230 },
  { title: '采集时间', key: 'time', width: 190 },
  { title: '存储', key: 'storage', width: 130 },
  { title: '异常与重试', key: 'error', width: 210 },
  { title: '操作', key: 'actions', width: 180 },
];
const pagination = computed(() => ({
  current: currentPage.value + 1,
  pageSize: pageSize.value,
  total: total.value,
  showSizeChanger: true,
  pageSizeOptions: ['10', '20', '50', '100'],
  showTotal: (value: number) => `共 ${value} 个样本`,
}));

const activeStatuses = new Set(['QUEUED', 'PREPARING', 'CAPTURING', 'CANCEL_REQUESTED']);
const terminalStatuses = new Set(['SUCCEEDED', 'FAILED', 'CANCELED', 'INTERRUPTED', 'DELETING']);

watch(
  [() => props.strategy?.id, () => props.reloadKey, projectId],
  () => void loadSamples(true),
  { immediate: true },
);

onMounted(() => {
  pollTimer = window.setInterval(() => void refreshProgress(), 3000);
});

onBeforeUnmount(() => {
  if (pollTimer !== undefined) window.clearInterval(pollTimer);
});

async function loadSamples(resetPage = false) {
  if (resetPage) currentPage.value = 0;
  if (!props.strategy || !projectId.value) {
    samples.value = [];
    total.value = 0;
    return;
  }
  loading.value = true;
  try {
    const result = await listSeedCaptureSamples(projectId.value, props.strategy.id, {
      status: filters.statuses,
      from: toInstant(filters.from),
      to: toInstant(filters.to),
      page: currentPage.value,
      size: pageSize.value,
    });
    samples.value = result.content;
    total.value = result.totalElements;
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载样本历史失败');
  } finally {
    loading.value = false;
  }
}

async function refreshProgress() {
  if (!props.strategy || !projectId.value) return;
  const activeSamples = samples.value.filter(isActive);
  if (!activeSamples.length) return;
  const updates = await Promise.allSettled(
    activeSamples.map((sample) => getSeedCaptureSample(projectId.value, sample.id)),
  );
  updates.forEach((result) => {
    if (result.status !== 'fulfilled') return;
    const index = samples.value.findIndex((sample) => sample.id === result.value.id);
    if (index >= 0) samples.value[index] = result.value;
  });
}

function applyFilters() {
  void loadSamples(true);
}

function resetFilters() {
  Object.assign(filters, { statuses: [], from: '', to: '' });
  void loadSamples(true);
}

function onPageChange(page: number, size: number) {
  currentPage.value = Math.max(page - 1, 0);
  pageSize.value = size;
  void loadSamples();
}

function openDetail(sample: SeedCaptureSample) {
  detailSampleId.value = sample.id;
  detailOpen.value = true;
}

function cancel(sample: SeedCaptureSample) {
  const id = projectId.value;
  if (!id) return;
  Modal.confirm({
    title: `取消样本 S${sample.sampleSeq}？`,
    content: '采集将在当前批次完成后停止，已提交分片会保留并标记为不完整。',
    okText: '确认取消',
    cancelText: '继续采集',
    async onOk() {
      cancelingId.value = sample.id;
      try {
        await cancelSeedCaptureSample(id, sample.id);
        message.success('已发出取消请求');
        await loadSamples();
      } catch (e) {
        message.error(e instanceof Error ? e.message : '取消样本失败');
        return Promise.reject();
      } finally {
        cancelingId.value = null;
      }
    },
  });
}

function remove(sample: SeedCaptureSample) {
  const id = projectId.value;
  if (!id) return;
  Modal.confirm({
    title: `删除样本 S${sample.sampleSeq}？`,
    content: '删除会清理样本分片和元数据；分析运行期间被引用的样本无法删除。',
    okType: 'danger',
    okText: '删除',
    cancelText: '取消',
    async onOk() {
      deletingId.value = sample.id;
      try {
        await deleteSeedCaptureSample(id, sample.id);
        message.success('样本删除请求已提交');
        await loadSamples();
      } catch (e) {
        message.error(e instanceof Error ? e.message : '删除样本失败');
        return Promise.reject();
      } finally {
        deletingId.value = null;
      }
    },
  });
}

function toInstant(value: string) {
  if (!value) return undefined;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString();
}

function isActive(sample: SeedCaptureSample) {
  return activeStatuses.has(sample.status);
}

function canDelete(sample: SeedCaptureSample) {
  return terminalStatuses.has(sample.status) && !isActive(sample);
}

function retryGuidance(sample: SeedCaptureSample) {
  return sample.incomplete || ['FAILED', 'CANCELED', 'INTERRUPTED'].includes(sample.status);
}

function progressPercent(sample: SeedCaptureSample) {
  if (sample.status === 'SUCCEEDED') return 100;
  if (!sample.totalTables) return 0;
  return Math.min(100, Math.floor((sample.completedTables / sample.totalTables) * 100));
}

function progressStatus(sample: SeedCaptureSample) {
  if (sample.status === 'FAILED' || sample.status === 'INTERRUPTED') return 'exception';
  if (sample.status === 'CANCELED') return 'normal';
  return 'active';
}

function statusLabel(status: string) {
  return ({
    QUEUED: '排队中',
    PREPARING: '准备中',
    CAPTURING: '采集中',
    CANCEL_REQUESTED: '取消中',
    SUCCEEDED: '成功',
    FAILED: '失败',
    CANCELED: '已取消',
    INTERRUPTED: '已中断',
    DELETING: '删除中',
  } as Record<string, string>)[status] || status;
}

function statusColor(status: string) {
  if (status === 'SUCCEEDED') return 'green';
  if (status === 'FAILED' || status === 'INTERRUPTED') return 'red';
  if (status === 'CANCELED') return 'orange';
  if (status === 'CAPTURING' || status === 'PREPARING') return 'blue';
  return 'default';
}

function formatDate(value: string | null | undefined) {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN');
}

function formatBytes(value: number | null | undefined) {
  if (!value) return '0 B';
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  return `${(value / 1024 / 1024).toFixed(1)} MB`;
}
</script>

<style scoped>
.sample-toolbar { margin-bottom: 12px; }
.secondary-text { color: var(--muted); font-size: 12px; line-height: 1.6; }
.error-text { color: #cf1322; overflow-wrap: anywhere; }
.retry-text { color: #d46b08; }
</style>
