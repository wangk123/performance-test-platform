<template>
  <section class="panel"><p>文档（Task 15 实现）</p></section>
</template>
<script setup lang="ts">
import type { usePlanDoc } from '../../composables/usePlanDoc';
defineProps<{ doc: ReturnType<typeof usePlanDoc>; plan: import('../../types').TaskPlan; scenarios: import('../../types').TaskScenario[] }>();
defineEmits<{ (e: 'changed'): void }>();
</script>
