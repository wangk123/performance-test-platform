<template>
  <section class="panel"><p>报告（Task 16 实现）</p></section>
</template>
<script setup lang="ts">
import type { usePlanDoc } from '../../composables/usePlanDoc';
defineProps<{ doc: ReturnType<typeof usePlanDoc>; scenarios: import('../../types').TaskScenario[] }>();
</script>
