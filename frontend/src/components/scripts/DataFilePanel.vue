<template>
  <div class="data-file-panel">
    <div class="df-toolbar">
      <a-input-search v-model:value="keyword" allow-clear placeholder="搜索数据文件…" />
      <a-button type="primary" @click="uploadOpen = true">上传 CSV</a-button>
    </div>
    <p class="df-count">共 {{ filteredFiles.length }} 个数据文件</p>

    <a-spin :spinning="loading">
      <div v-if="filteredFiles.length" class="df-list">
        <div v-for="(file, index) in filteredFiles" :key="file.id" class="df-card">
          <div class="df-head">
            <span class="df-icon">CSV</span>
            <div class="df-title">
              <strong :title="file.name">{{ file.name }}</strong>
              <small v-if="file.remark">{{ file.remark }}</small>
            </div>
            <span class="df-idx">{{ String(index + 1).padStart(2, '0') }}</span>
          </div>
          <div class="df-cols" :title="headerSummary(file)">
            <span v-for="col in file.latestVersion?.headerColumns ?? []" :key="col" class="df-col">{{ col }}</span>
            <span v-if="!file.latestVersion?.headerColumns?.length" class="df-col">未解析表头</span>
          </div>
          <div class="df-stats">
            <div class="df-stat">
              <span class="k">大小</span>
              <span class="v">{{ file.latestVersion ? formatFileSize(file.latestVersion.sizeBytes) : '-' }}</span>
            </div>
            <div class="df-stat">
              <span class="k">行数</span>
              <span class="v">{{ rowCountText(file.latestVersion?.rowCount) }}</span>
            </div>
            <div class="df-stat">
              <span class="k">更新时间</span>
              <span class="v">{{ file.latestVersion ? formatDate(file.latestVersion.uploadedAt) : '-' }}</span>
            </div>
          </div>
          <div class="df-foot">
            <span class="df-by">{{ file.latestVersion?.uploadedBy ?? file.createdBy }} 上传</span>
            <a-button
              size="small"
              :disabled="!file.latestVersion"
              :loading="previewLoadingId === file.id"
              @click="openPreview(file)"
            >预览</a-button>
            <a-popconfirm
              title="删除后记录与文件级联清除，场景绑定将失效，确认删除？"
              ok-text="删除"
              :ok-button-props="{ danger: true }"
              @confirm="deleteFile(file)"
            >
              <a-button size="small" type="text" danger>删除</a-button>
            </a-popconfirm>
          </div>
        </div>
      </div>
      <div v-else class="df-empty">
        <p>{{ projectId == null ? '请先选择项目。' : '暂无数据文件，上传 CSV 后可在场景中绑定分发。' }}</p>
        <a-button v-if="projectId != null" type="primary" @click="uploadOpen = true">上传 CSV</a-button>
      </div>
    </a-spin>

    <DataFileUploadDialog v-model:open="uploadOpen" :project-id="projectId" @uploaded="refresh" />

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

const props = defineProps<{ projectId: number | null }>();

const files = ref<DataFile[]>([]);
const loading = ref(false);
const keyword = ref('');

const uploadOpen = ref(false);

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

function headerSummary(file: DataFile) {
  return file.latestVersion?.headerColumns?.join(' · ') ?? '未解析表头';
}

function rowCountText(rowCount: number | null | undefined) {
  return rowCount == null ? '-' : rowCount.toLocaleString();
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
    ? `${previewTarget.value.name} 预览`
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
  margin-bottom: 6px;
}

.df-toolbar .ant-input-search {
  flex: 1;
  min-width: 0;
}

.df-count {
  margin: 0 0 10px;
  font-size: 11.5px;
  color: var(--muted);
}

.df-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.df-card {
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--surface);
  padding: 11px 12px 0;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.df-card:hover {
  border-color: var(--accent);
  box-shadow: 0 1px 2px rgba(26, 35, 50, 0.04);
}

.df-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.df-icon {
  flex: none;
  width: 30px;
  height: 30px;
  border-radius: var(--radius-sm);
  background: var(--accent-soft);
  color: var(--accent);
  display: flex;
  align-items: center;
  justify-content: center;
  font: 700 8.5px var(--font-data);
  letter-spacing: 0.5px;
}

.df-title {
  flex: 1;
  min-width: 0;
  line-height: 1.35;
}

.df-title strong {
  display: block;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.df-title small {
  display: block;
  font-size: 11px;
  color: var(--muted);
  margin-top: 1px;
}

.df-idx {
  flex: none;
  font: 500 11px var(--font-data);
  color: var(--muted);
  letter-spacing: 1px;
}

.df-cols {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 9px;
}

.df-col {
  font: 500 10.5px var(--font-data);
  color: var(--muted);
  background: var(--surface-soft);
  border: 1px solid var(--line);
  border-radius: 4px;
  padding: 1px 6px;
  line-height: 16px;
}

.df-stats {
  display: grid;
  grid-template-columns: 1fr 1fr 1.3fr;
  margin-top: 10px;
  border-top: 1px dashed var(--line);
  padding: 8px 0;
}

.df-stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 0 8px;
  border-left: 1px solid var(--line);
  min-width: 0;
}

.df-stat:first-child {
  border-left: none;
  padding-left: 0;
}

.df-stat .k {
  font-size: 10.5px;
  color: var(--muted);
}

.df-stat .v {
  font: 500 12px var(--font-data);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.df-foot {
  display: flex;
  align-items: center;
  gap: 6px;
  border-top: 1px solid var(--line);
  margin: 0 -12px;
  padding: 5px 12px;
}

.df-by {
  flex: 1;
  min-width: 0;
  font-size: 11px;
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.df-empty {
  border: 1px dashed var(--line-strong);
  border-radius: var(--radius);
  padding: 36px 20px;
  text-align: center;
  color: var(--muted);
}

.df-empty p {
  font-size: 12.5px;
  line-height: 1.6;
  margin: 0 0 14px;
}

.df-mono {
  font-family: var(--font-data);
}

.df-preview-hint {
  margin: 0 0 10px;
  font-size: 12px;
  color: var(--muted);
}
</style>
