<template>
  <div class="plan-phase-stepper">
    <div v-for="(node, index) in nodes" :key="node.phase" class="stepper-node" :class="nodeClass(index)">
      <span class="stepper-dot">{{ index < currentIndex ? '✓' : index + 1 }}</span>
      <span class="stepper-label">{{ node.label }}</span>
      <span v-if="index === currentIndex" class="stepper-status">{{ statusLabel(phase, status) }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { PlanPhase, PlanStatus } from '../../types';
import { statusLabel } from '../../composables/usePlanDoc';

const props = defineProps<{ phase: PlanPhase; status: PlanStatus }>();

const nodes: { phase: PlanPhase; label: string }[] = [
  { phase: 'DRAFT', label: '草稿' },
  { phase: 'REVIEW', label: '评审' },
  { phase: 'EXECUTION', label: '执行' },
  { phase: 'REPORT', label: '报告' },
  { phase: 'PUBLISH', label: '发布' },
];

const currentIndex = computed(() => nodes.findIndex((n) => n.phase === props.phase));

function nodeClass(index: number) {
  return { done: index < currentIndex.value, active: index === currentIndex.value };
}
</script>

<style scoped>
.plan-phase-stepper {
  display: flex;
  gap: 24px;
  padding: 12px 16px;
  border: 1px solid var(--border);
  border-radius: 8px;
  margin-bottom: 16px;
}
.stepper-node { display: flex; align-items: center; gap: 8px; color: var(--muted); }
.stepper-node.done { color: var(--accent, #0b7f8a); }
.stepper-node.active { color: var(--accent, #0b7f8a); font-weight: 600; }
.stepper-dot {
  width: 22px; height: 22px; border-radius: 50%;
  display: inline-flex; align-items: center; justify-content: center;
  border: 1px solid currentColor; font-size: 12px;
}
.stepper-status { font-size: 12px; color: var(--muted); }
</style>
