<template>
  <a-drawer
    :open="open"
    :title="`版本历史：${script?.name ?? ''}`"
    :width="520"
    @close="close"
  >
    <a-spin :spinning="loading">
      <div v-if="draftRow" class="version-row draft">
        <div class="version-main">
          <div class="version-head">
            <a-tag color="orange">草稿</a-tag>
            <span class="version-title">未发布修改</span>
            <span class="version-time">{{ formatDate(draftRow.version.uploadedAt) }}</span>
          </div>
          <p class="version-meta">{{ draftRow.version.originalFilename }} · {{ draftRow.version.uploadedBy }}</p>
        </div>
        <div class="version-actions">
          <a-popconfirm title="丢弃草稿后回到已发布状态重新编辑，确认？" @confirm="deleteRow(draftRow, '草稿已丢弃')">
            <a-button size="small" danger>删除草稿</a-button>
          </a-popconfirm>
        </div>
      </div>
      <div v-for="row in publishedRows" :key="row.version.id" class="version-row">
        <div class="version-main">
          <div class="version-head">
            <a-tag color="green">{{ row.version.versionLabel || `v${row.version.versionNo}` }}</a-tag>
            <span class="version-title">{{ row.version.remark || '（无变更说明）' }}</span>
          </div>
          <p class="version-meta">
            {{ formatDate(row.version.uploadedAt) }} · {{ row.version.uploadedBy }}
            <span v-if="row.referencedScenarioNames?.length">
              · 被「{{ row.referencedScenarioNames.join('、') }}」引用
            </span>
          </p>
        </div>
        <div class="version-actions">
          <a-popconfirm
            title="以该版本内容重建草稿？当前未发布草稿将被覆盖。"
            @confirm="forkRow(row)"
          >
            <a-button size="small">基于此版本建草稿</a-button>
          </a-popconfirm>
          <a-popconfirm title="确认删除该版本？" @confirm="deleteRow(row, '版本已删除')">
            <a-button size="small" danger>删除</a-button>
          </a-popconfirm>
        </div>
      </div>

      <a-empty v-if="!loading && publishedRows.length === 0 && !draftRow" description="暂无版本" />
    </a-spin>
  </a-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import type { ScriptAsset } from '../../types';
import {
  deleteScriptVersionApi,
  forkDraftApi,
  listScriptVersionsApi,
  type BackendScriptVersionWithRefs,
} from '../../api/scripts';
import { formatDate } from '../../utils/format';
import { useAuth } from '../../composables/useAuth';

const props = defineProps<{
  open: boolean;
  script: ScriptAsset | null;
}>();
const emit = defineEmits<{
  (e: 'update:open', value: boolean): void;
  (e: 'changed'): void;
}>();

const { currentUser } = useAuth();
const rows = ref<BackendScriptVersionWithRefs[]>([]);
const loading = ref(false);

const draftRow = computed(() => rows.value.find((row) => row.version.status === 'DRAFT') ?? null);
const publishedRows = computed(() => rows.value.filter((row) => row.version.status === 'PUBLISHED'));

watch(() => props.open, async (open) => {
  if (open && props.script) {
    await reload();
  }
});

async function reload() {
  if (!props.script) {
    return;
  }
  loading.value = true;
  try {
    rows.value = await listScriptVersionsApi(props.script.projectId, props.script.scriptId);
  } catch (error) {
    message.error(error instanceof Error ? error.message : '版本历史加载失败');
  } finally {
    loading.value = false;
  }
}

async function deleteRow(row: BackendScriptVersionWithRefs, successText: string) {
  if (!props.script) {
    return;
  }
  try {
    await deleteScriptVersionApi(props.script.projectId, props.script.scriptId, row.version.id);
    message.success(successText);
    emit('changed');
    await reload();
  } catch (error) {
    message.error(error instanceof Error ? error.message : '删除失败');
  }
}

async function forkRow(row: BackendScriptVersionWithRefs) {
  if (!props.script) {
    return;
  }
  try {
    await forkDraftApi(
      props.script.projectId,
      props.script.scriptId,
      row.version.id,
      currentUser.value?.username ?? 'admin',
    );
    message.success(`已基于 ${row.version.versionLabel || 'v' + row.version.versionNo} 重建草稿`);
    emit('changed');
    await reload();
  } catch (error) {
    message.error(error instanceof Error ? error.message : '草稿重建失败');
  }
}

function close() {
  emit('update:open', false);
}
</script>

<style scoped>
.version-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 4px;
  border-bottom: 1px solid var(--border, #f0f0f0);
}
.version-row.draft {
  background: rgba(250, 173, 20, 0.06);
  border-radius: 6px;
  padding-left: 10px;
  padding-right: 10px;
  margin-bottom: 8px;
}
.version-main {
  min-width: 0;
}
.version-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.version-title {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.version-time {
  color: var(--muted);
  font-size: 12px;
}
.version-meta {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 12px;
}
.version-actions {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex-shrink: 0;
}
</style>
