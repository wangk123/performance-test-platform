<template>
  <div class="kv-table" :class="{ 'kv-table--compact': !showDescription }">
    <div class="kv-header">
      <span class="kv-col-check"></span>
      <span class="kv-col-key">Key</span>
      <span class="kv-col-value">Value</span>
      <span v-if="showDescription" class="kv-col-desc">描述</span>
      <span class="kv-col-action"></span>
    </div>

    <div
      v-for="(item, index) in items"
      :key="`${kind}-${index}`"
      class="kv-row"
      :class="{ 'is-disabled': !item.enabled }"
    >
      <label class="kv-col-check">
        <input
          type="checkbox"
          class="kv-checkbox"
          :checked="item.enabled"
          @change="emit('update', index, 'enabled', ($event.target as HTMLInputElement).checked)"
        />
      </label>
      <div class="kv-col-key">
        <VariableField
          :id="`${kind}.${index}.key`"
          :value="item.key"
          :placeholder="keyPlaceholder"
          :active-field="activeField"
          :active-index="activeIndex"
          :suggestions="suggestions"
          plain
          @change="emit('update', index, 'key', $event)"
          @active="(id, element) => emit('active', id, element)"
          @choose="emit('choose', $event)"
          @move="emit('move', $event)"
          @close="emit('close')"
        />
      </div>
      <div class="kv-col-value">
        <VariableField
          :id="`${kind}.${index}.value`"
          :value="item.value"
          :placeholder="valuePlaceholder"
          :active-field="activeField"
          :active-index="activeIndex"
          :suggestions="suggestions"
          plain
          @change="emit('update', index, 'value', $event)"
          @active="(id, element) => emit('active', id, element)"
          @choose="emit('choose', $event)"
          @move="emit('move', $event)"
          @close="emit('close')"
        />
      </div>
      <div v-if="showDescription" class="kv-col-desc">
        <input
          class="kv-plain-input"
          :value="item.description"
          @input="emit('update', index, 'description', ($event.target as HTMLInputElement).value)"
        />
      </div>
      <button type="button" class="kv-remove" title="删除" @click="emit('remove', index)">
        <svg viewBox="0 0 16 16" aria-hidden="true">
          <path
            d="M5.5 2h5l.5 1H13v1H3V3h1.5l.5-1zm-1 3h7l-.6 8.1a1 1 0 0 1-1 .9H7.1a1 1 0 0 1-1-.9L5.5 5z"
            fill="currentColor"
          />
        </svg>
      </button>
    </div>

    <div class="kv-row kv-row--ghost">
      <label class="kv-col-check">
        <input
          type="checkbox"
          class="kv-checkbox"
          :checked="ghostRow.enabled"
          disabled
          tabindex="-1"
        />
      </label>
      <div class="kv-col-key">
        <VariableField
          :id="`${kind}.ghost.key`"
          :value="ghostRow.key"
          :active-field="activeField"
          :active-index="activeIndex"
          :suggestions="suggestions"
          plain
          @change="onGhostKeyChange"
          @active="(id, element) => emit('active', id, element)"
          @choose="emit('choose', $event)"
          @move="emit('move', $event)"
          @close="emit('close')"
          @blur="commitGhostRow"
        />
      </div>
      <div class="kv-col-value">
        <VariableField
          :id="`${kind}.ghost.value`"
          :value="ghostRow.value"
          :active-field="activeField"
          :active-index="activeIndex"
          :suggestions="suggestions"
          plain
          @change="onGhostValueChange"
          @active="(id, element) => emit('active', id, element)"
          @choose="emit('choose', $event)"
          @move="emit('move', $event)"
          @close="emit('close')"
          @blur="commitGhostRow"
        />
      </div>
      <div v-if="showDescription" class="kv-col-desc">
        <input
          class="kv-plain-input"
          :value="ghostRow.description"
          @input="onGhostDescriptionChange"
          @blur="commitGhostRow"
        />
      </div>
      <span class="kv-col-action"></span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, ref } from 'vue';
import type { HttpParamConfig } from '../../types';
import type { ActiveVariableField, VariableOption } from '../../utils/http-request-config';
import VariableField from './VariableField.vue';

const props = withDefaults(
  defineProps<{
    kind: string;
    items: HttpParamConfig[];
    keyPlaceholder: string;
    valuePlaceholder: string;
    descriptionPlaceholder: string;
    showDescription?: boolean;
    activeField: ActiveVariableField | null;
    activeIndex: number;
    suggestions: VariableOption[];
  }>(),
  { showDescription: true },
);

const emit = defineEmits<{
  update: [index: number, field: keyof HttpParamConfig, value: string | boolean];
  remove: [index: number];
  add: [];
  active: [id: string, element: HTMLInputElement | HTMLTextAreaElement];
  choose: [key?: string];
  move: [offset: number];
  close: [];
}>();

const ghostRow = ref(createGhostRow());

function createGhostRow(): HttpParamConfig {
  return { enabled: false, key: '', value: '', description: '' };
}

function syncGhostEnabled() {
  ghostRow.value.enabled = Boolean(ghostRow.value.key.trim() || ghostRow.value.value.trim());
}

function onGhostKeyChange(value: string) {
  ghostRow.value.key = value;
  syncGhostEnabled();
}

function onGhostValueChange(value: string) {
  ghostRow.value.value = value;
  syncGhostEnabled();
}

function onGhostDescriptionChange(event: Event) {
  ghostRow.value.description = (event.target as HTMLInputElement).value;
}

function commitGhostRow() {
  const { key, value, description } = ghostRow.value;
  if (!key.trim() && !value.trim()) {
    return;
  }
  const index = props.items.length;
  emit('add');
  nextTick(() => {
    emit('update', index, 'key', key);
    emit('update', index, 'value', value);
    emit('update', index, 'description', description);
    emit('update', index, 'enabled', true);
    ghostRow.value = createGhostRow();
  });
}
</script>
