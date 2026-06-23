<template>
  <a-modal v-model:open="visible" title="HTTP 调试结果" width="760px" destroy-on-close>
    <div v-if="result" class="http-debug-dialog">
      <div class="http-debug-summary">
        <a-tag :color="result.ok ? 'success' : 'danger'">{{ result.status ?? 'ERR' }}</a-tag>
        <strong>{{ result.method }} {{ result.url }}</strong>
        <span>{{ result.durationMs }}ms {{ result.statusText }}</span>
      </div>
      <a-alert v-if="result.error" :title="result.error" type="error" :closable="false" show-icon />
      <a-tabs v-model:activeKey="activeTab">
        <a-tab-pane tab="响应体" key="response">
          <CodeEditor
            class="http-debug-code-editor"
            :model-value="responseBodyText"
            :language="responseBodyLanguage"
            placeholder="无响应体"
            readonly
          />
        </a-tab-pane>
        <a-tab-pane tab="响应头" key="responseHeaders">
          <pre class="http-debug-pre">{{ formatRecord(result.responseHeaders) }}</pre>
        </a-tab-pane>
        <a-tab-pane tab="请求头" key="requestHeaders">
          <pre class="http-debug-pre">{{ formatRecord(result.requestHeaders) }}</pre>
        </a-tab-pane>
        <a-tab-pane tab="请求体" key="requestBody">
          <CodeEditor
            class="http-debug-code-editor"
            :model-value="requestBodyText"
            :language="requestBodyLanguage"
            placeholder="无请求体"
            readonly
          />
        </a-tab-pane>
      </a-tabs>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import type { HttpDebugResult } from '../../utils/http-debug';
import { detectHttpBodyLanguage, formatHttpBodyAuto } from '../../utils/http-request-config';
import CodeEditor from './CodeEditor.vue';

const props = defineProps<{
  modelValue: boolean;
  result: HttpDebugResult | null;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void;
}>();

const activeTab = ref('response');

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
});

const responseBodyText = computed(() => formatHttpBodyAuto(props.result?.responseBody ?? ''));
const responseBodyLanguage = computed(() => detectHttpBodyLanguage(props.result?.responseBody ?? ''));
const requestBodyText = computed(() => formatHttpBodyAuto(props.result?.requestBody ?? ''));
const requestBodyLanguage = computed(() => detectHttpBodyLanguage(props.result?.requestBody ?? ''));

function formatRecord(record: Record<string, string>) {
  const entries = Object.entries(record);
  return entries.length ? entries.map(([key, value]) => `${key}: ${value}`).join('\n') : '无';
}
</script>
