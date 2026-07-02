<template>
  <div class="tg-config-editor">
    <div v-if="!scriptVersionId" class="tg-config-hint">请先选择脚本。</div>
    <div v-else-if="threadGroupOptions.length === 0" class="tg-config-hint">当前脚本没有 Thread Group。</div>
    <template v-else>
      <div v-for="(group, groupIndex) in presetGroups" :key="group.sortOrder" class="tg-preset-card">
        <div class="tg-preset-head">
          <strong>配置 {{ groupIndex + 1 }}</strong>
          <a-button
            v-if="presetGroups.length > 1"
            type="link"
            danger
            @click="removePreset(groupIndex)"
          >
            删除
          </a-button>
        </div>
        <div class="tg-preset-table-wrap">
          <table class="tg-preset-table">
            <thead>
              <tr>
                <th>Thread Group</th>
                <th>线程数</th>
                <th>Ramp-Up（秒）</th>
                <th>执行时间（秒）</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, rowIndex) in group.rows" :key="row.stepId">
                <td class="tg-preset-name">{{ row.stepName }}</td>
                <td>
                  <a-input-number
                    :value="row.threads"
                    :min="1"
                    size="small"
                    @update:value="(v: number | null) => updateField(groupIndex, rowIndex, 'threads', v ?? 1)"
                  />
                </td>
                <td>
                  <a-input-number
                    :value="row.rampUp"
                    :min="0"
                    size="small"
                    @update:value="(v: number | null) => updateField(groupIndex, rowIndex, 'rampUp', v ?? 0)"
                  />
                </td>
                <td>
                  <a-input-number
                    :value="row.duration"
                    :min="1"
                    size="small"
                    @update:value="(v: number | null) => updateField(groupIndex, rowIndex, 'duration', v ?? 1)"
                  />
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
      <a-button class="tg-preset-add" @click="addPreset">+ 添加配置</a-button>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import type { ScenarioThreadGroupConfig } from '../../types';
import { getScriptDefinitionApi } from '../../api/scripts';
import {
  createDefaultPreset,
  flattenThreadGroupPresets,
  groupThreadGroupConfigs,
  listThreadGroupSteps,
  type ThreadGroupPresetGroup,
  type ThreadGroupStepOption,
} from '../../utils/scenario-thread-group';

const props = defineProps<{
  projectId: number;
  scriptVersionId: number | null;
  modelValue: ScenarioThreadGroupConfig[];
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: ScenarioThreadGroupConfig[]): void;
}>();

const threadGroupOptions = ref<ThreadGroupStepOption[]>([]);
const presetGroups = ref<ThreadGroupPresetGroup[]>([]);
let syncing = false;

function emitFlattened() {
  syncing = true;
  emit('update:modelValue', flattenThreadGroupPresets(presetGroups.value));
  queueMicrotask(() => {
    syncing = false;
  });
}

function syncPresetGroupsFromModel() {
  const grouped = groupThreadGroupConfigs(props.modelValue, threadGroupOptions.value);
  presetGroups.value = grouped.length > 0
    ? grouped
    : [createDefaultPreset(threadGroupOptions.value, 0)];
}

watch(
  () => [props.projectId, props.scriptVersionId] as const,
  async ([projectId, scriptVersionId]) => {
    threadGroupOptions.value = [];
    if (!projectId || !scriptVersionId) {
      presetGroups.value = [];
      return;
    }
    try {
      const definition = await getScriptDefinitionApi(projectId, scriptVersionId);
      threadGroupOptions.value = listThreadGroupSteps(definition.steps);
      syncPresetGroupsFromModel();
      if (props.modelValue.length === 0) {
        emitFlattened();
      }
    } catch {
      threadGroupOptions.value = [];
      presetGroups.value = [];
    }
  },
  { immediate: true },
);

watch(
  () => props.modelValue,
  () => {
    if (syncing || threadGroupOptions.value.length === 0) return;
    syncPresetGroupsFromModel();
  },
);

function addPreset() {
  const nextOrder = presetGroups.value.reduce((max, group) => Math.max(max, group.sortOrder), -1) + 1;
  presetGroups.value = [
    ...presetGroups.value,
    createDefaultPreset(threadGroupOptions.value, nextOrder),
  ];
  emitFlattened();
}

function removePreset(index: number) {
  presetGroups.value = presetGroups.value.filter((_, i) => i !== index);
  emitFlattened();
}

function updateField(
  groupIndex: number,
  rowIndex: number,
  field: 'threads' | 'rampUp' | 'duration',
  value: number,
) {
  presetGroups.value = presetGroups.value.map((group, gi) => (
    gi === groupIndex
      ? {
          ...group,
          rows: group.rows.map((row, ri) => (ri === rowIndex ? { ...row, [field]: value } : row)),
        }
      : group
  ));
  emitFlattened();
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

.tg-preset-card {
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--surface-soft);
  overflow: hidden;
}

.tg-preset-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid var(--border);
  background: var(--surface);
}

.tg-preset-table-wrap {
  overflow-x: auto;
}

.tg-preset-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.tg-preset-table th,
.tg-preset-table td {
  padding: 10px 12px;
  border-bottom: 1px solid var(--border);
  text-align: left;
  vertical-align: middle;
}

.tg-preset-table th {
  color: var(--muted);
  font-weight: 500;
  background: rgba(0, 0, 0, 0.02);
  white-space: nowrap;
}

.tg-preset-table tbody tr:last-child td {
  border-bottom: 0;
}

.tg-preset-name {
  min-width: 140px;
  font-weight: 500;
}

.tg-preset-table :deep(.ant-input-number) {
  width: 100%;
  min-width: 88px;
}

.tg-preset-add {
  align-self: start;
}
</style>
