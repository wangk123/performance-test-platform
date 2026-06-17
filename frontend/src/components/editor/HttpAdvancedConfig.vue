<template>
  <div class="step-config-grid">
    <a-form-item label="连接超时（ms）">
      <a-input-number
        :value="advanced.connectTimeout"
        :min="0"
        :step="1000"
        @update:value="emit('update', 'connectTimeout', $event)"
      />
    </a-form-item>
    <a-form-item label="响应超时（ms）">
      <a-input-number
        :value="advanced.responseTimeout"
        :min="0"
        :step="1000"
        @update:value="emit('update', 'responseTimeout', $event)"
      />
    </a-form-item>
    <a-form-item label="跟随重定向">
      <a-switch
        :checked="advanced.followRedirects"
        @update:checked="emit('update', 'followRedirects', $event)"
      />
    </a-form-item>
    <a-form-item label="Keep-Alive">
      <a-switch
        :checked="advanced.keepAlive"
        @update:checked="emit('update', 'keepAlive', $event)"
      />
    </a-form-item>
  </div>
</template>

<script setup lang="ts">
import type { HttpAdvancedConfig } from '../../types';

defineProps<{ advanced: HttpAdvancedConfig }>();

const emit = defineEmits<{
  update: [key: keyof HttpAdvancedConfig, value: number | boolean | null | undefined];
}>();
</script>
