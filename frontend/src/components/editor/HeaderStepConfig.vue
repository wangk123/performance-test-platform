<template>
  <div class="header-step-config">
    <HttpKeyValueEditor
      kind="header-step"
      :items="items"
      key-label="Header 名称"
      value-label="值"
      key-placeholder="Header 名称"
      value-placeholder=""
      description-placeholder="Header 说明"
      :show-description="false"
      :active-field="null"
      :active-index="0"
      :suggestions="[]"
      @update="updateItem"
      @remove="removeItem"
      @add="addItem"
    />

    <div v-if="items.length === 0" class="header-presets">
      <span class="header-presets-label">常用 Header，点击插入：</span>
      <button
        v-for="preset in presets"
        :key="preset.key"
        type="button"
        class="header-preset"
        @click="addPreset(preset)"
      >
        <b>{{ preset.key }}</b><span>: {{ preset.value }}</span>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { HttpParamConfig, ScriptStep } from '../../types';
import { createEmptyHttpParam } from '../../utils/http-request-config';
import HttpKeyValueEditor from './HttpKeyValueEditor.vue';

const props = defineProps<{
  step: ScriptStep;
}>();

const presets = [
  { key: 'Content-Type', value: 'application/json' },
  { key: 'Authorization', value: 'Bearer ${token}' },
  { key: 'Accept', value: 'application/json' },
];

// 旧数据只有 headersText（k: v 文本）：读取时转换，保存落结构化数组
const items = computed<HttpParamConfig[]>(() => {
  const raw = props.step.config.headers;
  if (Array.isArray(raw)) {
    return (raw as Array<Record<string, unknown>>).map((item) => ({
      enabled: item.enabled !== false,
      key: String(item.key ?? ''),
      value: String(item.value ?? ''),
      description: String(item.description ?? ''),
    }));
  }
  const text = typeof props.step.config.headersText === 'string' ? props.step.config.headersText : '';
  return text
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const separator = line.indexOf(':') >= 0 ? line.indexOf(':') : line.indexOf('=');
      return separator > 0
        ? { enabled: true, key: line.substring(0, separator).trim(), value: line.substring(separator + 1).trim(), description: '' }
        : { enabled: true, key: line, value: '', description: '' };
    });
});

function commit(next: HttpParamConfig[]) {
  props.step.config = { ...props.step.config, headers: next, headersText: undefined };
}

function updateItem(index: number, field: keyof HttpParamConfig, value: string | boolean) {
  commit(items.value.map((item, itemIndex) => (itemIndex === index ? { ...item, [field]: value } : item)));
}

function removeItem(index: number) {
  commit(items.value.filter((_, itemIndex) => itemIndex !== index));
}

function addItem() {
  commit([...items.value, createEmptyHttpParam()]);
}

function addPreset(preset: { key: string; value: string }) {
  commit([...items.value, { enabled: true, key: preset.key, value: preset.value, description: '' }]);
}
</script>

<style scoped>
.header-presets {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
  padding: 10px 12px;
  border: 1px dashed var(--border-strong, #c8d3dc);
  border-radius: var(--radius-sm, 6px);
  font-size: 12.5px;
  color: var(--muted, #5c6b7a);
}

.header-presets-label {
  margin-right: 2px;
}

.header-preset {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border: 1px solid var(--border-strong, #c8d3dc);
  border-radius: 999px;
  background: var(--surface, #fff);
  font-size: 12px;
  font-family: var(--font-data, monospace);
  color: var(--text, #1a2332);
  cursor: pointer;
}

.header-preset b {
  font-weight: 600;
}

.header-preset:hover {
  border-color: var(--primary, #0b7f8a);
  color: var(--primary, #0b7f8a);
}
</style>
