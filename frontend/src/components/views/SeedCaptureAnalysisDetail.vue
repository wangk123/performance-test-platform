<template>
  <a-modal
    :open="open"
    title="分析详情"
    width="1160px"
    :footer="null"
    destroy-on-close
    @cancel="close"
  >
    <a-spin :spinning="loading">
      <template v-if="analysis">
        <a-descriptions bordered size="small" :column="4">
          <a-descriptions-item label="分析">#{{ analysis.id }}</a-descriptions-item>
          <a-descriptions-item label="状态">
            <a-tag :color="statusColor(analysis.status)">{{ statusLabel(analysis.status) }}</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="阶段">{{ analysis.phase }}</a-descriptions-item>
          <a-descriptions-item label="输入样本">{{ analysis.inputSampleIds.join(', ') || '—' }}</a-descriptions-item>
          <a-descriptions-item label="表级进度">
            {{ analysis.completedTables }} / {{ analysis.totalTables || '?' }}
          </a-descriptions-item>
          <a-descriptions-item label="比较行数">{{ analysis.comparedRows.toLocaleString() }}</a-descriptions-item>
          <a-descriptions-item label="跳过表">{{ analysis.skippedTables }}</a-descriptions-item>
          <a-descriptions-item label="细筛分片">{{ analysis.fineScreenedChunks }}</a-descriptions-item>
          <a-descriptions-item label="候选操作">{{ analysis.candidateOperationCount }}</a-descriptions-item>
          <a-descriptions-item label="模板">
            {{ analysis.templateId ? `#${analysis.templateId}` : '尚未生成' }}
          </a-descriptions-item>
          <a-descriptions-item label="创建时间">{{ formatDate(analysis.createdAt) }}</a-descriptions-item>
          <a-descriptions-item label="完成时间">{{ formatDate(analysis.finishedAt) }}</a-descriptions-item>
        </a-descriptions>

        <a-alert
          v-if="analysis.errorMessage"
          class="detail-alert"
          type="error"
          show-icon
          message="分析未正常完成"
          :description="analysis.errorMessage"
        />

        <a-alert
          v-if="warningItems.length"
          class="detail-alert"
          type="warning"
          show-icon
          message="分析警告"
        >
          <template #description>
            <ul class="risk-list">
              <li v-for="(item, index) in warningItems" :key="`warning-${index}`">{{ item }}</li>
            </ul>
          </template>
        </a-alert>

        <a-alert
          v-if="riskItems.length || unknownCount"
          class="detail-alert"
          type="warning"
          show-icon
          message="覆盖率与 UNKNOWN 风险"
        >
          <template #description>
            <ul class="risk-list">
              <li v-for="(item, index) in riskItems" :key="`risk-${index}`">{{ item }}</li>
              <li v-if="unknownCount">有 {{ unknownCount }} 个相邻表区间为 UNKNOWN。</li>
            </ul>
          </template>
        </a-alert>

        <div class="detail-section-title">
          <strong>行级 Diff</strong>
          <a-select
            v-model:value="selectedTableName"
            style="min-width: 280px"
            placeholder="选择表"
            :options="tableOptions"
            :disabled="!tableOptions.length"
            @change="selectTable"
          />
        </div>
        <a-table
          :columns="diffColumns"
          :data-source="diffRows"
          :loading="diffLoading"
          :pagination="false"
          :row-key="rowKey"
          size="small"
          :scroll="{ x: 'max-content' }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'json'">
              {{ formatJson(getDiffValue(record, column.dataIndex)) }}
            </template>
            <template v-else>{{ getDiffValue(record, column.dataIndex) ?? '—' }}</template>
          </template>
        </a-table>
        <div class="diff-toolbar">
          <span class="secondary-text">
            {{ selectedTableName ? `表：${selectedTableName}` : '分析完成后可选择表查看 Diff' }}
            <template v-if="diffIncomplete || !diffChecksumValid"> · 当前结果存在异常</template>
          </span>
          <a-button
            size="small"
            :disabled="!diffNextCursor || diffLoading"
            :loading="diffLoading"
            @click="loadDiffs(diffNextCursor || undefined)"
          >
            下一页
          </a-button>
        </div>
      </template>
      <a-empty v-else-if="!loading" description="暂无分析详情" />
    </a-spin>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import type { TableColumnsType } from 'ant-design-vue';
import { message } from 'ant-design-vue';
import {
  getSeedCaptureAnalysis,
  listSeedCaptureAnalysisDiffs,
  type SeedCaptureAnalysis,
  type SeedJsonObject,
} from '../../api/seed';

const props = defineProps<{
  open: boolean;
  analysisId: number | null;
}>();

const emit = defineEmits<{ close: [] }>();

const route = useRoute();
const projectId = computed(() => Number(route.params.projectId) || 0);
const loading = ref(false);
const diffLoading = ref(false);
const analysis = ref<SeedCaptureAnalysis | null>(null);
const selectedTableName = ref<string>();
const diffRows = ref<SeedJsonObject[]>([]);
const diffNextCursor = ref<string | null>(null);
const diffIncomplete = ref(false);
const diffChecksumValid = ref(true);

const diffColumns: TableColumnsType = [
  { title: '区间', dataIndex: 'intervalIndex', width: 70 },
  { title: '类型', dataIndex: 'kind', width: 90 },
  { title: '基线样本', dataIndex: 'beforeSampleId', width: 90 },
  { title: '后置样本', dataIndex: 'afterSampleId', width: 90 },
  { title: '主键', key: 'json', dataIndex: 'primaryKey', width: 180 },
  { title: '变更前', key: 'json', dataIndex: 'before', width: 280 },
  { title: '变更后', key: 'json', dataIndex: 'after', width: 280 },
];

const tableOptions = computed(() =>
  (analysis.value?.summary.tables || []).map((table) => ({
    value: table.tableName,
    label: table.tableName,
  })),
);
const warningItems = computed(() => formatList(analysis.value?.summary.warnings));
const riskItems = computed(() => formatList(analysis.value?.summary.risks));
const unknownCount = computed(() =>
  (analysis.value?.summary.tables || []).reduce((count, table) => {
    return count + table.intervals.filter((interval) => intervalStatus(interval) === 'UNKNOWN').length;
  }, 0),
);

watch(
  () => [props.open, props.analysisId] as const,
  ([open]) => {
    if (open && props.analysisId != null) void loadAnalysis();
    if (!open) reset();
  },
  { immediate: true },
);

async function loadAnalysis() {
  if (!projectId.value || props.analysisId == null) return;
  loading.value = true;
  try {
    analysis.value = await getSeedCaptureAnalysis(projectId.value, props.analysisId);
    selectedTableName.value = tableOptions.value[0]?.value;
    diffRows.value = [];
    diffNextCursor.value = null;
    if (selectedTableName.value) await loadDiffs();
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载分析详情失败');
  } finally {
    loading.value = false;
  }
}

async function loadDiffs(cursor?: string) {
  if (!projectId.value || props.analysisId == null || !selectedTableName.value) return;
  diffLoading.value = true;
  try {
    const result = await listSeedCaptureAnalysisDiffs(
      projectId.value,
      props.analysisId,
      selectedTableName.value,
      cursor,
    );
    diffRows.value = result.rows;
    diffNextCursor.value = result.nextCursor;
    diffIncomplete.value = result.incomplete;
    diffChecksumValid.value = result.checksumValid;
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载 Diff 详情失败');
  } finally {
    diffLoading.value = false;
  }
}

function selectTable(value: string) {
  selectedTableName.value = value;
  diffNextCursor.value = null;
  void loadDiffs();
}

function reset() {
  analysis.value = null;
  selectedTableName.value = undefined;
  diffRows.value = [];
  diffNextCursor.value = null;
  diffIncomplete.value = false;
  diffChecksumValid.value = true;
}

function close() {
  emit('close');
}

function formatList(items: Array<string | SeedJsonObject> | undefined) {
  return (items || []).map((item) => typeof item === 'string' ? item : JSON.stringify(item));
}

function intervalStatus(interval: unknown) {
  if (!interval || typeof interval !== 'object') return '';
  const value = (interval as SeedJsonObject).status;
  return typeof value === 'string' ? value : '';
}

function getDiffValue(row: SeedJsonObject, dataIndex: unknown) {
  return typeof dataIndex === 'string' ? row[dataIndex] : undefined;
}

function formatJson(value: unknown) {
  if (value === undefined || value === null) return '—';
  return typeof value === 'object' ? JSON.stringify(value) : String(value);
}

function rowKey(_record: SeedJsonObject, index?: number) {
  return String(index ?? 0);
}

function formatDate(value: string | null | undefined) {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN');
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
</script>

<style scoped>
.detail-alert { margin-top: 16px; }
.detail-section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 18px 0 10px;
}
.risk-list { margin: 0; padding-left: 18px; }
.diff-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
}
.secondary-text { color: var(--muted); font-size: 12px; }
</style>
