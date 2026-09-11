<template>
  <section class="publish-tab">
    <div class="publish-layout">
      <div class="publish-main">
        <div v-if="can('PUBLISH')" class="card publish-form-card">
          <h4>发布</h4>
          <div class="plan-note warn publish-precondition">
            前置条件：报告已生成、总体结论已确认、无活跃执行。发布将冻结文档并固化快照，同时登记为「报告发布」版本。
          </div>
          <label class="publish-label">版本号（手填，同计划内唯一；低于最新版本不允许提交）</label>
          <a-input
            v-model:value="versionNo"
            placeholder="如：V1.0"
            :maxlength="32"
          />
          <label class="publish-label publish-conclusion-label">总体结论（发布人确认，必填；已预填自动判定文本，可修改）</label>
          <a-textarea
            v-model:value="conclusion"
            :rows="3"
            placeholder="总体结论（发布人确认，必填）"
          />
          <div class="publish-actions">
            <a-button v-if="can('NEW_REVISION')" @click="doc.transition('new-revision', undefined, '已发起新修订')">发起新修订</a-button>
            <a-button type="primary" :disabled="!conclusion.trim() || !versionNo.trim()" @click="publish">发布</a-button>
          </div>
        </div>
        <div v-else-if="doc.plan.value?.phase === 'PUBLISH'" class="plan-note ok">
          该计划已发布（终态）。变更请发起修订。
        </div>

        <div v-if="can('NEW_REVISION') && !can('PUBLISH')" class="publish-revision-row">
          <a-button @click="doc.transition('new-revision', undefined, '已发起新修订')">发起新修订</a-button>
        </div>

        <div class="card publish-merged-note">
          <p class="side-note">发布记录已并入「版本」Tab 的修订记录（带「报告发布」徽章），此处不再单列发布快照历史。</p>
        </div>
      </div>

      <aside class="publish-side">
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
              :disabled="doc.plan.value?.phase !== 'PUBLISH'"
              title="仅已发布计划可创建分享链接"
              @click="createShare"
            >创建分享链接（默认 30 天）</a-button>
          </div>
        </div>
        <div class="card side-card">
          <h4>发布权限</h4>
          <p class="side-note">计划负责人 / 项目 OWNER / 系统管理员。发布后文档冻结，变更走「发起新修订」。</p>
        </div>
      </aside>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { message } from 'ant-design-vue';
import { createShareApi, getPlanVerdictApi, listSharesApi, revokeShareApi } from '../../api/plan-doc';
import { copyToClipboard } from '../../utils/clipboard';
import type { PlanShareTokenView } from '../../types';
import type { usePlanDoc } from '../../composables/usePlanDoc';

const props = defineProps<{ doc: ReturnType<typeof usePlanDoc> }>();

const conclusion = ref('');
const versionNo = ref('');
const shares = ref<PlanShareTokenView[]>([]);

onMounted(() => void reload());

onMounted(async () => {
  const planId = props.doc.plan.value?.id;
  if (!planId) return;
  const verdict = await getPlanVerdictApi(planId).catch(() => null);
  if (verdict?.prefillConclusion && !conclusion.value.trim()) {
    conclusion.value = verdict.prefillConclusion; // 预填可改（V7：后端不预写文档）
  }
});

function can(action: string) {
  return Boolean(props.doc.permissions.value[action]);
}

async function reload() {
  const planId = props.doc.plan.value?.id;
  if (!planId) return;
  shares.value = await listSharesApi(planId).catch(() => []);
}

async function publish() {
  if (!versionNo.value.trim()) {
    message.warning('请填写版本号');
    return;
  }
  const ok = await props.doc.transition(
    'publish',
    { conclusion: conclusion.value.trim(), versionNo: versionNo.value.trim() },
    '已发布',
  );
  if (ok) await reload();
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
/* 规格：plan-document-prototype.html .publish-layout / .snapshot / .share-row */
.publish-tab {
  display: flex;
  flex-direction: column;
}

.publish-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 20px;
  align-items: start;
}

.publish-main {
  min-width: 0;
}

.card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 12px;
}

.publish-form-card {
  padding: 16px 18px;
  margin-bottom: 16px;
}

.publish-form-card h4 {
  margin: 0 0 10px;
  color: var(--muted);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.6px;
  text-transform: uppercase;
}

.publish-precondition {
  margin-bottom: 12px;
}

.plan-note {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 13px;
}

.plan-note.warn {
  background: var(--warning-soft);
  border: 1px solid var(--warn);
  color: var(--plan-warn-text);
}

.plan-note.ok {
  background: var(--ok-soft);
  border: 1px solid var(--ok);
  color: var(--plan-ok-text);
}

.publish-label {
  display: block;
  margin-bottom: 6px;
  color: var(--muted);
  font-size: 12px;
}

.publish-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 10px;
}

.publish-revision-row {
  margin-bottom: 16px;
}

.publish-history {
  padding: 16px 18px;
}

.publish-merged-note {
  padding: 14px 18px;
}

.publish-conclusion-label {
  margin-top: 12px;
}

.publish-side {
  display: flex;
  flex-direction: column;
  gap: 16px;
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

.side-note {
  margin: 0;
  color: var(--muted);
  font-size: 12.5px;
  line-height: 1.8;
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
