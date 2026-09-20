<template>
  <div class="kv-grid" :class="{ 'with-desc': withDescription }">
    <div class="kv-grid-head">
      <span class="kv-grid-col kv-grid-col-key">{{ keyLabel }}</span>
      <span class="kv-grid-col kv-grid-col-value">{{ valueLabel }}</span>
      <span v-if="withDescription" class="kv-grid-col kv-grid-col-desc">描述</span>
      <span class="kv-grid-col kv-grid-col-action"></span>
    </div>
    <div v-for="(item, index) in items" :key="index" class="kv-grid-row">
      <span class="kv-grid-col kv-grid-col-key">
        <input class="kv-grid-input" :value="item.key" :placeholder="keyPlaceholder" @input="update(index, 'key', ($event.target as HTMLInputElement).value)" />
      </span>
      <span class="kv-grid-col kv-grid-col-value">
        <input class="kv-grid-input" :value="item.value" :placeholder="valuePlaceholder" @input="update(index, 'value', ($event.target as HTMLInputElement).value)" />
      </span>
      <span v-if="withDescription" class="kv-grid-col kv-grid-col-desc">
        <input class="kv-grid-input" :value="item.description ?? ''" placeholder="备注" @input="update(index, 'description', ($event.target as HTMLInputElement).value)" />
      </span>
      <span class="kv-grid-col kv-grid-col-action">
        <button class="kv-grid-remove" type="button" title="删除该行" @click="remove(index)">×</button>
      </span>
    </div>
    <a-button size="small" class="kv-grid-add" @click="add">{{ addLabel }}</a-button>
  </div>
</template>

<script setup lang="ts">
export type KeyValueItem = {
  key: string;
  value: string;
  description?: string;
};

const props = withDefaults(
  defineProps<{
    items: KeyValueItem[];
    keyLabel?: string;
    valueLabel?: string;
    keyPlaceholder?: string;
    valuePlaceholder?: string;
    addLabel?: string;
    withDescription?: boolean;
  }>(),
  {
    keyLabel: '名称',
    valueLabel: '值',
    keyPlaceholder: '',
    valuePlaceholder: '',
    addLabel: '添加一行',
    withDescription: false,
  },
);

const emit = defineEmits<{
  change: [items: KeyValueItem[]];
}>();

function add() {
  emit('change', [...props.items, { key: '', value: '', description: '' }]);
}

function remove(index: number) {
  emit('change', props.items.filter((_, itemIndex) => itemIndex !== index));
}

function update(index: number, field: 'key' | 'value' | 'description', value: string) {
  emit(
    'change',
    props.items.map((item, itemIndex) => (itemIndex === index ? { ...item, [field]: value } : item)),
  );
}
</script>

<style scoped>
.kv-grid {
  border: 1px solid var(--border, #e5e7eb);
  border-radius: var(--radius, 8px);
  overflow: hidden;
}

.kv-grid-head,
.kv-grid-row {
  display: grid;
  grid-template-columns: minmax(120px, 1fr) minmax(160px, 1.4fr) 36px;
}

.kv-grid.with-desc :where(.kv-grid-head, .kv-grid-row) {
  grid-template-columns: minmax(110px, 1fr) minmax(140px, 1.2fr) minmax(110px, 1fr) 36px;
}

.kv-grid-head {
  background: var(--bg-secondary, #f5f5f5);
  font-weight: 600;
  font-size: 13px;
  color: var(--text-secondary, #595959);
}

.kv-grid-col {
  padding: 6px 8px;
  display: flex;
  align-items: center;
  min-width: 0;
}

.kv-grid-row .kv-grid-col {
  border-top: 1px solid var(--border, #e5e7eb);
}

.kv-grid-input {
  width: 100%;
  box-sizing: border-box;
  padding: 4px 8px;
  border: 1px solid var(--border, #d9d9d9);
  border-radius: 4px;
  font-size: 13px;
  font-family: var(--font-data, monospace);
  background: transparent;
  color: inherit;
}

.kv-grid-input:focus {
  outline: none;
  border-color: var(--primary, #1677ff);
}

.kv-grid-col-action {
  justify-content: center;
}

.kv-grid-remove {
  border: none;
  background: transparent;
  color: var(--text-tertiary, #8c8c8c);
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 4px;
}

.kv-grid-remove:hover {
  color: #ef4444;
  background: rgba(239, 68, 68, 0.1);
}

.kv-grid-add {
  margin: 8px;
}
</style>
