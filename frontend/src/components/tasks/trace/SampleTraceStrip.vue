<template>
  <div class="sample-trace-strip">
    <template v-if="traceId">
      <span class="sample-trace-chip">{{ traceId.slice(0, 16) }}…</span>
      <a-button type="link" size="small" class="sample-trace-link" @click="open">查看链路 →</a-button>
    </template>
    <template v-else>
      <a-tooltip title="容量轮低采样或应用未回写 traceId，无法关联链路">
        <span class="sample-trace-disabled-wrap">
          <a-button type="link" size="small" disabled>查看链路 →</a-button>
        </span>
      </a-tooltip>
    </template>
  </div>
</template>

<script setup lang="ts">
const props = defineProps<{ traceId: string | null }>();
const emit = defineEmits<{ (e: 'open', traceId: string): void }>();

function open() {
  if (props.traceId) emit('open', props.traceId);
}
</script>

<style scoped>
.sample-trace-strip { display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
  padding: 7px 16px; border-bottom: 1px dashed #e2e8ee; }
.sample-trace-chip { display: inline-flex; align-items: center; gap: 6px;
  background: var(--accent-soft, #e6f5f6); color: var(--accent, #0b7f8a);
  font-family: var(--font-data, ui-monospace, Menlo, Consolas, monospace);
  font-size: 11px; font-weight: 600; border-radius: 999px; padding: 2px 10px; }
.sample-trace-disabled-wrap { display: inline-flex; }
</style>
