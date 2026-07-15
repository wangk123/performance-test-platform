<template>
  <div>
    <template v-if="strategy">
      <div class="analysis-toolbar">
        <a-space wrap>
          <a-select
            v-model:value="statusFilter"
            style="width: 150px"
            :options="statusFilterOptions"
            @change="loadAnalyses"
          />
          <a-select
            v-model:value="metricFilter"
            style="width: 180px"
            :options="metricFilterOptions"
            @change="loadAnalyses"
          />
          <a-button type="primary" @click="openCreate">新建多样本分析</a-button>
        </a-space>
      </div>
      <p class="detail-description">
        当前策略：{{ strategy.name }}。分析只接受该策略的终态样本，并按采集时间从早到晚生成相邻区间。
      </p>
      <a-table
        :columns="analysisColumns"
        :data-source="visibleAnalyses"
        :loading="loading"
        :pagination="false"
        row-key="id"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'analysis'">
            <strong>#{{ record.id }}</strong>
            <div class="secondary-text">{{ record.inputSampleIds.length }} 份样本</div>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag>
            <div class="secondary-text">{{ record.phase }}</div>
          </template>
          <template v-else-if="column.key === 'progress'">
            <a-progress :percent="progressPercent(record)" size="small" :status="progressStatus(record)" />
            <div class="secondary-text">
              {{ record.completedTables }} / {{ record.totalTables || '?' }} 张表 ·
              {{ record.currentTables.join('、') || '—' }}
            </div>
          </template>
          <template v-else-if="column.key === 'metrics'">
            比较 {{ record.comparedRows.toLocaleString() }} 行
            <div class="secondary-text">
              跳过 {{ record.skippedTables }} 表 · {{ record.fineScreenedChunks }} 分片 ·
              {{ record.candidateOperationCount }} 个候选
            </div>
          </template>
          <template v-else-if="column.key === 'createdAt'">
            {{ formatDate(record.createdAt) }}
            <div v-if="record.finishedAt" class="secondary-text">完成于 {{ formatDate(record.finishedAt) }}</div>
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
                v-else
                size="small"
                danger
                :loading="deletingId === record.id"
                @click="remove(record)"
              >
                删除
              </a-button>
              <a-button
                v-if="record.templateId"
                size="small"
                type="link"
                @click="openTemplate(record.templateId)"
              >
                查看模板
              </a-button>
            </a-space>
          </template>
        </template>
      </a-table>
      <a-empty v-if="!loading && !visibleAnalyses.length" description="暂无分析记录" />
    </template>
    <a-empty v-else description="请先创建或选择录制策略" />
    <a-modal
      v-model:open="createModalOpen"
      title="新建多样本分析"
      width="980px"
      :confirm-loading="creating"
      destroy-on-close
    >
      <a-spin :spinning="sampleLoading">
        <a-alert
          v-if="selectedSampleIds.length < 3"
          type="info"
          show-icon
          message="至少选择 3 份终态样本，系统会生成至少 2 个相邻 Diff 区间。"
        />
        <a-table
          class="sample-select-table"
          :columns="sampleColumns"
          :data-source="availableSamples"
          :row-selection="sampleRowSelection"
          :pagination="false"
          row-key="id"
          size="small"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'sample'">
              <strong>S{{ record.sampleSeq }}</strong>
              <div class="secondary-text">#{{ record.id }}</div>
            </template>
            <template v-else-if="column.key === 'status'">
              <a-tag :color="record.incomplete ? 'orange' : statusColor(record.status)">
                {{ record.incomplete ? '不完整' : statusLabel(record.status) }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'captureStartedAt'">
              {{ formatDate(record.captureStartedAt) }}
            </template>
          </template>
        </a-table>
        <div v-if="orderedSamples.length" class="preview-block">
          <div class="preview-title">时间顺序预览</div>
          <a-timeline>
            <a-timeline-item v-for="sample in orderedSamples" :key="sample.id">
              S{{ sample.sampleSeq }} · #{{ sample.id }} · {{ formatDate(sample.captureStartedAt) }} · v{{ sample.configVersion }}
            </a-timeline-item>
          </a-timeline>
          <div class="preview-title">相邻区间</div>
          <a-space wrap>
            <a-tag v-for="(sample, index) in orderedSamples.slice(0, -1)" :key="`${sample.id}-${orderedSamples[index + 1].id}`">
              S{{ sample.sampleSeq }} → S{{ orderedSamples[index + 1].sampleSeq }}
            </a-tag>
          </a-space>
        </div>
        <a-alert
          v-if="sequenceGap || versionMismatch"
          class="analysis-warning"
          type="warning"
          show-icon
          message="选择的样本存在分析风险"
          :description="warningDescription"
        />
        <a-alert
          v-if="incompleteSamples.length"
          class="analysis-warning"
          type="warning"
          show-icon
          message="选择了不完整样本"
          :description="`样本 ${incompleteSamples.map((sample) => `#${sample.id}`).join('、')} 的缺失数据会传播为 UNKNOWN。`"
        />
        <a-checkbox v-if="sequenceGap || versionMismatch" v-model:checked="confirmRisk">
          我确认序号缺口或策略版本差异可能包含未选择的变化，仍继续分析
        </a-checkbox>
        <a-checkbox v-if="incompleteSamples.length" v-model:checked="confirmIncomplete">
          我确认不完整样本的覆盖风险，允许继续分析
        </a-checkbox>
      </a-spin>
      <template #footer>
        <a-button @click="createModalOpen = false">取消</a-button>
        <a-button type="primary" :loading="creating" :disabled="!canCreate" @click="submitAnalysis">
          开始分析
        </a-button>
      </template>
    </a-modal>

    <SeedCaptureAnalysisDetail
      :open="detailOpen"
      :analysis-id="detailAnalysisId"
      @close="detailOpen = false"
    />
  </div>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import type { TableColumnsType } from 'ant-design-vue';
import { Modal, message } from 'ant-design-vue';
import {
  cancelSeedCaptureAnalysis,
  createSeedCaptureAnalysis,
  deleteSeedCaptureAnalysis,
  getSeedCaptureAnalysis,
  listSeedCaptureAnalyses,
  listSeedCaptureSamples,
  type SeedCaptureAnalysis,
  type SeedCaptureSample,
  type SeedCaptureStrategy,
} from '../../api/seed';
import SeedCaptureAnalysisDetail from './SeedCaptureAnalysisDetail.vue';
const props = defineProps<{
  strategy: SeedCaptureStrategy | null;
  reloadKey: number;
}>();
const emit = defineEmits<{ openTemplate: [templateId: number] }>();
const route = useRoute();
const projectId = computed(() => Number(route.params.projectId) || 0);
const loading = ref(false);
const sampleLoading = ref(false);
const creating = ref(false);
const analyses = ref<SeedCaptureAnalysis[]>([]);
const availableSamples = ref<SeedCaptureSample[]>([]);
const selectedSampleIds = ref<number[]>([]);
const createModalOpen = ref(false);
const detailOpen = ref(false);
const detailAnalysisId = ref<number | null>(null);
const cancelingId = ref<number | null>(null);
const deletingId = ref<number | null>(null);
const statusFilter = ref('ALL');
const metricFilter = ref('ALL');
const confirmRisk = ref(false);
const confirmIncomplete = ref(false);
let pollTimer: number | undefined;
const statusFilterOptions = [
  { value: 'ALL', label: '全部状态' },
  { value: 'QUEUED', label: '排队中' },
  { value: 'VALIDATING', label: '校验中' },
  { value: 'DIFFING', label: 'Diff 中' },
  { value: 'INFERRING', label: '推断中' },
  { value: 'PERSISTING', label: '保存中' },
  { value: 'SUCCEEDED', label: '成功' },
  { value: 'FAILED', label: '失败' },
  { value: 'CANCELED', label: '已取消' },
  { value: 'INTERRUPTED', label: '已中断' },
];
const metricFilterOptions = [
  { value: 'ALL', label: '全部指标' },
  { value: 'RISK', label: '含风险' },
  { value: 'UNKNOWN', label: '含 UNKNOWN' },
  { value: 'OPERATIONS', label: '含候选操作' },
];
const analysisColumns: TableColumnsType<SeedCaptureAnalysis> = [
  { title: '分析', key: 'analysis', width: 110 },
  { title: '状态', key: 'status', width: 110 },
  { title: '进度', key: 'progress', width: 230 },
  { title: '筛选指标', key: 'metrics', width: 280 },
  { title: '时间', key: 'createdAt', width: 190 },
  { title: '操作', key: 'actions', width: 250 },
];
const sampleColumns: TableColumnsType<SeedCaptureSample> = [
  { title: '样本', key: 'sample', width: 100 },
  { title: '采集开始', key: 'captureStartedAt', width: 190 },
  { title: '状态', key: 'status', width: 110 },
  { title: '版本', dataIndex: 'configVersion', width: 70 },
  { title: '行数', dataIndex: 'capturedRows', width: 100 },
];
const activeStatuses = new Set(['QUEUED', 'VALIDATING', 'DIFFING', 'INFERRING', 'PERSISTING', 'CANCEL_REQUESTED']);
const visibleAnalyses = computed(() =>
  analyses.value
    .filter((analysis) => analysis.strategyId === props.strategy?.id)
    .filter((analysis) => statusFilter.value === 'ALL' || analysis.status === statusFilter.value)
    .filter((analysis) => matchesMetric(analysis))
    .sort((left, right) => right.createdAt.localeCompare(left.createdAt)),
);
const selectedSamples = computed(() =>
  availableSamples.value.filter((sample) => selectedSampleIds.value.includes(sample.id)),
);
const orderedSamples = computed(() =>
  [...selectedSamples.value].sort((left, right) =>
    left.captureStartedAt.localeCompare(right.captureStartedAt)
    || left.sampleSeq - right.sampleSeq
    || left.id - right.id,
  ),
);
const sequenceGap = computed(() =>
  orderedSamples.value.some((sample, index) =>
    index > 0 && sample.sampleSeq !== orderedSamples.value[index - 1].sampleSeq + 1,
  ),
);
const versionMismatch = computed(() =>
  new Set(orderedSamples.value.map((sample) => sample.configVersion)).size > 1,
);
const incompleteSamples = computed(() =>
  orderedSamples.value.filter((sample) => sample.incomplete || sample.status !== 'SUCCEEDED'),
);
const warningDescription = computed(() => [
  sequenceGap.value ? 'sample_seq 不连续，某个相邻区间可能包含未选择的累计变化。' : '',
  versionMismatch.value ? '样本使用了不同策略版本，请确认配置差异不会造成误判。' : '',
].filter(Boolean).join(' '));
const canCreate = computed(() =>
  selectedSampleIds.value.length >= 3
  && (!sequenceGap.value && !versionMismatch.value || confirmRisk.value)
  && (!incompleteSamples.value.length || confirmIncomplete.value),
);
const sampleRowSelection = computed(() => ({
  selectedRowKeys: selectedSampleIds.value,
  onChange: (keys: Array<string | number>) => {
    selectedSampleIds.value = keys.map((key) => Number(key));
  },
}));
watch(
  [() => props.strategy?.id, () => props.reloadKey, projectId],
  () => void loadAnalyses(),
  { immediate: true },
);
onMounted(() => {
  pollTimer = window.setInterval(() => void refreshProgress(), 3000);
});
onBeforeUnmount(() => {
  if (pollTimer !== undefined) window.clearInterval(pollTimer);
});
async function loadAnalyses() {
  if (!projectId.value || !props.strategy) {
    analyses.value = [];
    return;
  }
  loading.value = true;
  try {
    analyses.value = await listSeedCaptureAnalyses(projectId.value);
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载分析历史失败');
  } finally {
    loading.value = false;
  }
}
async function refreshProgress() {
  if (!projectId.value || !props.strategy) return;
  const activeAnalyses = analyses.value.filter(
    (analysis) => analysis.strategyId === props.strategy?.id && isActive(analysis),
  );
  const updates = await Promise.allSettled(
    activeAnalyses.map((analysis) => getSeedCaptureAnalysis(projectId.value, analysis.id)),
  );
  updates.forEach((result) => {
    if (result.status !== 'fulfilled') return;
    const index = analyses.value.findIndex((analysis) => analysis.id === result.value.id);
    if (index >= 0) analyses.value[index] = result.value;
  });
}
async function openCreate() {
  if (!props.strategy || !projectId.value) return;
  selectedSampleIds.value = [];
  confirmRisk.value = false;
  confirmIncomplete.value = false;
  createModalOpen.value = true;
  sampleLoading.value = true;
  try {
    const result = await listSeedCaptureSamples(projectId.value, props.strategy.id, {
      status: ['SUCCEEDED', 'FAILED', 'CANCELED', 'INTERRUPTED'],
      page: 0,
      size: 200,
    });
    availableSamples.value = result.content;
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载可分析样本失败');
  } finally {
    sampleLoading.value = false;
  }
}
async function submitAnalysis() {
  if (!props.strategy || !projectId.value || !canCreate.value) {
    message.warning('请选择至少 3 份样本并完成风险确认');
    return;
  }
  creating.value = true;
  try {
    const result = await createSeedCaptureAnalysis(projectId.value, {
      strategyId: props.strategy.id,
      sampleIds: orderedSamples.value.map((sample) => sample.id),
      confirmIncomplete: Boolean(incompleteSamples.value.length && confirmIncomplete.value),
    });
    createModalOpen.value = false;
    message.success(`分析 #${result.id} 已排队`);
    await loadAnalyses();
  } catch (e) {
    message.error(e instanceof Error ? e.message : '创建分析失败');
  } finally {
    creating.value = false;
  }
}
function openDetail(analysis: SeedCaptureAnalysis) {
  detailAnalysisId.value = analysis.id;
  detailOpen.value = true;
}
function cancel(analysis: SeedCaptureAnalysis) {
  if (!projectId.value) return;
  Modal.confirm({
    title: `取消分析 #${analysis.id}？`,
    content: '分析将在当前协作检查点停止，已保存的诊断结果会保留。',
    okText: '确认取消',
    cancelText: '继续分析',
    async onOk() {
      cancelingId.value = analysis.id;
      try {
        await cancelSeedCaptureAnalysis(projectId.value, analysis.id);
        message.success('已发出取消请求');
        await loadAnalyses();
      } catch (e) {
        message.error(e instanceof Error ? e.message : '取消分析失败');
        return Promise.reject();
      } finally {
        cancelingId.value = null;
      }
    },
  });
}
function remove(analysis: SeedCaptureAnalysis) {
  if (!projectId.value) return;
  Modal.confirm({
    title: `删除分析 #${analysis.id}？`,
    content: '删除会清理分析结果，不会删除输入样本或生成的模板。',
    okType: 'danger',
    okText: '删除',
    cancelText: '取消',
    async onOk() {
      deletingId.value = analysis.id;
      try {
        await deleteSeedCaptureAnalysis(projectId.value, analysis.id);
        message.success('分析删除请求已提交');
        await loadAnalyses();
      } catch (e) {
        message.error(e instanceof Error ? e.message : '删除分析失败');
        return Promise.reject();
      } finally {
        deletingId.value = null;
      }
    },
  });
}
function openTemplate(templateId: number) {
  emit('openTemplate', templateId);
}
function matchesMetric(analysis: SeedCaptureAnalysis) {
  if (metricFilter.value === 'ALL') return true;
  if (metricFilter.value === 'OPERATIONS') return analysis.candidateOperationCount > 0;
  if (metricFilter.value === 'RISK') return Boolean(analysis.summary.risks?.length);
  if (metricFilter.value === 'UNKNOWN') {
    return Boolean(analysis.summary.warnings?.some((warning) => JSON.stringify(warning).toUpperCase().includes('UNKNOWN')))
      || Boolean(analysis.summary.tables?.some((table) =>
        table.intervals.some((interval) => JSON.stringify(interval).toUpperCase().includes('UNKNOWN')),
      ));
  }
  return true;
}
function isActive(analysis: SeedCaptureAnalysis) {
  return activeStatuses.has(analysis.status);
}
function progressPercent(analysis: SeedCaptureAnalysis) {
  if (analysis.status === 'SUCCEEDED') return 100;
  if (!analysis.totalTables) return 0;
  return Math.min(100, Math.floor((analysis.completedTables / analysis.totalTables) * 100));
}
function progressStatus(analysis: SeedCaptureAnalysis) {
  if (analysis.status === 'FAILED' || analysis.status === 'INTERRUPTED') return 'exception';
  if (analysis.status === 'CANCELED') return 'normal';
  return 'active';
}
function statusLabel(status: string) {
  return ({
    QUEUED: '排队中',
    VALIDATING: '校验中',
    DIFFING: 'Diff 中',
    INFERRING: '推断中',
    PERSISTING: '保存中',
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
  if (['VALIDATING', 'DIFFING', 'INFERRING', 'PERSISTING'].includes(status)) return 'blue';
  return 'default';
}

function formatDate(value: string | null | undefined) {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN');
}
</script>

<style scoped>
.analysis-toolbar { margin-bottom: 12px; }
.secondary-text { color: var(--muted); font-size: 12px; line-height: 1.6; }
.sample-select-table { margin-top: 16px; }
.preview-block {
  border-top: 1px solid var(--border);
  margin-top: 16px;
  padding-top: 14px;
}
.preview-title { font-weight: 600; margin: 8px 0; }
.analysis-warning { margin-top: 14px; }
</style>
