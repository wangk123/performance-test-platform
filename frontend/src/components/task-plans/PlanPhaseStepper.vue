<template>
  <div class="plan-phase-stepper">
    <template v-for="(node, index) in nodes" :key="node.phase">
      <div
        class="phase"
        :class="phaseClass(index)"
        :aria-current="index === currentIndex ? 'step' : undefined"
      >
        <div class="node">{{ index < currentIndex ? '✓' : index + 1 }}</div>
        <div class="pname">{{ node.label }}</div>
        <div class="sub">{{ subLabel(node.phase, index) }}</div>
      </div>
      <div v-if="index < nodes.length - 1" class="p-line" :class="{ done: index < currentIndex }" />
    </template>
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

function phaseClass(index: number) {
  return { done: index < currentIndex.value, current: index === currentIndex.value };
}

/** 当前阶段显示真实子状态；已过阶段给既成事实；未来阶段给领域化待办文案。 */
function subLabel(phase: PlanPhase, index: number): string {
  if (phase === 'PUBLISH' && index >= currentIndex.value && props.phase !== 'PUBLISH') return '—';
  if (index < currentIndex.value) {
    return { DRAFT: '已完成', REVIEW: '评审通过', EXECUTION: '执行完成', REPORT: '已生成', PUBLISH: '已发布' }[phase];
  }
  if (index === currentIndex.value) return statusLabel(phase, props.status);
  return { DRAFT: '未开始', REVIEW: '待评审', EXECUTION: '待执行', REPORT: '待生成', PUBLISH: '—' }[phase];
}
</script>

<style scoped>
/* 规格：plan-document-prototype.html .stepper（数值原样取自原型） */
.plan-phase-stepper {
  display: flex;
  align-items: flex-start;
  padding: 14px 16px;
  background: var(--canvas);
  border-radius: 10px;
  overflow-x: auto;
}

.phase {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
  flex: none;
  min-width: 78px;
}

.phase .node {
  width: 24px;
  height: 24px;
  border: 1.5px solid var(--line);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--surface);
  color: var(--muted);
  font-size: 11px;
  font-weight: 700;
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
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}

.phase.done .pname {
  color: var(--ok);
}

.phase.current .pname {
  color: var(--accent);
  font-weight: 700;
}

.phase .sub {
  padding: 1px 8px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--surface);
  color: var(--muted);
  font-size: 11px;
  white-space: nowrap;
}

.phase.done .sub {
  border-color: var(--ok);
  background: var(--ok-soft);
  color: var(--ok);
}

.phase.current .sub {
  border-color: var(--accent);
  background: var(--accent);
  color: var(--accent-ink);
  font-weight: 600;
}

.p-line {
  flex: none;
  width: 26px;
  height: 1.5px;
  margin: 11px 6px 0;
  background: var(--line-strong);
}

.p-line.done {
  background: var(--ok);
}
</style>
