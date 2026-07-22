<template>
  <div class="body-type-row">
    <a-radio-group :value="bodyType" @update:value="emit('updateBodyType', $event)">
      <a-radio value="none">none</a-radio>
      <a-radio value="form-data">form-data</a-radio>
      <a-radio value="form-urlencoded">x-www-form-urlencoded</a-radio>
      <a-radio value="raw">raw</a-radio>
    </a-radio-group>
    <div v-if="bodyType === 'raw'" class="raw-body-tools">
      <a-select
        class="raw-format-select"
        :value="resolvedRawBodyType"
        @update:value="emit('updateRawBodyType', $event)"
      >
        <a-select-option label="Text" value="text" />
        <a-select-option label="JavaScript" value="javascript" />
        <a-select-option label="JSON" value="json" />
        <a-select-option label="HTML" value="html" />
        <a-select-option label="XML" value="xml" />
      </a-select>
      <a-button @click="emit('formatBody')">格式化</a-button>
    </div>
  </div>

  <HttpKeyValueEditor
    v-if="bodyType === 'form-data' || bodyType === 'form-urlencoded'"
    kind="bodyParams"
    :items="bodyParams"
    key-placeholder="Key"
    value-placeholder="${value}"
    description-placeholder=""
    :show-description="false"
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

  <CodeEditor
    v-else-if="bodyType === 'raw'"
    ref="codeEditorRef"
    field-id="body"
    :model-value="body"
    :placeholder="bodyPlaceholder"
    :language="codeLanguage"
    @update:model-value="emit('updateBody', $event)"
    @blur="emit('formatBody')"
    @active="(id, caret, value) => emit('bodyActive', id, caret, value)"
  />
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import type { HttpBodyType, HttpParamConfig, HttpRawBodyType } from '../../types';
import type { ActiveVariableField, VariableOption } from '../../utils/http-request-config';
import HttpKeyValueEditor from './HttpKeyValueEditor.vue';
import CodeEditor from './CodeEditor.vue';

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

const codeEditorRef = ref<InstanceType<typeof CodeEditor> | null>(null);
const resolvedRawBodyType = computed(() => props.rawBodyType || 'json');
const codeLanguage = computed<'json' | 'xml' | 'html' | 'text'>(() => {
  if (resolvedRawBodyType.value === 'json' || resolvedRawBodyType.value === 'xml' || resolvedRawBodyType.value === 'html') {
    return resolvedRawBodyType.value;
  }
  return 'text';
});

const emit = defineEmits<{
  updateBodyType: [value: string | number | boolean | undefined];
  updateRawBodyType: [value: string | number | boolean | undefined];
  updateBody: [value: string];
  updateBodyParam: [index: number, field: keyof HttpParamConfig, value: string | boolean];
  removeBodyParam: [index: number];
  addBodyParam: [];
  active: [id: string, element: HTMLInputElement | HTMLTextAreaElement];
  bodyActive: [id: string, caret: number, value: string];
  choose: [key?: string];
  move: [offset: number];
  close: [];
  formatBody: [];
}>();

defineExpose({
  insertAtCursor(text: string, replaceFrom?: number) {
    codeEditorRef.value?.insertAtCursor(text, replaceFrom);
  },
});
</script>
