<template>
  <a-modal
    :open="open"
    title="文档冲突：平台版本已被他人修改"
    width="960px"
    :footer="null"
    @cancel="$emit('update:open', false)"
  >
    <p class="conflict-hint">当前 revision 与服务器不一致。请选择处理方式：</p>
    <div class="conflict-columns">
      <div class="conflict-col">
        <h4>平台当前版</h4>
        <div class="conflict-diff">
          <div v-for="(part, index) in diffParts" :key="index" class="diff-line" :class="part.kind">
            {{ part.text || ' ' }}
          </div>
        </div>
      </div>
      <div class="conflict-col">
        <h4>本地版</h4>
        <div class="conflict-text"><pre>{{ localMarkdown }}</pre></div>
      </div>
    </div>
    <div class="conflict-actions">
      <a-button @click="$emit('resolve', 'keep-server')">保留平台版</a-button>
      <a-button @click="$emit('resolve', 'take-local')">采纳本地版</a-button>
      <a-button type="primary" @click="$emit('resolve', 'manual')">手改（以平台版为基底）</a-button>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { diffLines } from 'diff';

const props = defineProps<{ open: boolean; serverMarkdown: string; localMarkdown: string }>();
defineEmits<{ (e: 'update:open', value: boolean): void; (e: 'resolve', kind: 'keep-server' | 'take-local' | 'manual'): void }>();

const diffParts = computed(() =>
  diffLines(props.localMarkdown, props.serverMarkdown).flatMap((part) =>
    part.value
      .replace(/\n$/, '')
      .split('\n')
      .filter((line) => line.trim().length > 0)
      .map((text) => ({ text, kind: part.added ? 'added' : part.removed ? 'removed' : 'same' })),
  ),
);
</script>

<style scoped>
.conflict-hint { color: var(--muted); }
.conflict-columns { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.conflict-col h4 { margin: 0 0 8px; }
.conflict-diff, .conflict-text { height: 360px; overflow: auto; border: 1px solid var(--border); border-radius: 6px; }
.diff-line { font-family: var(--font-data, monospace); font-size: 12px; padding: 0 8px; white-space: pre-wrap; }
.diff-line.added { background: rgba(47, 155, 106, 0.12); }
.diff-line.removed { background: rgba(209, 67, 67, 0.12); }
.conflict-text pre { margin: 0; padding: 8px; font-size: 12px; white-space: pre-wrap; }
.conflict-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 12px; }
</style>
