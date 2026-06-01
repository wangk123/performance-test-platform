<template>
  <span class="step-type-icon" :class="`tone-${meta.tone}`" :title="meta.label">
    <svg
      v-if="props.type === 'THREAD_GROUP'"
      viewBox="0 0 20 20"
      fill="none"
      aria-hidden="true"
    >
      <path d="M3 5h14M3 10h14M3 15h14" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
      <circle cx="6" cy="5" r="1.6" fill="currentColor" />
      <circle cx="11" cy="10" r="1.6" fill="currentColor" />
      <circle cx="14" cy="15" r="1.6" fill="currentColor" />
    </svg>

    <svg
      v-else-if="props.type === 'HTTP_REQUEST'"
      viewBox="0 0 20 20"
      fill="none"
      aria-hidden="true"
    >
      <circle cx="10" cy="10" r="7.2" stroke="currentColor" stroke-width="1.7" />
      <path d="M3 10h14" stroke="currentColor" stroke-width="1.7" />
      <path d="M10 3c2.4 2 3.6 4.6 3.6 7s-1.2 5-3.6 7c-2.4-2-3.6-4.6-3.6-7s1.2-5 3.6-7Z" stroke="currentColor" stroke-width="1.7" />
    </svg>

    <svg
      v-else-if="props.type === 'ASSERTION'"
      viewBox="0 0 20 20"
      fill="none"
      aria-hidden="true"
    >
      <path d="M10 2.5 16 5v5.2c0 4.2-3 6.5-6 7.3-3-.8-6-3.1-6-7.3V5l6-2.5Z" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round" />
      <path d="m7.2 10.4 2 2 3.6-4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
    </svg>

    <svg
      v-else-if="props.type === 'CSV_DATA'"
      viewBox="0 0 20 20"
      fill="none"
      aria-hidden="true"
    >
      <rect x="3.2" y="4" width="13.6" height="12" rx="2" stroke="currentColor" stroke-width="1.6" />
      <path d="M3.2 8.4h13.6M3.2 12h13.6M8.6 4v12M13.4 4v12" stroke="currentColor" stroke-width="1.4" />
    </svg>

    <svg
      v-else-if="props.type === 'USER_PARAMS'"
      viewBox="0 0 20 20"
      fill="none"
      aria-hidden="true"
    >
      <path d="M7.4 3.2c-2 .5-2.6 1.6-2.6 3v2.4c0 .9-.4 1.4-1.6 1.4 1.2 0 1.6.5 1.6 1.4v2.4c0 1.4.6 2.5 2.6 3" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
      <path d="M12.6 3.2c2 .5 2.6 1.6 2.6 3v2.4c0 .9.4 1.4 1.6 1.4-1.2 0-1.6.5-1.6 1.4v2.4c0 1.4-.6 2.5-2.6 3" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
      <circle cx="10" cy="10" r="1.4" fill="currentColor" />
    </svg>

    <svg
      v-else-if="props.type === 'HEADER_CONFIG'"
      viewBox="0 0 20 20"
      fill="none"
      aria-hidden="true"
    >
      <rect x="3" y="4.5" width="14" height="11" rx="2" stroke="currentColor" stroke-width="1.6" />
      <path d="M3 8.5h14" stroke="currentColor" stroke-width="1.6" />
      <circle cx="5.6" cy="6.5" r="0.7" fill="currentColor" />
      <circle cx="7.8" cy="6.5" r="0.7" fill="currentColor" />
      <path d="M6 12h5M6 14h8" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" />
    </svg>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { ScriptStepType } from '../../types';
import { stepTypeMeta } from '../../constants';

const props = defineProps<{
  type: ScriptStepType;
}>();

const meta = computed(() => stepTypeMeta[props.type]);
</script>
