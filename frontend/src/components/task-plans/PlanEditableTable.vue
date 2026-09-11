<template>
  <div class="etable" role="table" :aria-label="ariaLabel">
    <div class="et-row et-head" role="row" :style="gridStyle">
      <template v-if="schema">
        <div v-for="col in schema.columns" :key="col.label" class="et-cell et-head-cell locked" role="columnheader">
          {{ col.label }}
        </div>
      </template>
      <template v-else>
        <div v-for="(label, colIdx) in header" :key="colIdx" class="et-cell et-head-cell" role="columnheader">
          <input
            :value="header[colIdx]"
            :aria-label="`第 ${colIdx + 1} 列表头`"
            placeholder="列名"
            @input="setHeader(colIdx, $event)"
          />
          <button
            v-if="header.length > 1"
            class="et-col-del"
            type="button"
            :aria-label="`删除第 ${colIdx + 1} 列`"
            title="删除此列"
            @click="removeColumn(colIdx)"
          >
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18" /></svg>
          </button>
        </div>
      </template>
      <div class="et-op" aria-hidden="true"></div>
    </div>

    <div
      v-for="(row, rowIdx) in rows"
      :key="rowIdx"
      class="et-row"
      role="row"
      :style="gridStyle"
    >
      <div
        v-for="(col, colIdx) in rowCells(row)"
        :key="colIdx"
        class="et-cell"
        role="cell"
        :class="{ 'is-dirty': touched.has(`${rowIdx}:${colIdx}`) }"
      >
        <input
          :value="row[colIdx]"
          :aria-label="`第 ${rowIdx + 1} 行 ${columnLabel(colIdx)}`"
          :placeholder="schema?.columns[colIdx]?.placeholder ?? ''"
          @input="setCell(rowIdx, colIdx, $event)"
        />
      </div>
      <div class="et-op" role="cell">
        <button
          class="et-row-del"
          type="button"
          :aria-label="`删除第 ${rowIdx + 1} 行`"
          title="删除本行"
          @click="removeRow(rowIdx)"
        >
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7h16M10 11v6M14 11v6M6 7l1 13a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1l1-13M9 7V5a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2" /></svg>
        </button>
      </div>
    </div>

    <div class="et-footer">
      <button class="et-add-row" type="button" @click="addRow">{{ schema?.addLabel ?? '＋ 添加行' }}</button>
      <button v-if="!schema" class="et-add-col" type="button" @click="addColumn">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 5v14M5 12h14" /></svg>添加列
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import type { PlanTableSectionSchema } from '../../utils/plan-table-schemas';

/**
 * 行内可编辑表格网格（Pretty 编辑态）：
 * - 锁定模式（schema 非空）：表头由平台 schema 固定，行可增删（固定列表章节：指标/资源/风险/排期）；
 * - 通用模式（schema 为空）：表头单元格可编辑，行和列都可增删（任意章节的普通表格）。
 * 数据通过 v-model:header / v-model:rows 上交父级，序列化回 markdown 表格在父级完成。
 */
const props = defineProps<{
  schema: PlanTableSectionSchema | null;
  header: string[];
  rows: string[][];
  ariaLabel?: string;
}>();

const emit = defineEmits<{
  (e: 'update:header', value: string[]): void;
  (e: 'update:rows', value: string[][]): void;
}>();

const width = computed(() => (props.schema ? props.schema.columns.length : Math.max(props.header.length, 1)));

const gridStyle = computed(() => ({
  gridTemplateColumns: props.schema
    ? `${props.schema.columns.map((col) => `${col.grow ?? 1}fr`).join(' ')} 36px`
    : `${Array.from({ length: width.value }, () => 'minmax(0, 1fr)').join(' ')} 36px`,
}));

/** 本次编辑会话中改过的单元格（r:c），渲染左缘色条。 */
const touched = ref(new Set<string>());

function rowCells(row: string[]): string[] {
  return Array.from({ length: width.value }, (_, i) => row[i] ?? '');
}

function columnLabel(colIdx: number): string {
  return props.schema?.columns[colIdx]?.label ?? props.header[colIdx] ?? String(colIdx + 1);
}

function emitRows(next: string[][]) {
  emit('update:rows', next);
}

function setHeader(colIdx: number, event: Event) {
  const value = (event.target as HTMLInputElement).value;
  emit('update:header', props.header.map((cell, i) => (i === colIdx ? value : cell)));
}

function setCell(rowIdx: number, colIdx: number, event: Event) {
  const value = (event.target as HTMLInputElement).value;
  touched.value.add(`${rowIdx}:${colIdx}`);
  emitRows(props.rows.map((row, r) => {
    if (r !== rowIdx) return row;
    const padded = Array.from({ length: width.value }, (_, i) => row[i] ?? '');
    padded[colIdx] = value;
    return padded;
  }));
}

function addRow() {
  emitRows([...props.rows, Array.from({ length: width.value }, () => '')]);
}

function removeRow(rowIdx: number) {
  emitRows(props.rows.filter((_, i) => i !== rowIdx));
}

function addColumn() {
  emit('update:header', [...props.header, '']);
  emitRows(props.rows.map((row) => [...row, '']));
}

function removeColumn(colIdx: number) {
  emit('update:header', props.header.filter((_, i) => i !== colIdx));
  emitRows(props.rows.map((row) => row.filter((_, i) => i !== colIdx)));
}
</script>

<style scoped>
.etable {
  border: 1px solid var(--line);
  border-radius: 10px;
  overflow: hidden;
  background: var(--surface);
  font-size: 13px;
}

.et-row {
  display: grid;
  align-items: stretch;
  border-bottom: 1px solid var(--line);
  transition: background 0.15s;
}

.et-row:last-of-type {
  border-bottom: 0;
}

.et-row:not(.et-head):hover {
  background: var(--surface-soft);
}

.et-cell {
  position: relative;
  display: flex;
  align-items: center;
  min-height: 38px;
  border-right: 1px solid color-mix(in srgb, var(--line) 60%, transparent);
}

.et-cell:last-of-type {
  border-right: 0;
}

.et-cell input {
  flex: 1;
  min-width: 0;
  height: 100%;
  padding: 7px 12px;
  border: 0;
  background: transparent;
  color: var(--ink);
  font: inherit;
  line-height: 1.5;
  outline: none;
  caret-color: var(--accent);
}

.et-cell input::placeholder {
  color: color-mix(in srgb, var(--muted) 62%, transparent);
}

.et-cell input:focus {
  box-shadow: inset 0 0 0 2px color-mix(in srgb, var(--accent) 40%, transparent);
  border-radius: 6px;
}

/* 聚焦描边与脏标记色条并存：色条贴左缘 */
.et-cell.is-dirty::before {
  content: '';
  position: absolute;
  left: 0;
  top: 6px;
  bottom: 6px;
  width: 2px;
  border-radius: 2px;
  background: var(--accent);
}

.et-head {
  background: var(--canvas);
}

.et-head-cell {
  min-height: 34px;
  color: var(--muted);
  font-size: 12px;
  font-weight: 600;
}

.et-head-cell.locked {
  padding: 7px 12px;
  white-space: nowrap;
}

.et-head-cell input {
  padding: 7px 12px;
  color: var(--muted);
  font-size: 12px;
  font-weight: 600;
}

.et-op {
  display: flex;
  align-items: center;
  justify-content: center;
}

.et-col-del,
.et-row-del {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--muted);
  opacity: 0;
  cursor: pointer;
  transition: opacity 0.15s, color 0.15s, background 0.15s;
}

.et-col-del svg,
.et-row-del svg {
  width: 12px;
  height: 12px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.et-row:hover .et-row-del,
.et-head:hover .et-col-del {
  opacity: 0.75;
}

.et-col-del:hover,
.et-row-del:hover {
  opacity: 1 !important;
  color: var(--danger);
  background: color-mix(in srgb, var(--danger) 10%, transparent);
}

.et-footer {
  display: flex;
  align-items: stretch;
}

.et-add-row {
  flex: 1;
  padding: 8px 0;
  border: 0;
  border-top: 1px dashed var(--line-strong);
  background: transparent;
  color: var(--muted);
  font-size: 12.5px;
  font-family: inherit;
  cursor: pointer;
  transition: color 0.15s, background 0.15s;
}

.et-add-row:hover {
  color: var(--plan-accent-text);
  background: var(--surface-soft);
}

.et-add-col {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 0 14px;
  border: 0;
  border-top: 1px dashed var(--line-strong);
  border-left: 1px dashed var(--line-strong);
  background: transparent;
  color: var(--muted);
  font-size: 12px;
  font-family: inherit;
  cursor: pointer;
  transition: color 0.15s, background 0.15s;
}

.et-add-col svg {
  width: 10px;
  height: 10px;
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linecap: round;
}

.et-add-col:hover {
  color: var(--plan-accent-text);
  background: var(--surface-soft);
}
</style>
