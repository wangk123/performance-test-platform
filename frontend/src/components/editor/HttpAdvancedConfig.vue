<template>
  <div class="step-config-grid">
    <el-form-item label="连接超时（ms）">
      <el-input-number
        :model-value="advanced.connectTimeout"
        :min="0"
        :step="1000"
        controls-position="right"
        @update:model-value="emit('update', 'connectTimeout', $event)"
      />
    </el-form-item>
    <el-form-item label="响应超时（ms）">
      <el-input-number
        :model-value="advanced.responseTimeout"
        :min="0"
        :step="1000"
        controls-position="right"
        @update:model-value="emit('update', 'responseTimeout', $event)"
      />
    </el-form-item>
    <el-form-item label="跟随重定向">
      <el-switch
        :model-value="advanced.followRedirects"
        @update:model-value="emit('update', 'followRedirects', $event)"
      />
    </el-form-item>
    <el-form-item label="Keep-Alive">
      <el-switch
        :model-value="advanced.keepAlive"
        @update:model-value="emit('update', 'keepAlive', $event)"
      />
    </el-form-item>
  </div>
</template>

<script setup lang="ts">
import type { HttpAdvancedConfig } from '../../types';

defineProps<{ advanced: HttpAdvancedConfig }>();

const emit = defineEmits<{
  update: [key: keyof HttpAdvancedConfig, value: number | boolean | null | undefined];
}>();
</script>
