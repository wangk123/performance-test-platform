<template>
  <div class="plan-versions">
    <div class="versions-toolbar">
      <span v-if="dirty" class="versions-dirty">有未发布的变更：当前文档与最新版本快照不一致</span>
      <span v-else-if="versions.length" class="versions-clean">文档与最新版本快照一致</span>
      <a-button v-if="canEdit" size="small" type="primary" @click="emit('request-publish')">发布版本</a-button>
    </div>

    <div v-if="versions.length === 0" class="plan-empty">（暂无版本，发布第一个版本以建立修订记录）</div>

    <div v-for="version in versions" :key="version.id" class="version-row">
      <div class="version-main">
        <span class="version-no">{{ version.versionNo }}</span>
        <span class="version-note">{{ version.changeNote }}</span>
      </div>
      <div class="version-meta">
        <span>修订人 {{ version.author }}</span>
        <span>{{ timeLabel(version) }}</span>
        <span>{{ phaseLabel(version.planPhase) }}</span>
      </div>
      <div class="version-actions">
        <a-button size="small" type="text" @click="openView(version)">查看全文</a-button>
        <a-button v-if="canEdit" size="small" type="text" @click="rollback(version)">回滚到此版本</a-button>
      </div>
    </div>

    <a-modal v-model:open="viewOpen" :title="`版本 ${viewing?.versionNo ?? ''} 全文`" :width="880" :footer="null">
      <MdPreview v-if="viewBody" class="plan-md" :model-value="viewBody" :theme="mdTheme" language="zh-CN" />
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch, onMounted } from 'vue';
import { message, Modal } from 'ant-design-vue';
import { MdPreview } from 'md-editor-v3';
import 'md-editor-v3/lib/style.css';
import type { usePlanDoc } from '../../composables/usePlanDoc';
import { useTheme } from '../../composables/useTheme';
import { formatDate } from '../../utils/format';
import { getPlanVersionApi, listPlanVersionsApi } from '../../api/plan-doc';
import type { PlanVersionView } from '../../types';

/**
 * 修订记录展示（spec 2026-09-10 §4）：发布时间倒序列表 + 未发布变更提示 +
 * 查看全文（只读快照预览）+ 回滚（快照内容走 saveDocument 既有链路，不产生版本记录）。
 */
const props = defineProps<{
  doc: ReturnType<typeof usePlanDoc>;
  refreshTick: number;
}>();
const emit = defineEmits<{ (e: 'request-publish'): void }>();

const { themeMode } = useTheme();
const mdTheme = computed(() => (themeMode.value === 'dark' ? 'dark' : 'light'));

const versions = ref<PlanVersionView[]>([]);
const dirty = ref(false);
const viewOpen = ref(false);
const viewing = ref<PlanVersionView | null>(null);
const viewBody = ref('');

const canEdit = computed(() => Boolean(props.doc.permissions.value.EDIT));

onMounted(() => void reload());
watch(() => props.refreshTick, () => void reload());

async function reload() {
  const planId = props.doc.plan.value?.id;
  if (!planId) return;
  const response = await listPlanVersionsApi(planId).catch(() => null);
  versions.value = response?.versions ?? [];
  dirty.value = response?.bodyDiffersFromLatest ?? false;
}

function timeLabel(version: PlanVersionView): string {
  return version.updatedAt !== version.createdAt
    ? `修订于 ${formatDate(version.updatedAt)}`
    : formatDate(version.createdAt);
}

function phaseLabel(phase: string): string {
  return ({ DRAFT: '草稿', REVIEW: '评审', EXECUTION: '执行', REPORT: '报告', PUBLISH: '已发布' })[phase] ?? phase;
}

async function openView(version: PlanVersionView) {
  const planId = props.doc.plan.value?.id;
  if (!planId) return;
  const detail = await getPlanVersionApi(planId, version.id).catch(() => null);
  if (!detail) {
    message.error('版本详情加载失败');
    return;
  }
  viewing.value = version;
  viewBody.value = detail.snapshotBody;
  viewOpen.value = true;
}

function rollback(version: PlanVersionView) {
  const planId = props.doc.plan.value?.id;
  if (!planId) return;
  Modal.confirm({
    title: `回滚到版本 ${version.versionNo}？`,
    content: '将以该版本快照覆盖当前文档（保留保存冲突保护）；回滚本身不产生版本记录。',
    okText: '回滚',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      const detail = await getPlanVersionApi(planId, version.id).catch(() => null);
      if (!detail) {
        message.error('版本详情加载失败');
        return;
      }
      const outcome = await props.doc.saveDocument(detail.snapshotBody);
      if (outcome === 'ok') {
        message.success(`已回滚到版本 ${version.versionNo}`);
        await reload();
      }
    },
  });
}
</script>

<style scoped>
.plan-versions {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.versions-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.versions-dirty {
  color: var(--plan-warn-text, var(--ink));
  font-size: 12.5px;
  font-weight: 600;
}

.versions-clean {
  color: var(--muted);
  font-size: 12px;
}

.version-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--surface);
}

.version-main {
  display: flex;
  align-items: baseline;
  gap: 10px;
  min-width: 0;
}

.version-no {
  flex: none;
  color: var(--ink);
  font: 650 13.5px var(--font-ui);
}

.version-note {
  min-width: 0;
  color: var(--ink);
  font-size: 12.5px;
  overflow-wrap: anywhere;
}

.version-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
  color: var(--muted);
  font-size: 12px;
}

.version-actions {
  display: flex;
  gap: 4px;
  margin-left: -8px;
}
</style>
