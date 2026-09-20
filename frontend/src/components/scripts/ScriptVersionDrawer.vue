<template>
  <a-drawer
    :open="open"
    :title="`版本历史 · ${script?.name ?? ''}`"
    :width="520"
    @close="close"
  >
    <div class="drawer-inner">
      <p v-if="rows.length" class="drawer-sub">{{ subSummary }}</p>
      <a-spin :spinning="loading" wrapper-class-name="drawer-scroll">
        <div v-if="draftRow" class="draft-card">
          <span class="draft-badge"><span class="pulse"></span>草稿</span>
          <div class="draft-main">
            <div class="draft-title">未发布修改</div>
            <div class="draft-meta">
              {{ formatDate(draftRow.version.uploadedAt) }} · {{ draftRow.version.uploadedBy }} ·
              {{ draftRow.version.originalFilename }}
            </div>
          </div>
          <a-popconfirm title="丢弃草稿后回到已发布状态重新编辑，确认？" @confirm="deleteRow(draftRow, '草稿已丢弃')">
            <a-button size="small" type="text" danger>删除草稿</a-button>
          </a-popconfirm>
        </div>

        <div
          v-for="(row, index) in publishedRows"
          :key="row.version.id"
          class="v-item"
          :class="{ 'is-current': index === 0 }"
        >
          <div class="v-rail"><span class="v-node"></span></div>
          <div class="v-card">
            <div class="v-head">
              <span class="v-chip" :class="{ 'is-current': index === 0 }">
                {{ row.version.versionLabel || `v${row.version.versionNo}` }}
              </span>
              <span v-if="index === 0" class="v-flag">当前版本</span>
              <span class="v-time">{{ formatDate(row.version.uploadedAt) }}</span>
            </div>
            <p class="v-remark" :class="{ none: !row.version.remark }">
              {{ row.version.remark || '（无变更说明）' }}
            </p>
            <div class="v-meta">
              <span class="who">{{ row.version.uploadedBy }}</span>
              <span v-for="name in row.referencedScenarioNames" :key="name" class="v-ref">
                <span class="link-glyph">◆</span>{{ name }}
              </span>
            </div>
            <div class="v-actions">
              <a-popconfirm title="以该版本内容重建草稿？当前未发布草稿将被覆盖。" @confirm="forkRow(row)">
                <a-button size="small" class="v-btn-ghost">重建草稿</a-button>
              </a-popconfirm>
              <a-popconfirm title="确认删除该版本？" @confirm="deleteRow(row, '版本已删除')">
                <a-button size="small" type="text" danger>删除</a-button>
              </a-popconfirm>
            </div>
          </div>
        </div>

        <a-empty v-if="!loading && publishedRows.length === 0 && !draftRow" description="暂无版本" />
      </a-spin>
    </div>
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
// 接口按 versionNo 倒序返回，publishedRows[0] 即最新已发布（当前版本）
const publishedRows = computed(() => rows.value.filter((row) => row.version.status === 'PUBLISHED'));
const subSummary = computed(() => {
  const parts = [`${publishedRows.value.length} 个已发布版本`];
  if (draftRow.value) {
    parts.push('1 个未发布草稿');
  }
  parts.push('版本号仅支持递增');
  return parts.join(' · ');
});

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
.drawer-inner {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.drawer-sub {
  flex-shrink: 0;
  padding: 0 20px 10px;
  color: var(--muted);
  font-size: 12px;
  border-bottom: 1px solid var(--border);
}
.drawer-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 14px 20px 20px;
}

/* 草稿卡 */
.draft-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  margin-bottom: 18px;
  background: var(--warning-soft);
  border: 1px solid var(--warning-border);
  border-radius: var(--radius);
}
.draft-badge {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 22px;
  padding: 0 8px;
  border-radius: 11px;
  background: var(--warn);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
}
.pulse {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #fff;
  animation: draft-pulse 1.8s ease-out infinite;
}
@keyframes draft-pulse {
  0% { box-shadow: 0 0 0 0 rgba(255, 255, 255, 0.55); }
  70% { box-shadow: 0 0 0 5px rgba(255, 255, 255, 0); }
  100% { box-shadow: 0 0 0 0 rgba(255, 255, 255, 0); }
}
@media (prefers-reduced-motion: reduce) {
  .pulse { animation: none; }
}
.draft-main {
  flex: 1;
  min-width: 0;
}
.draft-title {
  font-size: 13px;
  font-weight: 600;
}
.draft-meta {
  margin-top: 2px;
  font-size: 12px;
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 版本时间线 */
.v-item {
  display: grid;
  grid-template-columns: 18px 1fr;
  column-gap: 12px;
}
.v-rail {
  position: relative;
}
.v-rail::before {
  content: "";
  position: absolute;
  left: 50%;
  top: 0;
  bottom: -14px;
  width: 2px;
  transform: translateX(-50%);
  background: var(--line);
}
.v-item:last-of-type .v-rail::before {
  display: none;
}
.v-node {
  position: relative;
  z-index: 1;
  display: block;
  width: 12px;
  height: 12px;
  margin: 5px auto 0;
  border-radius: 50%;
  background: var(--surface);
  border: 2px solid var(--line-strong);
}
.v-item.is-current .v-node {
  border-color: var(--accent);
  background: var(--accent);
  box-shadow: 0 0 0 4px var(--accent-soft);
}

.v-card {
  padding: 12px 14px;
  margin-bottom: 14px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
}
.v-item.is-current .v-card {
  border-color: var(--accent);
}
.v-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.v-chip {
  font-family: var(--font-data);
  font-size: 13px;
  font-weight: 600;
  line-height: 1;
  padding: 4px 8px;
  border-radius: var(--radius-sm);
  background: var(--ok-soft);
  color: var(--ok);
}
.v-chip.is-current {
  background: var(--accent-soft);
  color: var(--accent);
}
.v-flag {
  font-size: 11px;
  line-height: 1;
  padding: 4px 7px;
  border-radius: 4px;
  color: var(--accent);
  background: var(--accent-soft);
  font-weight: 600;
  letter-spacing: 0.5px;
}
.v-time {
  margin-left: auto;
  flex-shrink: 0;
  font-size: 12px;
  color: var(--muted);
  font-variant-numeric: tabular-nums;
}
.v-remark {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.55;
  color: var(--ink);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.v-remark.none {
  color: var(--muted);
  font-style: italic;
}
.v-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}
.v-meta .who {
  font-size: 12px;
  color: var(--muted);
}
.v-ref {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  line-height: 1;
  padding: 4px 7px;
  border-radius: 4px;
  color: var(--muted);
  background: var(--surface-soft);
  border: 1px solid var(--border);
}
.v-ref .link-glyph {
  color: var(--accent);
  font-size: 10px;
}
.v-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--border);
}

/* 深色主题：竖线与引用 chip 提一档可见度 */
:global(:root[data-theme='dark'] .v-rail::before) {
  background: var(--line-strong);
}
:global(:root[data-theme='dark'] .v-ref) {
  color: rgba(255, 255, 255, 0.64);
}

/* 操作按钮：仅按钮响应 hover，卡片本身不高亮 */
.v-btn-ghost {
  border-color: var(--border-strong);
  color: var(--muted);
}
.v-btn-ghost:hover,
.v-btn-ghost:focus {
  border-color: var(--accent) !important;
  color: var(--accent) !important;
}
</style>
