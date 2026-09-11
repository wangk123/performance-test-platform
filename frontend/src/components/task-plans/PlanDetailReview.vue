<template>
  <section class="review-tab">
    <div class="workbench-actions">
      <span class="workbench-badge">{{ phaseText }} · rev {{ doc.plan.value?.revision ?? '-' }}</span>
      <span class="workbench-hint" v-if="unresolvedCount > 0 && can('APPROVE')">未解决批注 {{ unresolvedCount }} 条</span>
      <span style="flex: 1" />
      <a-button v-if="can('START_REVIEW')" type="primary" @click="run('start-review', '已开始评审')">开始评审</a-button>
      <a-button v-if="can('APPROVE')" type="primary" @click="approve">评审通过</a-button>
      <a-button v-if="can('REJECT')" danger @click="openReject">驳回</a-button>
      <span v-if="!can('COMMENT') && !can('APPROVE')" class="review-hint">当前阶段批注只读</span>
    </div>

    <div class="workbench-filters">
      <button
        v-for="filter in FILTERS"
        :key="filter.key"
        type="button"
        class="workbench-chip"
        :class="{ active: activeFilter === filter.key }"
        @click="activeFilter = filter.key"
      >{{ filter.label }} {{ filter.count() }}</button>
    </div>

    <div class="workbench-list">
      <template v-for="group in visibleGroups" :key="group.key">
        <div class="workbench-group">{{ group.title }}</div>
        <PlanCommentCard
          v-for="thread in group.threads"
          :key="thread.root.id"
          :thread="thread"
          :doc="doc"
          @locate="(t) => emit('locate', t.root.id)"
          @resolve="(t, r) => doc.resolveComment(t.root.id, r)"
          @reply="(t, c) => doc.addAnchoredComment({ content: c, parentId: t.root.id })"
          @edit="(t, c) => doc.editComment(t.root.id, c)"
          @remove="removeComment"
        />
      </template>
      <div v-if="visibleGroups.length === 0" class="plan-empty">
        {{ activeFilter === 'all' ? '暂无批注。到文档 Tab 悬浮任意行即可添加。' : '该筛选下暂无批注' }}
      </div>
    </div>

    <div class="workbench-flow">
      <div class="workbench-group">流转记录</div>
      <div
        v-for="comment in flowRecords"
        :key="comment.id"
        class="workbench-flow-item"
      >· {{ comment.author }} {{ comment.content }} —— {{ new Date(comment.createdAt).toLocaleString() }}</div>
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
import { Modal, message } from 'ant-design-vue';
import type { PlanComment, PlanCommentThread } from '../../types';
import { deleteCommentApi } from '../../api/plan-doc';
import { deriveAnchors } from '../../utils/plan-anchors';
import PlanCommentCard from './PlanCommentCard.vue';
import type { usePlanDoc } from '../../composables/usePlanDoc';

const props = defineProps<{ doc: ReturnType<typeof usePlanDoc> }>();
const emit = defineEmits<{ (e: 'locate', commentId: number): void }>();

const rejectOpen = ref(false);
const rejectReason = ref('');
const rejecting = ref(false);
const activeFilter = ref<'all' | 'unresolved' | 'resolved' | 'broken' | 'unanchored'>('all');

const PHASE_TEXT: Record<string, string> = {
  DRAFT: '草稿', REVIEW: '评审', EXECUTION: '执行', REPORT: '报告', PUBLISH: '发布',
};
const phaseText = computed(() => PHASE_TEXT[props.doc.plan.value?.phase ?? 'DRAFT'] ?? '草稿');

function can(action: string) {
  return Boolean(props.doc.permissions.value[action]);
}

const unresolvedCount = computed(() => props.doc.unresolvedCount.value);
const flowRecords = computed(() => props.doc.comments.value.filter((c) => c.kind === 'SYSTEM'));

const resolutions = computed(() => deriveAnchors(props.doc.plan.value?.body, props.doc.anchoredRoots.value));

function threadState(thread: PlanCommentThread): 'unresolved' | 'resolved' | 'broken' | 'unanchored' {
  if (props.doc.unanchoredThreads.value.includes(thread)) return 'unanchored';
  const resolution = resolutions.value.get(thread.root.id);
  if (resolution?.state === 'broken') return 'broken';
  return thread.root.resolved ? 'resolved' : 'unresolved';
}

const FILTERS = computed(() => {
  const counts = { all: props.doc.threads.value.length, unresolved: 0, resolved: 0, broken: 0, unanchored: 0 };
  for (const thread of props.doc.threads.value) counts[threadState(thread)] += 1;
  return [
    { key: 'all', label: '全部', count: () => counts.all },
    { key: 'unresolved', label: '未解决', count: () => counts.unresolved },
    { key: 'resolved', label: '已解决', count: () => counts.resolved },
    { key: 'broken', label: '断链', count: () => counts.broken },
    { key: 'unanchored', label: '未锚定', count: () => counts.unanchored },
  ] as const;
});

const visibleGroups = computed(() => {
  const filtered = props.doc.threads.value.filter(
    (thread) => activeFilter.value === 'all' || threadState(thread) === activeFilter.value,
  );
  const bySection = new Map<string, PlanCommentThread[]>();
  for (const thread of filtered) {
    const section = resolutions.value.get(thread.root.id)?.sectionTitle ?? '未锚定';
    bySection.set(section, [...(bySection.get(section) ?? []), thread]);
  }
  return [...bySection.entries()].map(([title, threads]) => ({ key: title, title, threads }));
});

function run(action: 'start-review', text: string) {
  void props.doc.transition(action, undefined, text);
}

function approve() {
  if (unresolvedCount.value > 0) {
    Modal.confirm({
      title: '评审通过确认',
      content: `仍有 ${unresolvedCount.value} 条未解决批注。通过后进入执行阶段，批注将保留在文档上。`,
      okText: '仍要通过',
      cancelText: '取消',
      onOk: () => props.doc.transition('approve', undefined, '评审已通过'),
    });
    return;
  }
  void props.doc.transition('approve', undefined, '评审已通过');
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

async function removeComment(thread: PlanCommentThread, comment: PlanComment) {
  if (!props.doc.plan.value) return;
  await deleteCommentApi(props.doc.plan.value.id, comment.id);
  await props.doc.refresh();
  message.success('批注已删除');
}
</script>
