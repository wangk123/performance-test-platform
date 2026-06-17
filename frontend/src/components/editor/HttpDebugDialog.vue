<template>
  <el-dialog v-model="visible" title="HTTP 调试结果" width="760px" destroy-on-close>
    <div v-if="result" class="http-debug-dialog">
      <div class="http-debug-summary">
        <el-tag :type="result.ok ? 'success' : 'danger'">{{ result.status ?? 'ERR' }}</el-tag>
        <strong>{{ result.method }} {{ result.url }}</strong>
        <span>{{ result.durationMs }}ms {{ result.statusText }}</span>
      </div>
      <el-alert v-if="result.error" :title="result.error" type="error" :closable="false" show-icon />
      <el-tabs model-value="response">
        <el-tab-pane label="响应体" name="response">
          <pre class="http-debug-pre">{{ result.responseBody || '无响应体' }}</pre>
        </el-tab-pane>
        <el-tab-pane label="响应头" name="responseHeaders">
          <pre class="http-debug-pre">{{ formatRecord(result.responseHeaders) }}</pre>
        </el-tab-pane>
        <el-tab-pane label="请求头" name="requestHeaders">
          <pre class="http-debug-pre">{{ formatRecord(result.requestHeaders) }}</pre>
        </el-tab-pane>
        <el-tab-pane label="请求体" name="requestBody">
          <pre class="http-debug-pre">{{ result.requestBody || '无请求体' }}</pre>
        </el-tab-pane>
      </el-tabs>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { HttpDebugResult } from '../../utils/http-debug';

const props = defineProps<{
  modelValue: boolean;
  result: HttpDebugResult | null;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void;
}>();

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
});

function formatRecord(record: Record<string, string>) {
  const entries = Object.entries(record);
  return entries.length ? entries.map(([key, value]) => `${key}: ${value}`).join('\n') : '无';
}
</script>
