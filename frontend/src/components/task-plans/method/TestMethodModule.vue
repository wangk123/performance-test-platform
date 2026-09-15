<template>
  <section class="scenario-design-module">测试方法模块（构建中）</section>
</template>

<script setup lang="ts">
import type { TaskPlan, TaskScenario } from '../../../types';
import type { usePlanDoc } from '../../../composables/usePlanDoc';

defineProps<{ docPlan: ReturnType<typeof usePlanDoc>; plan: TaskPlan; scenarios: TaskScenario[] }>();
defineEmits<{ (e: 'changed'): void; (e: 'request-add'): void; (e: 'request-edit', scenario: TaskScenario): void }>();
</script>
