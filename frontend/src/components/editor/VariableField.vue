<template>
  <div
    class="variable-field"
    :class="{
      'is-multiline': multiline,
      'is-disabled': disabled,
      'is-empty': !value,
    }"
  >
    <div
      class="variable-highlight-layer"
      v-html="value ? highlightContent(value, highlightMode) : ''"
    />
    <textarea
      v-if="multiline"
      class="variable-control"
      :value="value"
      :placeholder="placeholder"
      :disabled="disabled"
      rows="8"
      @input="handleInput"
      @focus="handleActive"
      @click="handleActive"
      @keyup="handleActive"
      @keydown="handleKeydown"
      @scroll="syncHighlightScroll"
      @blur="emit('blur')"
    />
    <input
      v-else
      class="variable-control"
      :value="value"
      :placeholder="placeholder"
      :disabled="disabled"
      @input="handleInput"
      @focus="handleActive"
      @click="handleActive"
      @keyup="handleActive"
      @keydown="handleKeydown"
      @blur="emit('blur')"
    />

    <Teleport to="body">
      <div v-if="showSuggestions" class="variable-suggest is-floating" :style="suggestionStyle">
      <button
        v-for="(item, index) in suggestions"
        :key="item.key"
        type="button"
        :class="{ active: index === activeIndex }"
        @mousedown.prevent="emit('choose', item.key)"
      >
        <strong>{{ placeholderOf(item.key) }}</strong>
        <span>{{ item.label }}</span>
      </button>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import type { ActiveVariableField, VariableOption } from '../../utils/http-request-config';
import { placeholderOf } from '../../utils/http-request-config';
import { highlightContent } from '../../utils/syntax-highlight';

const props = defineProps<{
  id: string;
  value: string;
  placeholder?: string;
  multiline?: boolean;
  disabled?: boolean;
  activeField: ActiveVariableField | null;
  activeIndex: number;
  suggestions: VariableOption[];
  highlightMode?: string;
}>();

const emit = defineEmits<{
  change: [value: string];
  active: [id: string, element: HTMLInputElement | HTMLTextAreaElement];
  choose: [key?: string];
  move: [offset: number];
  close: [];
  blur: [];
}>();

const showSuggestions = computed(
  () => props.activeField?.id === props.id && props.activeField.suggesting && props.suggestions.length > 0,
);
const suggestionStyle = ref<Record<string, string>>({});

function handleInput(event: Event) {
  const element = event.target as HTMLInputElement | HTMLTextAreaElement;
  emit('change', element.value);
  updateSuggestionPosition(element);
  emit('active', props.id, element);
}

function handleActive(event: Event) {
  const element = event.target as HTMLInputElement | HTMLTextAreaElement;
  updateSuggestionPosition(element);
  emit('active', props.id, element);
}

function handleKeydown(event: KeyboardEvent) {
  if (!showSuggestions.value) {
    return;
  }
  if (event.key === 'ArrowDown') {
    event.preventDefault();
    emit('move', 1);
  } else if (event.key === 'ArrowUp') {
    event.preventDefault();
    emit('move', -1);
  } else if (event.key === 'Enter') {
    event.preventDefault();
    emit('choose');
  } else if (event.key === 'Escape') {
    emit('close');
  }
}

function syncHighlightScroll(event: Event) {
  const element = event.target as HTMLTextAreaElement;
  const highlight = element.previousElementSibling as HTMLElement | null;
  if (!highlight) {
    return;
  }
  highlight.scrollTop = element.scrollTop;
  highlight.scrollLeft = element.scrollLeft;
}

function updateSuggestionPosition(element: HTMLInputElement | HTMLTextAreaElement) {
  const field = element.closest('.variable-field') as HTMLElement | null;
  if (!field) {
    return;
  }
  const marker = document.createElement('span');
  marker.textContent = '\u200b';
  const mirror = document.createElement('div');
  const style = window.getComputedStyle(element);
  const caret = element.selectionStart ?? element.value.length;

  mirror.style.position = 'absolute';
  mirror.style.visibility = 'hidden';
  mirror.style.pointerEvents = 'none';
  mirror.style.boxSizing = style.boxSizing;
  mirror.style.width = `${element.clientWidth}px`;
  mirror.style.padding = style.padding;
  mirror.style.border = style.border;
  mirror.style.font = style.font;
  mirror.style.lineHeight = style.lineHeight;
  mirror.style.letterSpacing = style.letterSpacing;
  mirror.style.whiteSpace = props.multiline ? 'pre-wrap' : 'pre';
  mirror.style.overflowWrap = style.overflowWrap;
  mirror.append(document.createTextNode(element.value.slice(0, caret)), marker);
  field.appendChild(mirror);

  const lineHeight = Number.parseFloat(style.lineHeight) || 20;
  const rect = element.getBoundingClientRect();
  const left = rect.left + marker.offsetLeft - element.scrollLeft;
  const top = rect.top + marker.offsetTop - element.scrollTop + lineHeight + 4;
  const maxLeft = window.innerWidth - 320 - 12;
  suggestionStyle.value = {
    position: 'fixed',
    left: `${Math.max(8, Math.min(left, maxLeft))}px`,
    top: `${Math.max(8, top)}px`,
    width: 'min(320px, calc(100vw - 16px))',
  };
  mirror.remove();
}
</script>
