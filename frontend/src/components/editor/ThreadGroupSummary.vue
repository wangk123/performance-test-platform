<template>
  <span class="thread-group-summary">
    <template v-if="threadGroup.mode === 'stepping'">
      阶梯 · {{ threadGroup.threads }} 线程 · 每阶 {{ threadGroup.stepping?.startUsersCount ?? '-' }} 用户 · 保持 {{ threadGroup.stepping?.flightTime ?? '-' }}s
    </template>
    <template v-else-if="threadGroup.mode === 'duration' || threadGroup.scheduler">
      {{ threadGroup.threads }} 线程 · Ramp {{ threadGroup.rampUp }}s · 持续 {{ threadGroup.duration }}s
    </template>
    <template v-else>
      {{ threadGroup.threads }} 线程 · Ramp {{ threadGroup.rampUp }}s · 循环 {{ threadGroup.loops }} 次
    </template>
  </span>
</template>

<script setup lang="ts">
import type { ThreadGroup } from '../../types';

defineProps<{
  threadGroup: ThreadGroup;
}>();
</script>
