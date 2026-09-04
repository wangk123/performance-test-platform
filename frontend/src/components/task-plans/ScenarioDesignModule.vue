<template>
  <div class="scenario-module-stub">
    <MdPreview :model-value="sectionContent" language="zh-CN" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { MdPreview } from 'md-editor-v3';
import 'md-editor-v3/lib/style.css';
import type { TaskPlan, TaskScenario } from '../../types';
import type { usePlanDoc } from '../../composables/usePlanDoc';
import { extractSection } from '../../utils/plan-markdown';

// Task 15 最小桩：仅渲染场景章节原文；Task 16 替换为完整交互实现。
// props/emits 契约与 Task 16 完整实现一致。
const props = defineProps<{ docPlan: ReturnType<typeof usePlanDoc>; plan: TaskPlan; scenarios: TaskScenario[] }>();
defineEmits<{ (e: 'changed'): void; (e: 'request-add'): void; (e: 'request-edit', scenario: TaskScenario): void }>();

const sectionContent = computed(() => extractSection(props.plan.body, '七、场景设计') ?? '（空）');
</script>
