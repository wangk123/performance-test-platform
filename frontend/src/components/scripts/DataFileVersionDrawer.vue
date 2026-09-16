<template>
  <a-drawer
    :open="open"
    :title="drawerTitle"
    :width="440"
    @close="close"
  >
    <p v-if="dataFile" class="df-ver-desc">执行装配时始终取最新版本，manifest 记录实际版本与 SHA-256。</p>
    <a-spin :spinning="loading">
      <div v-if="versions.length > 0" class="df-timeline">
        <div
          v-for="version in versions"
          :key="version.versionNo"
          class="tl-item"
          :class="{ current: version.versionNo === currentVersionNo }"
        >
          <div class="tl-head">
            <span class="ver-dot">{{ version.versionNo === currentVersionNo ? '●' : '○' }}</span>
            <strong>v{{ version.versionNo }}</strong>
            <span v-if="version.versionNo === currentVersionNo" class="tl-chip">执行取此版</span>
          </div>
          <div class="tl-meta">
            {{ version.originalFilename }} · {{ formatFileSize(version.sizeBytes) }} ·
            <span class="df-mono">sha256 {{ version.sha256.slice(0, 8) }}…</span>
          </div>
          <div class="tl-meta">
            {{ formatDate(version.uploadedAt) }} · {{ version.uploadedBy }} ·
            <a
              class="df-download"
              :class="{ disabled: downloadingNo === version.versionNo }"
              @click="download(version)"
            >{{ downloadingNo === version.versionNo ? '下载中…' : '下载' }}</a>
          </div>
          <div v-if="version.remark" class="tl-meta">备注：{{ version.remark }}</div>
        </div>
      </div>
      <a-empty
        v-else
        :description="loading ? '版本加载中…' : dataFile ? '该数据文件暂无版本记录' : '选择数据文件后查看版本'"
      />
    </a-spin>
  </a-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import { AUTH_TOKEN_KEY } from '../../api/http';
import { dataFileDownloadUrl, listDataFileVersionsApi } from '../../api/data-files';
import { formatDate, formatFileSize } from '../../utils/format';
import type { DataFile, DataFileVersion } from '../../types';

const props = defineProps<{ projectId: number | null; dataFile: DataFile | null; open: boolean }>();
const emit = defineEmits<{ (e: 'update:open', value: boolean): void }>();

const versions = ref<DataFileVersion[]>([]);
const loading = ref(false);
const downloadingNo = ref<number | null>(null);

const drawerTitle = computed(() =>
  props.dataFile ? `${props.dataFile.name} · 版本时间线` : '版本时间线',
);

const currentVersionNo = computed(() => versions.value[0]?.versionNo ?? null);

watch(
  () => [props.open, props.dataFile?.id, props.projectId] as const,
  ([open]) => {
    if (open) {
      void loadVersions();
    }
  },
  { immediate: true },
);

async function loadVersions() {
  if (!props.open || props.dataFile == null || props.projectId == null) {
    versions.value = [];
    return;
  }
  loading.value = true;
  try {
    const list = await listDataFileVersionsApi(props.projectId, props.dataFile.id);
    versions.value = [...list].sort((a, b) => b.versionNo - a.versionNo);
  } catch (error) {
    versions.value = [];
    message.error(error instanceof Error ? error.message : '版本加载失败');
  } finally {
    loading.value = false;
  }
}

function close() {
  emit('update:open', false);
}

/* 认证走 Authorization Bearer 头，不能直接 <a href> 下载（会 401），
   用 fetch 拿 blob 再经 objectURL 触发浏览器下载。 */
async function download(version: DataFileVersion) {
  if (props.projectId == null || props.dataFile == null || downloadingNo.value != null) {
    return;
  }
  downloadingNo.value = version.versionNo;
  try {
    const token = localStorage.getItem(AUTH_TOKEN_KEY);
    const response = await fetch(
      dataFileDownloadUrl(props.projectId, props.dataFile.id, version.versionNo),
      { headers: token ? { Authorization: `Bearer ${token}` } : undefined },
    );
    if (!response.ok) {
      const body = await response.json().catch(() => null);
      throw new Error(body?.message ?? `下载失败 (${response.status})`);
    }
    const url = URL.createObjectURL(await response.blob());
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = version.originalFilename;
    anchor.click();
    URL.revokeObjectURL(url);
  } catch (error) {
    message.error(error instanceof Error ? error.message : '下载失败');
  } finally {
    downloadingNo.value = null;
  }
}
</script>

<style scoped>
.df-ver-desc {
  margin: 0 0 14px;
  font-size: 12px;
  color: var(--muted);
}

.df-timeline {
  position: relative;
  padding-left: 4px;
}

.tl-item {
  position: relative;
  padding: 0 0 16px 22px;
}

.tl-item::before {
  content: '';
  position: absolute;
  left: 5px;
  top: 6px;
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: var(--surface);
  border: 2px solid var(--line-strong);
}

.tl-item.current::before {
  border-color: var(--accent);
  background: var(--accent);
}

.tl-item:not(:last-child)::after {
  content: '';
  position: absolute;
  left: 9px;
  top: 17px;
  bottom: 0;
  width: 1px;
  background: var(--line);
}

.tl-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12.5px;
  font-weight: 600;
}

.ver-dot {
  color: var(--accent);
  font-weight: 700;
  font-size: 11px;
}

.tl-chip {
  font: 600 10.5px var(--font-data);
  color: var(--accent);
  background: var(--accent-soft);
  border-radius: 4px;
  padding: 0 6px;
}

.tl-meta {
  margin-top: 2px;
  font-size: 11.5px;
  color: var(--muted);
}

.df-mono {
  font-family: var(--font-data);
}

.df-download {
  color: var(--accent);
  cursor: pointer;
}

.df-download.disabled {
  opacity: .6;
  cursor: default;
}
</style>
