<template>
  <el-form-item label="调试">
    <el-button type="primary" plain :loading="debugging" @click="debugRequest">调试</el-button>
    <HttpDebugDialog v-model="debugDialogVisible" :result="debugResult" />
  </el-form-item>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { ElMessage } from 'element-plus';
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
    ElMessage.error(error instanceof Error ? error.message : '调试请求失败');
  } finally {
    debugging.value = false;
  }
}
</script>
