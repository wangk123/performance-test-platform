<template>
  <div class="anno-card" :class="{ grey: thread.root.resolved }">
    <div class="anno-card-head">
      <span class="anno-card-author">{{ thread.root.author }}</span>
      <span class="anno-card-time">{{ new Date(thread.root.createdAt).toLocaleString() }}</span>
      <span v-if="thread.root.bodyRevision != null" class="anno-card-rev">rev {{ thread.root.bodyRevision }}</span>
      <span v-if="thread.root.anchorText" class="anno-card-state">{{ thread.root.resolved ? '已解决' : '待处理' }}</span>
    </div>
    <div v-if="thread.root.anchorText" class="anno-card-quote">「{{ thread.root.anchorText }}」</div>
    <div class="anno-card-body">{{ thread.root.content }}</div>

    <div v-for="reply in thread.replies" :key="reply.id" class="anno-card-reply">
      <b>{{ reply.author }}</b>：{{ reply.content }}
      <a-button
        v-if="reply.canDelete"
        type="link" size="small" danger class="anno-card-reply-del"
        @click="emit('remove', thread, reply)"
      >删除</a-button>
    </div>

    <template v-if="replying">
      <PlanCommentComposer
        placeholder="回复…"
        :busy="busy"
        @submit="submitReply"
        @cancel="replying = false"
      />
    </template>
    <div v-else-if="canReply" class="anno-card-reply-entry" @click="replying = true">回复…</div>
    <div v-else-if="thread.root.resolved && can('COMMENT')" class="anno-card-locked">已解决线程已锁定，先「重新打开」再回复。</div>

    <div class="anno-card-actions">
      <a v-if="showLocate && thread.root.anchorLine != null" href="#" class="anno-card-link" @click.prevent="emit('locate', thread)">↧ 定位</a>
      <a-button
        v-if="thread.root.canResolve"
        type="link" size="small"
        @click="emit('resolve', thread, !thread.root.resolved)"
      >{{ thread.root.resolved ? '重新打开' : '✓ 解决' }}</a-button>
      <a-button
        v-if="thread.root.canDelete"
        type="link" size="small" danger
        @click="emit('remove', thread, thread.root)"
      >删除</a-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import type { PlanComment, PlanCommentThread } from '../../types';
import type { usePlanDoc } from '../../composables/usePlanDoc';
import PlanCommentComposer from './PlanCommentComposer.vue';

const props = withDefaults(defineProps<{
  thread: PlanCommentThread;
  showLocate?: boolean;
  doc: ReturnType<typeof usePlanDoc>;
}>(), { showLocate: true });
const emit = defineEmits<{
  (e: 'locate', thread: PlanCommentThread): void;
  (e: 'resolve', thread: PlanCommentThread, resolved: boolean): void;
  (e: 'reply', thread: PlanCommentThread, content: string): void;
  (e: 'remove', thread: PlanCommentThread, comment: PlanComment): void;
}>();

const replying = ref(false);
const busy = ref(false);
const canReply = computed(() => Boolean(props.doc.permissions.value.COMMENT) && !props.thread.root.resolved);

function can(action: string) {
  return Boolean(props.doc.permissions.value[action]);
}

async function submitReply(content: string) {
  busy.value = true;
  emit('reply', props.thread, content);
  busy.value = false;
  replying.value = false;
}
</script>
