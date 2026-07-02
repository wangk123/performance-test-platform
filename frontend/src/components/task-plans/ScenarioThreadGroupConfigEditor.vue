<template>
  <div class="tg-config-editor">
    <div v-if="!scriptVersionId" class="tg-config-hint">请先选择脚本。</div>
    <div v-else-if="threadGroupOptions.length === 0" class="tg-config-hint">当前脚本没有 Thread Group。</div>
    <template v-else>
      <div v-for="(row, index) in rows" :key="index" class="tg-config-row">
        <div class="tg-config-row-head">
          <strong>配置 {{ index + 1 }}</strong>
          <a-button type="link" danger @click="removeRow(index)">删除</a-button>
        </div>
        <div class="tg-config-grid">
          <a-form-item label="Thread Group">
            <a-select
              :value="row.stepId"
              :options="threadGroupOptions.map((item) => ({ value: item.id, label: item.name }))"
              @update:value="(value: string) => updateStep(index, value)"
            />
          </a-form-item>
          <a-form-item label="线程数">
            <a-input-number :value="row.threads" :min="1" @update:value="(v: number | null) => updateField(index, 'threads', v ?? 1)" />
          </a-form-item>
          <a-form-item label="Ramp-Up（秒）">
            <a-input-number :value="row.rampUp" :min="0" @update:value="(v: number | null) => updateField(index, 'rampUp', v ?? 0)" />
          </a-form-item>
          <a-form-item label="执行时间（秒）">
            <a-input-number :value="row.duration" :min="1" @update:value="(v: number | null) => updateField(index, 'duration', v ?? 1)" />
          </a-form-item>
        </div>
      </div>
      <a-button @click="addRow">+ 添加配置</a-button>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { ScenarioThreadGroupConfig } from '../../types';
import { getScriptDefinitionApi } from '../../api/scripts';
import { emptyThreadGroupConfig, listThreadGroupSteps } from '../../utils/scenario-thread-group';

const props = defineProps<{
  projectId: number;
  scriptVersionId: number | null;
  modelValue: ScenarioThreadGroupConfig[];
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: ScenarioThreadGroupConfig[]): void;
}>();

const threadGroupOptions = ref<{ id: string; name: string }[]>([]);

const rows = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
});

watch(
  () => [props.projectId, props.scriptVersionId] as const,
  async ([projectId, scriptVersionId]) => {
    threadGroupOptions.value = [];
    if (!projectId || !scriptVersionId) return;
    try {
      const definition = await getScriptDefinitionApi(projectId, scriptVersionId);
      threadGroupOptions.value = listThreadGroupSteps(definition.steps);
    } catch {
      threadGroupOptions.value = [];
    }
  },
  { immediate: true },
);

function addRow() {
  const first = threadGroupOptions.value[0];
  const next = emptyThreadGroupConfig(first?.id ?? '', first?.name ?? '');
  rows.value = [...rows.value, { ...next, sortOrder: rows.value.length }];
}

function removeRow(index: number) {
  rows.value = rows.value.filter((_, i) => i !== index).map((item, sortOrder) => ({ ...item, sortOrder }));
}

function updateStep(index: number, stepId: string) {
  const step = threadGroupOptions.value.find((item) => item.id === stepId);
  rows.value = rows.value.map((item, i) => (
    i === index ? { ...item, stepId, stepName: step?.name ?? item.stepName } : item
  ));
}

function updateField(index: number, field: 'threads' | 'rampUp' | 'duration', value: number) {
  rows.value = rows.value.map((item, i) => (i === index ? { ...item, [field]: value } : item));
}
</script>

<style scoped>
.tg-config-editor {
  display: grid;
  gap: 12px;
}

.tg-config-hint {
  color: var(--muted);
  font-size: 13px;
}

.tg-config-row {
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 12px;
  background: var(--surface-soft);
}

.tg-config-row-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.tg-config-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 12px;
}

@media (min-width: 720px) {
  .tg-config-grid {
    grid-template-columns: 1.2fr repeat(3, minmax(0, 1fr));
  }
}
</style>
