<template>
  <a-modal
    :open="open"
    title="样本详情"
    width="1120px"
    :footer="null"
    destroy-on-close
    @cancel="close"
  >
    <a-spin :spinning="loading">
      <template v-if="sample">
        <a-descriptions bordered size="small" :column="3">
          <a-descriptions-item label="样本">#{{ sample.id }} · S{{ sample.sampleSeq }}</a-descriptions-item>
          <a-descriptions-item label="状态">
            <a-tag :color="statusColor(sample.status)">{{ statusLabel(sample.status) }}</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="策略版本">v{{ sample.configVersion }}</a-descriptions-item>
          <a-descriptions-item label="开始时间">{{ formatDate(sample.captureStartedAt) }}</a-descriptions-item>
          <a-descriptions-item label="完成时间">{{ formatDate(sample.captureFinishedAt) }}</a-descriptions-item>
          <a-descriptions-item label="进度">
            {{ sample.completedTables }} / {{ sample.totalTables || '?' }} 张表
          </a-descriptions-item>
          <a-descriptions-item label="采集行数">{{ sample.capturedRows.toLocaleString() }}</a-descriptions-item>
          <a-descriptions-item label="写入大小">{{ formatBytes(sample.writtenBytes) }}</a-descriptions-item>
          <a-descriptions-item label="活动 Worker">{{ sample.activeWorkers }}</a-descriptions-item>
        </a-descriptions>

        <a-alert
          v-if="sample.incomplete"
          class="detail-alert"
          type="warning"
          show-icon
          message="该样本数据不完整，分析时必须显式确认，缺失范围会按 UNKNOWN 处理。"
          :description="sample.errorMessage || undefined"
        />

        <div class="detail-section-title">
          <strong>库表摘要</strong>
          <span>{{ tables.length }} 张表{{ tablesIncomplete ? ' · 存在不完整表' : '' }}</span>
        </div>
        <a-table
          :columns="tableColumns"
          :data-source="tables"
          :pagination="false"
          :loading="tablesLoading"
          row-key="id"
          size="small"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'tableName'">
              <strong>{{ record.tableName }}</strong>
              <a-tag v-if="record.riskyNoPk" color="orange" class="inline-tag">无主键风险</a-tag>
            </template>
            <template v-else-if="column.key === 'status'">
              <a-tag :color="record.incomplete ? 'orange' : statusColor(record.status)">
                {{ record.incomplete ? '不完整' : statusLabel(record.status) }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'actions'">
              <a-button size="small" @click="openRows(record)">
                {{ selectedTable?.tableName === record.tableName ? '刷新行数据' : '查看行数据' }}
              </a-button>
            </template>
          </template>
        </a-table>

        <div v-if="selectedTable" class="rows-section">
          <div class="detail-section-title">
            <strong>{{ selectedTable.tableName }} · 行数据</strong>
            <a-space>
              <a-button size="small" :loading="rowLoading" @click="loadRows()">回到第一页</a-button>
              <a-button size="small" :disabled="!nextCursor || rowLoading" :loading="rowLoading" @click="loadRows(nextCursor || undefined)">
                下一页
              </a-button>
            </a-space>
          </div>

          <a-alert
            v-if="rowIncomplete || !rowChecksumValid"
            type="warning"
            show-icon
            message="当前表存在缺失或校验异常"
            description="异常分片不会被当作空数据处理。"
          />

          <a-collapse class="detail-collapse">
            <a-collapse-panel key="schema" header="查看 schema 与风险元数据">
              <pre class="json-preview">{{ JSON.stringify(activeSchema, null, 2) }}</pre>
              <div v-if="selectedTable.errorMessage" class="error-text">{{ selectedTable.errorMessage }}</div>
            </a-collapse-panel>
            <a-collapse-panel key="chunks" :header="`分片诊断（${selectedTable.chunks.length}）`">
              <a-table
                :columns="chunkColumns"
                :data-source="rowDiagnostics.length ? rowDiagnostics : selectedTable.chunks"
                :pagination="false"
                row-key="chunkSeq"
                size="small"
              >
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'validity'">
                    <a-tag :color="chunkIncomplete(record) ? 'orange' : 'green'">
                      {{ chunkIncomplete(record) ? '异常' : '正常' }}
                    </a-tag>
                  </template>
                </template>
              </a-table>
            </a-collapse-panel>
          </a-collapse>

          <a-table
            class="row-table"
            :columns="rowColumns"
            :data-source="rows"
            :pagination="false"
            :loading="rowLoading"
            :row-key="rowKey"
            size="small"
            :scroll="{ x: 'max-content' }"
          >
            <template #bodyCell="{ column, record }">
              {{ formatCell(getRowValue(record, column.dataIndex)) }}
            </template>
          </a-table>
          <div v-if="!rowLoading && !rows.length" class="empty-rows">当前页没有行数据。</div>
        </div>
      </template>
      <a-empty v-else-if="!loading" description="暂无样本详情" />
    </a-spin>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import type { TableColumnsType } from 'ant-design-vue';
import { message } from 'ant-design-vue';
import {
  getSeedCaptureSample,
  listSeedCaptureSampleRows,
  listSeedCaptureSampleTables,
  type SeedCaptureChunk,
  type SeedCaptureSample,
  type SeedCaptureSampleTable,
  type SeedJsonObject,
} from '../../api/seed';

const props = defineProps<{
  open: boolean;
  sampleId: number | null;
}>();

const emit = defineEmits<{ close: [] }>();

const route = useRoute();
const projectId = computed(() => Number(route.params.projectId) || 0);
const loading = ref(false);
const tablesLoading = ref(false);
const rowLoading = ref(false);
const sample = ref<SeedCaptureSample | null>(null);
const tables = ref<SeedCaptureSampleTable[]>([]);
const selectedTable = ref<SeedCaptureSampleTable | null>(null);
const rows = ref<SeedJsonObject[]>([]);
const nextCursor = ref<string | null>(null);
const rowSchema = ref<SeedJsonObject>({});
const rowDiagnostics = ref<SeedCaptureChunk[]>([]);
const rowIncomplete = ref(false);
const rowChecksumValid = ref(true);

const tableColumns: TableColumnsType<SeedCaptureSampleTable> = [
  { title: '表名', key: 'tableName' },
  { title: '行数', dataIndex: 'rowCount', width: 110 },
  { title: '分片', dataIndex: 'chunkCount', width: 80 },
  { title: '状态', key: 'status', width: 110 },
  { title: '操作', key: 'actions', width: 120 },
];
const chunkColumns: TableColumnsType<SeedCaptureChunk> = [
  { title: '分片', dataIndex: 'chunkSeq', width: 80 },
  { title: '行数', dataIndex: 'rowCount', width: 100 },
  { title: '大小', dataIndex: 'byteSize', width: 110 },
  { title: '状态', dataIndex: 'status', width: 100 },
  { title: '校验', key: 'validity', width: 90 },
];
const rowColumns = computed<TableColumnsType>(() => {
  const keys = new Set<string>();
  rows.value.forEach((row) => Object.keys(row).forEach((key) => keys.add(key)));
  return [...keys].map((key) => ({ title: key, dataIndex: key, key }));
});
const tablesIncomplete = computed(() => tables.value.some((table) => table.incomplete));
const activeSchema = computed(() =>
  Object.keys(rowSchema.value).length ? rowSchema.value : selectedTable.value?.schema || {},
);

watch(
  () => [props.open, props.sampleId] as const,
  ([open]) => {
    if (open && props.sampleId != null) void loadDetail();
    if (!open) reset();
  },
  { immediate: true },
);

async function loadDetail() {
  if (!projectId.value || props.sampleId == null) return;
  loading.value = true;
  tablesLoading.value = true;
  try {
    const [sampleResult, tableResult] = await Promise.all([
      getSeedCaptureSample(projectId.value, props.sampleId),
      listSeedCaptureSampleTables(projectId.value, props.sampleId),
    ]);
    sample.value = sampleResult;
    tables.value = tableResult.tables;
    selectedTable.value = null;
    rows.value = [];
    nextCursor.value = null;
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载样本详情失败');
  } finally {
    loading.value = false;
    tablesLoading.value = false;
  }
}

async function openRows(table: SeedCaptureSampleTable) {
  selectedTable.value = table;
  rows.value = [];
  nextCursor.value = null;
  await loadRows();
}

async function loadRows(cursor?: string) {
  if (!projectId.value || props.sampleId == null || !selectedTable.value) return;
  rowLoading.value = true;
  try {
    const result = await listSeedCaptureSampleRows(
      projectId.value,
      props.sampleId,
      selectedTable.value.tableName,
      cursor,
    );
    rows.value = result.rows;
    nextCursor.value = result.nextCursor;
    rowSchema.value = result.schema;
    rowDiagnostics.value = result.chunks;
    rowIncomplete.value = result.incomplete;
    rowChecksumValid.value = result.checksumValid;
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载行数据失败');
  } finally {
    rowLoading.value = false;
  }
}

function reset() {
  sample.value = null;
  tables.value = [];
  selectedTable.value = null;
  rows.value = [];
  nextCursor.value = null;
  rowSchema.value = {};
  rowDiagnostics.value = [];
  rowIncomplete.value = false;
  rowChecksumValid.value = true;
}

function close() {
  emit('close');
}

function getRowValue(row: SeedJsonObject, dataIndex: unknown) {
  return typeof dataIndex === 'string' ? row[dataIndex] : undefined;
}

function rowKey(_record: SeedJsonObject, index?: number) {
  return String(index ?? 0);
}

function formatCell(value: unknown) {
  if (value === null || value === undefined) return 'NULL';
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
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

function chunkIncomplete(chunk: SeedCaptureChunk) {
  return chunk.incomplete === true || chunk.checksumValid === false || chunk.status !== 'READY';
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
.detail-section-title span { color: var(--muted); font-size: 12px; }
.inline-tag { margin-left: 8px; }
.rows-section { margin-top: 18px; }
.detail-collapse { margin: 12px 0; }
.json-preview {
  max-height: 220px;
  margin: 0;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
}
.row-table { margin-top: 12px; }
.empty-rows { color: var(--muted); padding: 18px 0; text-align: center; }
.error-text { color: var(--danger); margin-top: 8px; }
</style>
