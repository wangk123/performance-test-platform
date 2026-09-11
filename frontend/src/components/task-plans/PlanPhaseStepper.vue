<template>
  <div class="plan-phase-stepper">
    <template v-for="(label, index) in stages" :key="label">
      <div
        class="phase"
        :class="phaseClass(index)"
        :aria-current="index === currentIndex ? 'step' : undefined"
      >
        <div class="node">{{ index < currentIndex ? '✓' : index + 1 }}</div>
        <div class="pname">{{ label }}</div>
      </div>
      <div v-if="index < stages.length - 1" class="p-line" :class="{ done: index < currentIndex }" />
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { STAGES, stageIndexOf } from '../../utils/plan-status';

/** 单一 status 推导流程位置（spec 2026-09-11 §3.2）：下标 < 当前 = 已完成，= 当前 = 进行中，> 当前 = 未开始。 */
const props = defineProps<{ status: string }>();

const stages = STAGES;
const currentIndex = computed(() => stageIndexOf(props.status));

function phaseClass(index: number) {
  return { done: index < currentIndex.value, current: index === currentIndex.value };
}
</script>

<style scoped>
/* 紧凑变体：嵌于页头右列（规格：2026-09-09-plan-detail-layout-optimization-design.md §5） */
.plan-phase-stepper {
  display: flex;
  align-items: flex-start;
}

.phase {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  flex: none;
}

.phase .node {
  width: 20px;
  height: 20px;
  border: 1px solid var(--line-strong);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--surface);
  color: var(--muted);
  font-size: 11px;
  font-weight: 600;
}

.phase.done .node {
  border-color: var(--ok);
  background: var(--ok);
  color: var(--accent-ink);
}

.phase.current .node {
  border-color: var(--accent);
  background: var(--accent);
  color: var(--accent-ink);
}

.phase .pname {
  color: var(--muted);
  font-size: 11px;
  line-height: 1;
  white-space: nowrap;
}

.phase.done .pname {
  color: var(--plan-ok-text);
}

.phase.current .pname {
  color: var(--plan-accent-text);
  font-weight: 600;
}

.p-line {
  flex: none;
  width: 18px;
  height: 2px;
  margin: 9px 6px 0;
  background: var(--line-strong);
}

.p-line.done {
  background: var(--ok);
}
</style>
