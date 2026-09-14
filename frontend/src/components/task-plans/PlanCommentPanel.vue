<template>
  <aside class="anno-panel">
    <div class="anno-panel-head">
      <b>批注</b>
      <span class="anno-panel-count">未解决 {{ unresolved }} · 已解决 {{ resolved }}</span>
      <button type="button" class="anno-panel-close" title="收起" @click="emit('close')">»</button>
    </div>
    <div ref="scrollRef" class="anno-panel-scroll">
      <template v-for="group in groups" :key="group.key">
        <div class="anno-panel-group" :class="{ grey: group.tone === 'grey' }">{{ group.title }}</div>
        <template v-if="group.key !== 'resolved' || resolvedExpanded">
          <PlanCommentCard
            v-for="thread in group.threads"
            :key="thread.root.id"
            :thread="thread"
            :doc="doc"
            :data-thread-id="thread.root.id"
            @locate="emit('locate', $event)"
            @resolve="(t, r) => emit('resolve', t, r)"
            @reply="(t, c) => emit('reply', t, c)"
            @edit="(t, c) => emit('edit', t, c)"
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
import { nextTick, ref } from 'vue';
import type { PlanComment, PlanCommentThread } from '../../types';
import type { usePlanDoc } from '../../composables/usePlanDoc';
import PlanCommentCard from './PlanCommentCard.vue';

export interface PlanCommentPanelGroup {
  key: string;
  title: string;
  tone: 'normal' | 'grey';
  threads: PlanCommentThread[];
}

const props = defineProps<{
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
  (e: 'edit', thread: PlanCommentThread, content: string): void;
  (e: 'remove', thread: PlanCommentThread, comment: PlanComment): void;
}>();

const resolvedExpanded = ref(false);
const scrollRef = ref<HTMLElement | null>(null);

/** 正文徽标反向定位（spec §3.2）：已解决组先展开，滚动到首张命中卡片并闪烁全部命中卡片。 */
function reveal(threadIds: number[]): void {
  const idSet = new Set(threadIds);
  const hiddenInResolved = props.groups.some(
    (g) => g.key === 'resolved' && !resolvedExpanded.value && g.threads.some((t) => idSet.has(t.root.id)),
  );
  if (hiddenInResolved) resolvedExpanded.value = true;
  void nextTick(() => {
    const container = scrollRef.value;
    if (!container) return;
    const cards = [...container.querySelectorAll<HTMLElement>('.anno-card')]
      .filter((el) => idSet.has(Number(el.dataset.threadId)));
    if (!cards.length) return;
    const first = cards[0];
    container.scrollTo({
      top: container.scrollTop + first.getBoundingClientRect().top
        - container.getBoundingClientRect().top
        - container.clientHeight / 2 + first.clientHeight / 2,
      behavior: 'smooth',
    });
    for (const card of cards) {
      card.classList.remove('anno-card-flash');
      void card.offsetWidth; // 重启动画：连续点击同一徽标也能再次闪烁
      card.classList.add('anno-card-flash');
      window.setTimeout(() => card.classList.remove('anno-card-flash'), 1600);
    }
  });
}

defineExpose({ reveal });
</script>
