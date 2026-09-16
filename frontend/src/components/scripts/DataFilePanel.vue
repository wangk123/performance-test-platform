<template>
  <div class="data-file-panel">
    <div class="df-toolbar">
      <a-input-search v-model:value="keyword" allow-clear placeholder="搜索数据文件…" />
      <a-button type="primary" @click="uploadOpen = true">上传 CSV</a-button>
    </div>

    <a-table
      class="df-table"
      size="small"
      :columns="fileColumns"
      :data-source="filteredFiles"
      :loading="loading"
      :pagination="false"
      :row-key="(record: DataFile) => record.id"
      :scroll="{ x: 640 }"
      :locale="{ emptyText: projectId == null ? '请先选择项目。' : '暂无数据文件，上传 CSV 后可在场景中绑定分发。' }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'name'">
          <div class="df-name">
            <strong>{{ record.name }}</strong>
            <small v-if="record.remark">{{ record.remark }}</small>
          </div>
          <div class="df-header-cols" :title="headerSummary(record)">{{ headerSummary(record) }}</div>
        </template>
        <template v-else-if="column.key === 'latestVersion'">
          <span v-if="record.latestVersion" class="df-ver-chip">v{{ record.latestVersion.versionNo }} · 当前</span>
          <span v-else class="df-muted">-</span>
        </template>
        <template v-else-if="column.key === 'size'">
          <span class="df-mono">{{ record.latestVersion ? formatFileSize(record.latestVersion.sizeBytes) : '-' }}</span>
        </template>
        <template v-else-if="column.key === 'rows'">
          <span class="df-mono">{{ rowCountText(record.latestVersion?.rowCount) }}</span>
        </template>
        <template v-else-if="column.key === 'updatedAt'">
          <div class="df-updated">
            <span>{{ record.latestVersion ? formatDate(record.latestVersion.uploadedAt) : '-' }}</span>
            <small>{{ record.latestVersion?.uploadedBy ?? record.createdBy }}</small>
          </div>
        </template>
        <template v-else-if="column.key === 'actions'">
          <div class="df-row-actions">
            <a-button size="small" @click="openVersions(record)">版本</a-button>
            <a-button
              size="small"
              :disabled="!record.latestVersion"
              :loading="previewLoadingId === record.id"
              @click="openPreview(record)"
            >预览</a-button>
            <a-popconfirm
              title="删除后记录与文件级联清除，场景绑定将失效，确认删除？"
              ok-text="删除"
              :ok-button-props="{ danger: true }"
              @confirm="deleteFile(record)"
            >
              <a-button size="small" danger>删除</a-button>
            </a-popconfirm>
          </div>
        </template>
      </template>
    </a-table>

    <DataFileUploadDialog v-model:open="uploadOpen" :project-id="projectId" @uploaded="refresh" />
    <DataFileVersionDrawer v-model:open="versionsOpen" :project-id="projectId" :data-file="versionTarget" />

    <a-modal
      v-model:open="previewOpen"
      :title="previewTitle"
      :footer="null"
      width="720px"
      destroy-on-close
    >
      <a-spin :spinning="previewLoading">
        <p v-if="previewDetail" class="df-preview-hint">
          前 {{ previewDetail.previewRows.length }} 行 · 共 {{ rowCountText(previewDetail.version.rowCount) }} ·
          <span class="df-mono">sha256 {{ previewDetail.version.sha256.slice(0, 8) }}…</span>
        </p>
        <a-table
          v-if="previewDetail"
          size="small"
          :columns="previewColumns"
          :data-source="previewRows"
          :pagination="false"
          :row-key="(row: Record<string, string>) => row.__key"
          :scroll="{ x: previewScrollX }"
          :locale="{ emptyText: '该版本无可预览行（可能未启用首行表头或文件为空）。' }"
        />
      </a-spin>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import type { TableColumnsType } from 'ant-design-vue';
import {
  deleteDataFileApi,
  getDataFileVersionApi,
  listDataFilesApi,
} from '../../api/data-files';
import { formatDate, formatFileSize } from '../../utils/format';
import type { DataFile, DataFileVersionDetail } from '../../types';
import DataFileUploadDialog from './DataFileUploadDialog.vue';
import DataFileVersionDrawer from './DataFileVersionDrawer.vue';

const props = defineProps<{ projectId: number | null }>();

const files = ref<DataFile[]>([]);
const loading = ref(false);
const keyword = ref('');

const uploadOpen = ref(false);
const versionsOpen = ref(false);
const versionTarget = ref<DataFile | null>(null);

const previewOpen = ref(false);
const previewLoading = ref(false);
const previewLoadingId = ref<number | null>(null);
const previewTarget = ref<DataFile | null>(null);
const previewDetail = ref<DataFileVersionDetail | null>(null);

watch(
  () => props.projectId,
  () => void refresh(),
  { immediate: true },
);

async function refresh() {
  if (props.projectId == null) {
    files.value = [];
    return;
  }
  loading.value = true;
  try {
    files.value = await listDataFilesApi(props.projectId);
  } catch (error) {
    message.error(error instanceof Error ? error.message : '数据文件加载失败');
  } finally {
    loading.value = false;
  }
}

const filteredFiles = computed(() => {
  const kw = keyword.value.trim().toLowerCase();
  if (!kw) {
    return files.value;
  }
  return files.value.filter(
    (file) => file.name.toLowerCase().includes(kw) || (file.remark ?? '').toLowerCase().includes(kw),
  );
});

const fileColumns: TableColumnsType<DataFile> = [
  { title: '名称 / 表头', key: 'name', width: 190 },
  { title: '最新版本', key: 'latestVersion', width: 92 },
  { title: '大小', key: 'size', width: 76 },
  { title: '行数', key: 'rows', width: 82 },
  { title: '更新时间', key: 'updatedAt', width: 110 },
  { title: '操作', key: 'actions', width: 172 },
];

function headerSummary(file: DataFile) {
  return file.latestVersion?.headerColumns?.join(' · ') ?? '未解析表头';
}

function rowCountText(rowCount: number | null | undefined) {
  return rowCount == null ? '-' : rowCount.toLocaleString();
}

function openVersions(file: DataFile) {
  versionTarget.value = file;
  versionsOpen.value = true;
}

async function openPreview(file: DataFile) {
  if (!file.latestVersion || props.projectId == null) {
    return;
  }
  previewTarget.value = file;
  previewDetail.value = null;
  previewOpen.value = true;
  previewLoading.value = true;
  previewLoadingId.value = file.id;
  try {
    previewDetail.value = await getDataFileVersionApi(props.projectId, file.id, file.latestVersion.versionNo);
  } catch (error) {
    previewOpen.value = false;
    message.error(error instanceof Error ? error.message : '预览加载失败');
  } finally {
    previewLoading.value = false;
    previewLoadingId.value = null;
  }
}

const previewTitle = computed(() =>
  previewTarget.value?.latestVersion
    ? `${previewTarget.value.name} · v${previewTarget.value.latestVersion.versionNo} 预览`
    : '数据文件预览',
);

const previewColumns = computed<TableColumnsType>(() => {
  const headers = previewDetail.value?.version.headerColumns;
  const count = previewDetail.value?.previewRows[0]?.length ?? headers?.length ?? 0;
  return Array.from({ length: count }, (_, index) => ({
    title: headers?.[index] || `列${index + 1}`,
    dataIndex: String(index),
    key: String(index),
    width: 128,
    ellipsis: true,
  }));
});

const previewRows = computed(() =>
  (previewDetail.value?.previewRows ?? []).map((row, index) => {
    const record: Record<string, string> = { __key: String(index) };
    row.forEach((cell, cellIndex) => {
      record[String(cellIndex)] = cell;
    });
    return record;
  }),
);

const previewScrollX = computed(() => Math.max(previewColumns.value.length * 128, 320));

async function deleteFile(file: DataFile) {
  if (props.projectId == null) {
    return;
  }
  try {
    await deleteDataFileApi(props.projectId, file.id);
    message.success('数据文件已删除');
    if (versionTarget.value?.id === file.id) {
      versionsOpen.value = false;
    }
    if (previewTarget.value?.id === file.id) {
      previewOpen.value = false;
    }
    await refresh();
  } catch (error) {
    message.error(error instanceof Error ? error.message : '删除失败');
  }
}
</script>

<style scoped>
.df-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}

.df-toolbar .ant-input-search {
  flex: 1;
  min-width: 0;
}

.df-name {
  display: flex;
  align-items: baseline;
  gap: 6px;
  min-width: 0;
}

.df-name strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.df-name small {
  flex: none;
  font-size: 11px;
  color: var(--muted);
  font-weight: 400;
}

.df-header-cols {
  margin-top: 1px;
  font: 500 11px var(--font-data);
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.df-ver-chip {
  display: inline-block;
  font: 600 11px var(--font-data);
  color: var(--accent);
  background: var(--accent-soft);
  border: 1px solid var(--accent);
  border-radius: 999px;
  padding: 0 8px;
  line-height: 18px;
  white-space: nowrap;
}

.df-mono {
  font: 500 11.5px var(--font-data);
}

.df-muted {
  color: var(--muted);
}

.df-updated {
  display: flex;
  flex-direction: column;
  line-height: 1.35;
}

.df-updated span {
  font-size: 12px;
  color: var(--muted);
}

.df-updated small {
  font-size: 11px;
  color: var(--muted);
  opacity: .8;
}

.df-row-actions {
  display: flex;
  gap: 6px;
  justify-content: flex-end;
}

.df-preview-hint {
  margin: 0 0 10px;
  font-size: 12px;
  color: var(--muted);
}

.df-preview-hint .df-mono {
  color: var(--muted);
}
</style>
