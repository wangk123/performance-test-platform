<template>
  <div class="plan-versions">
    <div class="versions-layout">
      <div class="versions-main">
        <div class="versions-toolbar">
          <span v-if="dirty" class="versions-dirty">有未发布的变更：当前文档与最新版本快照不一致</span>
          <span v-else-if="versions.length" class="versions-clean">文档与最新版本快照一致</span>
          <a-button size="small" type="primary" @click="emit('request-publish')">新增版本</a-button>
        </div>

        <div v-if="versions.length === 0" class="plan-empty">（暂无版本，发布第一个版本以建立修订记录）</div>

        <div v-for="version in versions" :key="version.id" class="version-row">
          <div class="version-main">
            <span class="version-no">{{ version.versionNo }}</span>
            <span v-if="version.kind === 'PUBLISH'" class="version-kind">报告发布</span>
            <span class="version-note">{{ version.changeNote }}</span>
          </div>
          <div class="version-meta">
            <span>修订人 {{ version.author }}</span>
            <span>{{ timeLabel(version) }}</span>
            <span>{{ statusLabel(version.planPhase) }}</span>
          </div>
          <div class="version-actions">
            <a-button size="small" type="text" @click="openView(version)">查看全文</a-button>
            <a-button v-if="canEdit" size="small" type="text" @click="rollback(version)">回滚到此版本</a-button>
          </div>
        </div>
      </div>

      <aside class="versions-side">
        <div v-if="status === 'PUBLISHED'" class="plan-note ok">该计划已发布（终态）。变更请新增版本。</div>

        <div class="card side-card">
          <h4>只读分享链接</h4>
          <template v-if="shares.length">
            <div v-for="share in shares" :key="share.id" class="share-row">
              <div class="share-link mono" :class="{ revoked: shareState(share) !== 'active' }" :title="shareUrl(share.token)">
                {{ shareUrl(share.token) }}
              </div>
              <span class="share-state" :class="shareState(share)">{{ shareStateText(share) }}</span>
              <a-button
                v-if="shareState(share) === 'active'"
                size="small"
                @click="copyShare(share)"
              >复制</a-button>
              <a-button
                v-if="can('SHARE') && !share.revokedAt && shareState(share) === 'active'"
                size="small"
                danger
                @click="revoke(share)"
              >撤销</a-button>
            </div>
          </template>
          <div v-else class="plan-empty">暂无分享链接。</div>
          <div v-if="can('SHARE')" class="share-create">
            <a-button
              size="small"
              :disabled="status !== 'PUBLISHED'"
              title="仅已发布计划可创建分享链接"
              @click="createShare"
            >创建分享链接（默认 30 天）</a-button>
          </div>
        </div>
      </aside>
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
import { copyToClipboard } from '../../utils/clipboard';
import { STATUS_LABEL } from '../../utils/plan-status';
import {
  createShareApi,
  getPlanVersionApi,
  listPlanVersionsApi,
  listSharesApi,
  revokeShareApi,
} from '../../api/plan-doc';
import type { PlanShareTokenView, PlanVersionView } from '../../types';

/**
 * 版本 Tab：修订记录唯一展示处（spec 2026-09-10 §4、§9；2026-09-11 §3.3 版本与状态解耦）——
 * 发布时间倒序列表 + 未发布变更提示 + 查看全文 + 回滚（走 saveDocument 既有链路，不产生版本记录）；
 * 「新增版本」与状态解耦（弹窗由详情页持有）；流转「发布」收口到详情页按钮区；
 * 只读分享管理留在本 Tab（仅已发布计划）。
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
const shares = ref<PlanShareTokenView[]>([]);

const canEdit = computed(() => Boolean(props.doc.permissions.value.EDIT));
const status = computed(() => props.doc.plan.value?.status ?? 'PLANNING');

function can(action: string) {
  return Boolean(props.doc.permissions.value[action]);
}

onMounted(() => void reload());
watch(() => props.refreshTick, () => void reload());
watch(() => props.doc.plan.value?.id, (id) => { if (id) void reload(); });

async function reload() {
  const planId = props.doc.plan.value?.id;
  if (!planId) return;
  const response = await listPlanVersionsApi(planId).catch(() => null);
  versions.value = response?.versions ?? [];
  dirty.value = response?.bodyDiffersFromLatest ?? false;
  // 分享链接仅已发布计划存在（SHARE 键仅 PUBLISHED 为 true），非已发布不发请求
  if (status.value === 'PUBLISHED') {
    shares.value = await listSharesApi(planId).catch(() => []);
  } else {
    shares.value = [];
  }
}

function timeLabel(version: PlanVersionView): string {
  return version.updatedAt !== version.createdAt
    ? `修订于 ${formatDate(version.updatedAt)}`
    : formatDate(version.createdAt);
}

function statusLabel(planStatus: string): string {
  return STATUS_LABEL[planStatus] ?? planStatus;
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
      } else if (outcome === 'conflict') {
        message.info('文档已被他人修改，已保留平台版本，请刷新后重试');
      }
    },
  });
}

async function createShare() {
  const planId = props.doc.plan.value?.id;
  if (!planId) return;
  try {
    await createShareApi(planId);
    await reload();
    message.success('分享链接已创建');
  } catch (error) {
    message.error(error instanceof Error ? error.message : '创建分享链接失败');
  }
}

async function copyShare(share: PlanShareTokenView) {
  const succeeded = await copyToClipboard(shareUrl(share.token));
  if (succeeded) message.success('分享链接已复制');
  else message.error('复制失败，请手动选择复制');
}

async function revoke(record: PlanShareTokenView) {
  const planId = props.doc.plan.value?.id;
  if (!planId) return;
  try {
    await revokeShareApi(planId, record.id);
    await reload();
    message.success('已撤销');
  } catch (error) {
    message.error(error instanceof Error ? error.message : '撤销失败');
  }
}

function shareUrl(token: string) {
  return `${window.location.origin}/share/plans/${token}`;
}

function shareState(record: PlanShareTokenView): 'active' | 'expired' | 'revoked' {
  if (record.revokedAt) return 'revoked';
  if (record.expiresAt && new Date(record.expiresAt) < new Date()) return 'expired';
  return 'active';
}

function shareStateText(record: PlanShareTokenView) {
  return { active: '有效', expired: '已过期', revoked: '已撤销' }[shareState(record)];
}
</script>

<style scoped>
.plan-versions {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.versions-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 20px;
  align-items: start;
}

.versions-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.versions-side {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

@media (max-width: 1100px) {
  .versions-layout {
    grid-template-columns: minmax(0, 1fr);
  }
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

.card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 12px;
}

.side-card {
  padding: 16px 18px;
}

.side-card h4 {
  margin: 0 0 10px;
  color: var(--muted);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.6px;
  text-transform: uppercase;
}

.plan-note {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 13px;
}

.plan-note.ok {
  background: var(--ok-soft);
  border: 1px solid var(--ok);
  color: var(--plan-ok-text);
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

.version-kind {
  flex: none;
  padding: 0 8px;
  border-radius: 999px;
  background: var(--accent-soft);
  color: var(--plan-accent-text);
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
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

.share-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 0;
  border-bottom: 1px solid var(--line);
}

.share-row:last-of-type {
  border-bottom: none;
}

.share-link {
  flex: 1;
  min-width: 0;
  padding: 6px 10px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--canvas);
  color: var(--ink);
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.share-link.revoked {
  text-decoration: line-through;
  opacity: 0.6;
}

.share-state {
  flex: none;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}

.share-state.active {
  background: var(--ok-soft);
  color: var(--plan-ok-text);
}

.share-state.expired {
  background: var(--warning-soft);
  color: var(--plan-warn-text);
}

.share-state.revoked {
  background: var(--canvas);
  border: 1px solid var(--line);
  color: var(--muted);
}

.share-create {
  margin-top: 12px;
}
</style>
