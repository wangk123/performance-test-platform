<template>
  <section class="review-tab">
    <div class="review-layout">
      <div class="review-main">
        <div class="review-actions">
          <a-button v-if="can('START_REVIEW')" type="primary" @click="run('start-review', '已开始评审')">开始评审</a-button>
          <a-button v-if="can('APPROVE')" type="primary" @click="approve">评审通过</a-button>
          <a-button v-if="can('REJECT')" danger @click="openReject">驳回</a-button>
          <span v-if="!can('COMMENT') && !can('APPROVE')" class="review-hint">当前阶段批注只读</span>
        </div>

        <div class="card comment-box" v-if="can('COMMENT')">
          <a-textarea
            v-model:value="draft"
            :rows="3"
            placeholder="添加批注（全文档级，评审中全员可见）"
          />
          <div class="comment-box-actions">
            <span class="comment-hint">批注不进文档正文，随流转记录留存。</span>
            <a-button type="primary" :disabled="!draft.trim()" @click="submitComment">发批注</a-button>
          </div>
        </div>

        <div class="timeline">
          <div
            v-for="comment in doc.comments.value"
            :key="comment.id"
            class="tl-item"
            :class="comment.kind === 'SYSTEM' ? 'system' : 'review'"
          >
            <div class="tl-dot">{{ comment.kind === 'SYSTEM' ? '◌' : '💬' }}</div>
            <div class="tl-head">
              <span class="author">{{ comment.author }}</span>
              <span class="tl-tag">{{ comment.kind === 'SYSTEM' ? '系统' : '批注' }}</span>
              <span class="tl-time">{{ new Date(comment.createdAt).toLocaleString() }}</span>
              <a-button
                v-if="comment.kind === 'REVIEW' && canDelete(comment)"
                type="link"
                size="small"
                danger
                @click="remove(comment)"
              >删除</a-button>
            </div>
            <div class="tl-body">{{ comment.content }}</div>
          </div>
          <div v-if="doc.comments.value.length === 0" class="plan-empty">暂无评审记录。提交评审后流转与批注将在此展示。</div>
        </div>
      </div>

      <aside class="review-side">
        <div class="card side-card">
          <h4>评审信息</h4>
          <div class="kv"><span class="k">当前阶段</span><span class="v">{{ phaseText }} · {{ statusText }}</span></div>
          <div class="kv"><span class="k">批注数</span><span class="v">{{ reviewCount }}</span></div>
          <div class="kv"><span class="k">流转记录</span><span class="v">{{ systemCount }}</span></div>
          <div class="kv"><span class="k">通过权限</span><span class="v">任意项目成员</span></div>
        </div>
        <div class="card side-card side-accent">
          <h4>评审规则</h4>
          <p class="side-note">
            阶段流转：草稿 → 评审 → 执行 → 报告 → 发布；<br />
            评审：待评审 / 评审中 / 评审通过；<br />
            驳回退回草稿，原因作为批注留存；进入执行后历史批注只读，新修订会开启新一轮评审。
          </p>
        </div>
      </aside>
    </div>

    <a-modal
      v-model:open="rejectOpen"
      title="驳回评审"
      ok-text="驳回"
      :ok-button-props="{ danger: true, disabled: !rejectReason.trim() }"
      :confirm-loading="rejecting"
      @ok="confirmReject"
    >
      <p class="reject-hint">驳回原因必填，将作为批注留存并退回草稿。</p>
      <a-textarea v-model:value="rejectReason" :rows="4" placeholder="填写驳回原因（必填）" />
    </a-modal>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { message } from 'ant-design-vue';
import type { PlanComment } from '../../types';
import { useAuth } from '../../composables/useAuth';
import { statusLabel } from '../../composables/usePlanDoc';
import { deleteCommentApi } from '../../api/plan-doc';
import type { usePlanDoc } from '../../composables/usePlanDoc';

const props = defineProps<{ doc: ReturnType<typeof usePlanDoc> }>();

const draft = ref('');
const rejectOpen = ref(false);
const rejectReason = ref('');
const rejecting = ref(false);
const { currentUser } = useAuth();

const PHASE_TEXT: Record<string, string> = {
  DRAFT: '草稿', REVIEW: '评审', EXECUTION: '执行', REPORT: '报告', PUBLISH: '发布',
};

const phase = computed(() => props.doc.plan.value?.phase ?? 'DRAFT');
const status = computed(() => props.doc.plan.value?.status ?? 'DRAFT');
const phaseText = computed(() => PHASE_TEXT[phase.value] ?? phase.value);
const statusText = computed(() => statusLabel(phase.value, status.value));
const reviewCount = computed(() => props.doc.comments.value.filter((c) => c.kind === 'REVIEW').length);
const systemCount = computed(() => props.doc.comments.value.filter((c) => c.kind === 'SYSTEM').length);

function can(action: string) {
  return Boolean(props.doc.permissions.value[action]);
}

function canDelete(comment: PlanComment) {
  return can('DELETE') || comment.author === currentUser.value?.username;
}

async function run(action: 'start-review' | 'withdraw', text: string) {
  await props.doc.transition(action, undefined, text);
}

async function approve() {
  await props.doc.transition('approve', undefined, '评审已通过');
}

function openReject() {
  rejectReason.value = '';
  rejectOpen.value = true;
}

async function confirmReject() {
  const comment = rejectReason.value.trim();
  if (!comment) return;
  rejecting.value = true;
  const ok = await props.doc.transition('reject', { comment }, '已驳回，退回草稿');
  rejecting.value = false;
  if (ok) rejectOpen.value = false;
}

async function submitComment() {
  await props.doc.addComment(draft.value.trim());
  draft.value = '';
}

async function remove(comment: PlanComment) {
  if (!props.doc.plan.value) return;
  await deleteCommentApi(props.doc.plan.value.id, comment.id);
  await props.doc.refresh();
  message.success('批注已删除');
}
</script>

<style scoped>
/* 规格：plan-document-prototype.html .review-layout / .timeline / .tl-* / .side-card */
.review-tab {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.review-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 20px;
  align-items: start;
}

.review-main {
  min-width: 0;
}

.review-actions {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  align-items: center;
}

.review-hint {
  color: var(--muted);
  font-size: 12px;
}

.card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 12px;
}

.comment-box {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px 16px;
  margin-bottom: 16px;
}

.comment-box-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.comment-hint {
  color: var(--muted);
  font-size: 12px;
}

/* 自绘时间线（替代 a-timeline 蓝点） */
.timeline {
  position: relative;
  padding-left: 26px;
}

.timeline::before {
  content: '';
  position: absolute;
  left: 8px;
  top: 6px;
  bottom: 6px;
  width: 2px;
  background: var(--line);
}

.tl-item {
  position: relative;
  margin-bottom: 14px;
}

.tl-dot {
  position: absolute;
  left: -26px;
  top: 2px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  z-index: 1;
}

.tl-item.review .tl-dot {
  background: var(--accent);
  color: var(--accent-ink);
}

.tl-item.system .tl-dot {
  background: var(--canvas);
  border: 1.5px solid var(--line-strong);
  color: var(--muted);
}

.tl-head {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--muted);
  font-size: 12px;
}

.tl-head .author {
  color: var(--ink);
  font-weight: 600;
}

.tl-tag {
  padding: 0 6px;
  border: 1px solid var(--line);
  border-radius: 4px;
  color: var(--muted);
  font-size: 11px;
}

.tl-time {
  margin-left: auto;
  font-family: var(--font-data);
}

.tl-body {
  margin-top: 6px;
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.tl-item.review .tl-body {
  background: var(--accent-soft);
  border: 1px solid var(--line);
  color: var(--ink);
}

.tl-item.system .tl-body {
  background: var(--canvas);
  color: var(--muted);
}

/* 右侧元信息 */
.review-side {
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

.kv {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 3px 0;
  font-size: 12px;
}

.kv .k {
  color: var(--muted);
}

.kv .v {
  font-family: var(--font-data);
  color: var(--ink);
  text-align: right;
}

.side-accent {
  background: var(--accent-soft);
  border-color: var(--accent);
}

.side-accent h4 {
  color: var(--accent);
}

.side-note {
  margin: 0;
  color: var(--muted);
  font-size: 12.5px;
  line-height: 1.8;
}

.reject-hint {
  margin: 0 0 12px;
  color: var(--muted);
  font-size: 12px;
}
</style>
