<template>
  <div class="checklist">
    <section v-for="(group, groupIndex) in groups" :key="groupIndex" class="checklist-group">
      <div v-if="group.title" class="checklist-group-title">{{ group.title }}</div>
      <div
        v-for="entry in group.items"
        :key="entry.index"
        class="check-item"
        :class="{ pass: entry.checked }"
      >
        <a-checkbox
          class="check-box"
          :checked="entry.checked"
          :disabled="!editable"
          @change="$emit('toggle', entry.index)"
        >
          <span class="check-label">{{ entry.text }}</span>
        </a-checkbox>
        <span v-if="entry.auto" class="check-how auto">自动核验</span>
      </div>
    </section>
    <div v-if="groups.length === 0" class="plan-empty">（空清单）</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { parseChecklistGroups } from '../../utils/plan-markdown';

const props = defineProps<{ content: string; editable: boolean }>();
defineEmits<{ (e: 'toggle', index: number): void }>();

const groups = computed(() => parseChecklistGroups(props.content));
</script>

<style scoped>
/* 规格：plan-document-prototype.html .check-list / .check-item（分组 + token 色） */
.checklist {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 12.5px;
}

.checklist-group + .checklist-group {
  margin-top: 10px;
}

.checklist-group-title {
  margin-bottom: 4px;
  color: var(--muted);
  font-size: 11px;
  letter-spacing: 0.3px;
}

.check-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 2px 0;
}

.check-item .check-how {
  margin-left: auto;
  color: var(--muted);
  font-size: 11px;
  white-space: nowrap;
}

.check-item .check-how.auto {
  padding: 0 8px;
  border: 1px solid var(--accent);
  border-radius: 999px;
  background: var(--accent-soft);
  color: var(--plan-accent-text);
  font-weight: 600;
}

.check-item :deep(.ant-checkbox) {
  width: 16px;
  height: 16px;
  border-radius: 4px;
}

.check-item :deep(.ant-checkbox .ant-checkbox-inner) {
  width: 16px;
  height: 16px;
  border-radius: 4px;
  border-width: 1.5px;
  border-color: var(--line-strong);
  background: var(--surface);
}

.check-item :deep(.ant-checkbox-checked .ant-checkbox-inner) {
  border-color: var(--ok);
  background: var(--ok);
}

.check-item :deep(.ant-checkbox-disabled .ant-checkbox-inner) {
  background: var(--canvas);
}

.check-item :deep(.ant-checkbox-disabled.ant-checkbox-checked .ant-checkbox-inner) {
  border-color: var(--ok);
  background: var(--ok-soft);
}

.check-item :deep(.ant-checkbox-checked .ant-checkbox-inner::after) {
  border-color: var(--accent-ink);
}

.check-item :deep(.ant-checkbox-wrapper) {
  margin-right: 0;
}

.check-item.pass .check-label {
  color: var(--muted);
}
</style>
