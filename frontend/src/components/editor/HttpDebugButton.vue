<template>
  <div class="http-debug-action">
    <a-button type="primary" :loading="debugging" @click="debugRequest">调试</a-button>
    <HttpDebugDialog v-model="debugDialogVisible" :result="debugResult" />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { message } from 'ant-design-vue';
import type { HttpRequestConfig } from '../../types';
import { executeHttpDebug, type HttpDebugResult } from '../../utils/http-debug';
import type { VariableOption } from '../../utils/http-request-config';
import HttpDebugDialog from './HttpDebugDialog.vue';

const props = defineProps<{
  config: HttpRequestConfig;
  variables: VariableOption[];
}>();

const debugDialogVisible = ref(false);
const debugging = ref(false);
const debugResult = ref<HttpDebugResult | null>(null);

async function debugRequest() {
  debugging.value = true;
  try {
    debugResult.value = await executeHttpDebug(props.config, props.variables);
    debugDialogVisible.value = true;
  } catch (error) {
    message.error(error instanceof Error ? error.message : '调试请求失败');
  } finally {
    debugging.value = false;
  }
}
</script>
