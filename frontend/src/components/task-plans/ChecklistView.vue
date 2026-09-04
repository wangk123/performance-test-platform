<template>
  <div class="checklist">
    <div v-for="(item, index) in items" :key="index" class="checklist-item">
      <a-checkbox
        :checked="item.checked"
        :disabled="!editable"
        @change="$emit('toggle', index)"
      >
        {{ item.text }}
        <a-tag v-if="item.auto" color="cyan" class="checklist-tag">自动核验</a-tag>
      </a-checkbox>
    </div>
    <p v-if="items.length === 0" class="checklist-empty">（空清单）</p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { parseChecklistItems } from '../../utils/plan-markdown';

const props = defineProps<{ content: string; editable: boolean }>();
defineEmits<{ (e: 'toggle', index: number): void }>();

const items = computed(() => parseChecklistItems(props.content));
</script>

<style scoped>
.checklist-item { padding: 2px 0; }
.checklist-tag { margin-left: 6px; }
.checklist-empty { color: var(--muted); }
</style>
