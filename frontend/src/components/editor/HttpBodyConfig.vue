<template>
  <div class="body-type-row">
    <el-radio-group :model-value="bodyType" @update:model-value="emit('updateBodyType', $event)">
      <el-radio value="none">none</el-radio>
      <el-radio value="form-data">form-data</el-radio>
      <el-radio value="form-urlencoded">x-www-form-urlencoded</el-radio>
      <el-radio value="raw">raw</el-radio>
    </el-radio-group>
    <el-select
      v-if="bodyType === 'raw'"
      class="raw-format-select"
      :model-value="resolvedRawBodyType"
      @update:model-value="emit('updateRawBodyType', $event)"
    >
      <el-option label="Text" value="text" />
      <el-option label="JavaScript" value="javascript" />
      <el-option label="JSON" value="json" />
      <el-option label="HTML" value="html" />
      <el-option label="XML" value="xml" />
    </el-select>
  </div>

  <HttpKeyValueEditor
    v-if="bodyType === 'form-data' || bodyType === 'form-urlencoded'"
    kind="bodyParams"
    :items="bodyParams"
    key-placeholder="Key"
    value-placeholder="${value}"
    description-placeholder="参数说明"
    add-text="新增 Body 参数"
    :active-field="activeField"
    :active-index="activeIndex"
    :suggestions="suggestions"
    @update="(index, field, value) => emit('updateBodyParam', index, field, value)"
    @remove="emit('removeBodyParam', $event)"
    @add="emit('addBodyParam')"
    @active="(id, element) => emit('active', id, element)"
    @choose="emit('choose', $event)"
    @move="emit('move', $event)"
    @close="emit('close')"
  />

  <VariableField
    v-else-if="bodyType === 'raw'"
    id="body"
    :value="body"
    :placeholder="bodyPlaceholder"
    multiline
    :active-field="activeField"
    :active-index="activeIndex"
    :suggestions="suggestions"
    :highlight-mode="resolvedRawBodyType"
    @change="emit('updateBody', $event)"
    @active="(id, element) => emit('active', id, element)"
    @choose="emit('choose', $event)"
    @move="emit('move', $event)"
    @close="emit('close')"
    @blur="emit('formatBody')"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { HttpBodyType, HttpParamConfig, HttpRawBodyType } from '../../types';
import type { ActiveVariableField, VariableOption } from '../../utils/http-request-config';
import HttpKeyValueEditor from './HttpKeyValueEditor.vue';
import VariableField from './VariableField.vue';

const props = defineProps<{
  bodyType: HttpBodyType;
  rawBodyType: HttpRawBodyType;
  body: string;
  bodyParams: HttpParamConfig[];
  bodyPlaceholder: string;
  activeField: ActiveVariableField | null;
  activeIndex: number;
  suggestions: VariableOption[];
}>();

const resolvedRawBodyType = computed(() => props.rawBodyType || 'json');

const emit = defineEmits<{
  updateBodyType: [value: string | number | boolean | undefined];
  updateRawBodyType: [value: string | number | boolean | undefined];
  updateBody: [value: string];
  updateBodyParam: [index: number, field: keyof HttpParamConfig, value: string | boolean];
  removeBodyParam: [index: number];
  addBodyParam: [];
  active: [id: string, element: HTMLInputElement | HTMLTextAreaElement];
  choose: [key?: string];
  move: [offset: number];
  close: [];
  formatBody: [];
}>();
</script>
