<template>
  <section class="panel review-tab">
    <div class="review-actions">
      <a-button v-if="can('START_REVIEW')" type="primary" @click="run('start-review', '已开始评审')">开始评审</a-button>
      <a-button v-if="can('APPROVE')" type="primary" @click="approve">评审通过</a-button>
      <a-button v-if="can('REJECT')" danger @click="reject">驳回</a-button>
      <span v-if="!can('COMMENT') && !can('APPROVE')" class="review-hint">当前阶段批注只读</span>
    </div>

    <div class="review-comment-input" v-if="can('COMMENT')">
      <a-textarea v-model:value="draft" :rows="2" placeholder="添加批注（全文档级）" />
      <a-button type="primary" :disabled="!draft.trim()" @click="submitComment">发批注</a-button>
    </div>

    <a-timeline class="review-timeline">
      <a-timeline-item v-for="comment in doc.comments.value" :key="comment.id" :color="comment.kind === 'SYSTEM' ? 'gray' : 'blue'">
        <div class="comment-head">
          <strong>{{ comment.author }}</strong>
          <a-tag v-if="comment.kind === 'SYSTEM'">系统</a-tag>
          <span class="comment-time">{{ new Date(comment.createdAt).toLocaleString() }}</span>
          <a-button
            v-if="comment.kind === 'REVIEW' && canDelete(comment)"
            type="link" size="small" danger
            @click="remove(comment)"
          >删除</a-button>
        </div>
        <p class="comment-body">{{ comment.content }}</p>
      </a-timeline-item>
    </a-timeline>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { message } from 'ant-design-vue';
import type { PlanComment } from '../../types';
import { useAuth } from '../../composables/useAuth';
import { deleteCommentApi } from '../../api/plan-doc';
import type { usePlanDoc } from '../../composables/usePlanDoc';

const props = defineProps<{ doc: ReturnType<typeof usePlanDoc> }>();

const draft = ref('');
const { currentUser } = useAuth();

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

async function reject() {
  const comment = window.prompt('驳回原因（必填，将作为批注留存）');
  if (!comment?.trim()) return;
  await props.doc.transition('reject', { comment }, '已驳回，退回草稿');
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
.review-actions { display: flex; gap: 8px; margin-bottom: 12px; }
.review-hint { color: var(--muted); align-self: center; }
.review-comment-input { display: flex; gap: 8px; align-items: flex-start; margin-bottom: 16px; }
.comment-head { display: flex; gap: 8px; align-items: center; }
.comment-time { color: var(--muted); font-size: 12px; }
.comment-body { margin: 4px 0 0; }
</style>
