<template>
  <nav class="settings-tabs" aria-label="配置分区">
    <button
      v-for="option in options"
      :key="option.value"
      class="settings-tab"
      :class="{ active: option.value === modelValue }"
      type="button"
      @click="emit('update:modelValue', option.value)"
    >
      {{ option.label }}
    </button>
  </nav>
</template>

<script setup lang="ts">
defineProps<{
  options: Array<{ label: string; value: string }>;
  modelValue: string;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: string];
}>();
</script>
