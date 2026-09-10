<template>
  <aside class="anno-panel">
    <div class="anno-panel-head">
      <b>批注</b>
      <span class="anno-panel-count">未解决 {{ unresolved }} · 已解决 {{ resolved }}</span>
      <button type="button" class="anno-panel-close" title="收起" @click="emit('close')">»</button>
    </div>
    <div class="anno-panel-scroll">
      <template v-for="group in groups" :key="group.key">
        <div class="anno-panel-group" :class="{ grey: group.tone === 'grey' }">{{ group.title }}</div>
        <template v-if="group.key !== 'resolved' || resolvedExpanded">
          <PlanCommentCard
            v-for="thread in group.threads"
            :key="thread.root.id"
            :thread="thread"
            :doc="doc"
            @locate="emit('locate', $event)"
            @resolve="(t, r) => emit('resolve', t, r)"
            @reply="(t, c) => emit('reply', t, c)"
            @remove="(t, c) => emit('remove', t, c)"
          />
        </template>
        <a
          v-if="group.key === 'resolved' && group.threads.length > 0"
          href="#"
          class="anno-panel-toggle"
          @click.prevent="resolvedExpanded = !resolvedExpanded"
        >{{ resolvedExpanded ? '收起' : `展开 ${group.threads.length} 条` }}</a>
      </template>
      <div v-if="groups.every((g) => g.threads.length === 0)" class="plan-empty">暂无批注。悬浮文档任意行即可添加。</div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import type { PlanComment, PlanCommentThread } from '../../types';
import type { usePlanDoc } from '../../composables/usePlanDoc';
import PlanCommentCard from './PlanCommentCard.vue';

export interface PlanCommentPanelGroup {
  key: string;
  title: string;
  tone: 'normal' | 'grey';
  threads: PlanCommentThread[];
}

defineProps<{
  groups: PlanCommentPanelGroup[];
  unresolved: number;
  resolved: number;
  doc: ReturnType<typeof usePlanDoc>;
}>();
const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'locate', thread: PlanCommentThread): void;
  (e: 'resolve', thread: PlanCommentThread, resolved: boolean): void;
  (e: 'reply', thread: PlanCommentThread, content: string): void;
  (e: 'remove', thread: PlanCommentThread, comment: PlanComment): void;
}>();

const resolvedExpanded = ref(false);
</script>
