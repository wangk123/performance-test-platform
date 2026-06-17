<template>
  <a-modal v-model:open="visible" title="HTTP 调试结果" width="760px" destroy-on-close>
    <div v-if="result" class="http-debug-dialog">
      <div class="http-debug-summary">
        <a-tag :color="result.ok ? 'success' : 'danger'">{{ result.status ?? 'ERR' }}</a-tag>
        <strong>{{ result.method }} {{ result.url }}</strong>
        <span>{{ result.durationMs }}ms {{ result.statusText }}</span>
      </div>
      <a-alert v-if="result.error" :title="result.error" type="error" :closable="false" show-icon />
      <a-tabs active-key="response">
        <a-tab-pane tab="响应体" key="response">
          <pre class="http-debug-pre">{{ result.responseBody || '无响应体' }}</pre>
        </a-tab-pane>
        <a-tab-pane tab="响应头" key="responseHeaders">
          <pre class="http-debug-pre">{{ formatRecord(result.responseHeaders) }}</pre>
        </a-tab-pane>
        <a-tab-pane tab="请求头" key="requestHeaders">
          <pre class="http-debug-pre">{{ formatRecord(result.requestHeaders) }}</pre>
        </a-tab-pane>
        <a-tab-pane tab="请求体" key="requestBody">
          <pre class="http-debug-pre">{{ result.requestBody || '无请求体' }}</pre>
        </a-tab-pane>
      </a-tabs>
    </div>
  </a-modal>
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
