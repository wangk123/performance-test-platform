<template>
  <div class="kv-section">
    <div class="kv-header">
      <span>启用</span>
      <span>Key</span>
      <span>Value</span>
      <span>描述</span>
      <span></span>
    </div>
    <div v-for="(item, index) in items" :key="`${kind}-${index}`" class="kv-row">
      <a-switch
        :checked="item.enabled"
        size="small"
        @update:checked="emit('update', index, 'enabled', $event)"
      />
      <VariableField
        :id="`${kind}.${index}.key`"
        :value="item.key"
        :placeholder="keyPlaceholder"
        :active-field="activeField"
        :active-index="activeIndex"
        :suggestions="suggestions"
        @change="emit('update', index, 'key', $event)"
        @active="(id, element) => emit('active', id, element)"
        @choose="emit('choose', $event)"
        @move="emit('move', $event)"
        @close="emit('close')"
      />
      <VariableField
        :id="`${kind}.${index}.value`"
        :value="item.value"
        :placeholder="valuePlaceholder"
        :active-field="activeField"
        :active-index="activeIndex"
        :suggestions="suggestions"
        @change="emit('update', index, 'value', $event)"
        @active="(id, element) => emit('active', id, element)"
        @choose="emit('choose', $event)"
        @move="emit('move', $event)"
        @close="emit('close')"
      />
      <a-input
        :value="item.description"
        :placeholder="descriptionPlaceholder"
        @update:value="emit('update', index, 'description', $event)"
      />
      <a-button type="text" danger @click="emit('remove', index)">删除</a-button>
    </div>
    <a-button class="kv-add" @click="emit('add')">{{ addText }}</a-button>
  </div>
</template>

<script setup lang="ts">
import type { HttpParamConfig } from '../../types';
import type { ActiveVariableField, VariableOption } from '../../utils/http-request-config';
import VariableField from './VariableField.vue';

defineProps<{
  kind: string;
  items: HttpParamConfig[];
  keyPlaceholder: string;
  valuePlaceholder: string;
  descriptionPlaceholder: string;
  addText: string;
  activeField: ActiveVariableField | null;
  activeIndex: number;
  suggestions: VariableOption[];
}>();

const emit = defineEmits<{
  update: [index: number, field: keyof HttpParamConfig, value: string | boolean];
  remove: [index: number];
  add: [];
  active: [id: string, element: HTMLInputElement | HTMLTextAreaElement];
  choose: [key?: string];
  move: [offset: number];
  close: [];
}>();
</script>
