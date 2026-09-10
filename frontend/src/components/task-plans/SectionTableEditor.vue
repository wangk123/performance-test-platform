<template>
  <a-modal
    :open="open"
    :title="`编辑章节：${sectionTitle}`"
    :width="880"
    ok-text="保存"
    cancel-text="取消"
    @cancel="close"
    @ok="save"
  >
    <p v-if="schema?.hint" class="sec-table-hint">{{ schema.hint }}</p>
    <div v-if="schema" class="sec-table-grid" role="table" :aria-label="`${sectionTitle}表`">
      <div class="sec-table-row sec-table-head" role="row" :style="gridStyle">
        <span v-for="col in schema.columns" :key="col.label">{{ col.label }}</span>
        <span class="grid-op" aria-hidden="true"></span>
      </div>
      <div class="sec-table-body">
        <div
          v-for="(row, index) in rows"
          :key="index"
          class="sec-table-row"
          role="row"
          :style="gridStyle"
        >
          <a-input
            v-for="(col, colIdx) in schema.columns"
            :key="col.label"
            v-model:value="row[colIdx]"
            :aria-label="`第 ${index + 1} 行 ${col.label}`"
            :placeholder="col.placeholder"
          />
          <button
            class="sec-table-remove"
            type="button"
            :aria-label="`删除第 ${index + 1} 行`"
            title="删除本行"
            @click="removeRow(index)"
          >
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7h16M10 11v6M14 11v6M6 7l1 13a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1l1-13M9 7V5a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2" /></svg>
          </button>
        </div>
        <button class="sec-table-add" type="button" @click="addRow">{{ schema.addLabel }}</button>
      </div>
    </div>
    <p v-if="beforeText || afterText" class="sec-table-keep">
      章节内表格外内容（{{ (beforeText + afterText).trim().length }} 字）保存后原样保留。
    </p>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { parseMarkdownTable, toMarkdownTable } from '../../utils/plan-markdown';
import { planTableSchemaOf, type PlanTableSectionSchema } from '../../utils/plan-table-schemas';

/**
 * 「表格型章节」结构化编辑（泛化自三、测试指标的 MetricsEditorModal）：
 * 列名固定（表头锁定）、行可增删；保存时序列化回 markdown 表格并保留表格外章节内容，
 * 走全文保存链路（revision 冲突保护不变）。
 * 列映射：指标章与后端 PlanAcceptanceParser 列契约对齐（canonicalHeader）；
 * 其余章节按表头名匹配、位置兜底，保存保留文档原表头。
 */
const props = defineProps<{ open: boolean; sectionTitle: string; content: string }>();
const emit = defineEmits<{
  (e: 'update:open', value: boolean): void;
  (e: 'save', content: string): void;
}>();

const rows = ref<string[][]>([]);
const beforeText = ref('');
const afterText = ref('');
const originalHeader = ref<string[]>([]);

const schema = computed<PlanTableSectionSchema | null>(() => planTableSchemaOf(props.sectionTitle));

const gridStyle = computed(() => ({
  gridTemplateColumns: `${(schema.value?.columns ?? []).map((col) => `${col.grow ?? 1}fr`).join(' ')} 40px`,
}));

watch(() => props.open, (open) => {
  if (open) parse();
});

function emptyRow(): string[] {
  return (schema.value?.columns ?? []).map(() => '');
}

function at(cells: string[], index: number): string {
  return index >= 0 && index < cells.length ? cells[index].trim() : '';
}

/** 指标章（canonicalHeader）：列位与后端 PlanAcceptanceParser 契约对齐，第 4 列并入口径。 */
function metricsColumnMap(header: string[]): number[] {
  const objectCol = header.findIndex((h) => ['对象', '交易'].includes(h.trim().toLowerCase()));
  const metricCol = header.findIndex((h) => h.trim() === '指标');
  const targetCol = header.findIndex((h) => h.trim() === '目标值');
  const caliberCol = header.length > 3 ? 3 : -1;
  return [objectCol, metricCol, targetCol, caliberCol];
}

/** 其余章节：按表头名（含别名）匹配，未命中按位置兜底；多出的列并入末列，不丢数据。 */
function genericColumnMap(header: string[], tableSchema: PlanTableSectionSchema): number[] {
  const used = new Set<number>();
  const byName = tableSchema.columns.map((col) => {
    const names = [col.label, ...(col.aliases ?? [])];
    const idx = header.findIndex((h, hi) => !used.has(hi) && names.includes(h.trim()));
    if (idx >= 0) used.add(idx);
    return idx;
  });
  return byName.map((idx, i) => {
    if (idx >= 0) return idx;
    const positional = i < header.length && !used.has(i) ? i : header.findIndex((_, hi) => !used.has(hi));
    if (positional >= 0) used.add(positional);
    return positional;
  });
}

function parse() {
  const tableSchema = schema.value;
  if (!tableSchema) return;
  const lines = (props.content ?? '').split('\n');
  const start = lines.findIndex((line) => line.trim().startsWith('|'));
  if (start < 0) {
    beforeText.value = props.content ?? '';
    afterText.value = '';
    originalHeader.value = [];
    rows.value = [emptyRow()];
    return;
  }
  let end = start;
  while (end < lines.length && lines[end].trim().startsWith('|')) {
    end += 1;
  }
  beforeText.value = lines.slice(0, start).join('\n').replace(/\n+$/, '');
  afterText.value = lines.slice(end).join('\n').replace(/^\n+/, '').replace(/\n+$/, '');
  const table = parseMarkdownTable(lines.slice(start, end).join('\n'));
  const header = table?.header ?? [];
  originalHeader.value = header;
  const map = tableSchema.canonicalHeader ? metricsColumnMap(header) : genericColumnMap(header, tableSchema);
  const lastMapped = Math.max(...map.filter((idx) => idx >= 0), -1);
  const extras = header.map((_, hi) => hi).filter((hi) => !map.includes(hi) && hi > lastMapped);
  rows.value = (table?.rows ?? []).map((cells) =>
    map.map((idx, colIdx) => {
      const base = at(cells, idx);
      if (colIdx === map.length - 1 && extras.length) {
        const merged = extras.map((hi) => at(cells, hi)).filter(Boolean).join('；');
        return merged ? (base ? `${base}；${merged}` : merged) : base;
      }
      return base;
    }),
  );
  if (rows.value.length === 0) {
    rows.value = [emptyRow()];
  }
}

function addRow() {
  rows.value.push(emptyRow());
}

function removeRow(index: number) {
  rows.value.splice(index, 1);
}

function close() {
  emit('update:open', false);
}

function save() {
  const tableSchema = schema.value;
  if (!tableSchema) return;
  const width = tableSchema.columns.length;
  const header = originalHeader.value.length > 0 && !tableSchema.canonicalHeader
    ? Array.from({ length: width }, (_, i) => originalHeader.value[i]?.trim() || tableSchema.columns[i].label)
    : tableSchema.columns.map((col) => col.label);
  const table = toMarkdownTable(header, rows.value);
  const parts: string[] = [];
  if (beforeText.value.trim()) {
    parts.push(beforeText.value, '', table);
  } else {
    parts.push(table);
  }
  if (afterText.value.trim()) {
    parts.push('', afterText.value);
  }
  emit('save', `\n${parts.join('\n')}\n`);
  close();
}
</script>

<style scoped>
.sec-table-hint {
  margin: 0 0 12px;
  font-size: 12.5px;
  color: var(--muted);
  line-height: 1.7;
}

.sec-table-grid {
  border: 1px solid var(--line);
  border-radius: 10px;
  overflow: hidden;
}

.sec-table-head {
  background: var(--surface-soft);
  border-bottom: 1px solid var(--line);
  padding: 7px 12px;
  color: var(--muted);
  font-size: 12px;
  font-weight: 700;
}

.sec-table-head span {
  white-space: nowrap;
}

.sec-table-body {
  max-height: 48vh;
  overflow-y: auto;
  scrollbar-width: thin;
}

.sec-table-row {
  display: grid;
  gap: 8px;
  align-items: center;
  padding: 8px 12px;
}

.sec-table-body .sec-table-row {
  border-bottom: 1px solid color-mix(in srgb, var(--line) 55%, transparent);
  transition: background 0.15s;
}

.sec-table-body .sec-table-row:hover {
  background: var(--surface-soft);
}

.sec-table-body .sec-table-row:last-of-type {
  border-bottom: 0;
}

.sec-table-remove {
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
  transition: color 0.15s, background 0.15s;
}

.sec-table-remove svg {
  width: 14px;
  height: 14px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.7;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.sec-table-remove:hover {
  color: var(--danger);
  background: color-mix(in srgb, var(--danger) 10%, transparent);
}

.sec-table-add {
  width: 100%;
  padding: 8px 0;
  border: 0;
  border-top: 1px dashed var(--line-strong);
  background: transparent;
  color: var(--muted);
  font-size: 12.5px;
  cursor: pointer;
  transition: color 0.15s, background 0.15s;
}

.sec-table-add:hover {
  color: var(--plan-accent-text);
  background: var(--surface-soft);
}

.sec-table-keep {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--muted);
}
</style>
