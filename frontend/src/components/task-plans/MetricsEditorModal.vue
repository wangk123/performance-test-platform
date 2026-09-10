<template>
  <a-modal
    :open="open"
    title="编辑测试指标"
    :width="880"
    ok-text="保存"
    cancel-text="取消"
    @cancel="close"
    @ok="save"
  >
    <p class="metrics-hint">
      指标建议使用平台已知别名（TPS / 吞吐量 / 平均RT / P95 / P99 / 错误率 / 并发峰值 / 容量），
      已知别名参与报告自动达成判定，目标值须为可解析数值；列名固定，行可增删。
    </p>
    <div class="metric-edit-grid" role="table" aria-label="测试指标表">
      <div class="metric-edit-row metric-edit-head" role="row">
        <span>对象</span>
        <span>指标</span>
        <span>目标值</span>
        <span>口径</span>
        <span class="grid-op" aria-hidden="true"></span>
      </div>
      <div class="metric-edit-body">
        <div v-for="(row, index) in rows" :key="index" class="metric-edit-row" role="row">
          <a-input v-model:value="row.object" :aria-label="`第 ${index + 1} 行 对象`" placeholder="如：查询交易" />
          <a-input v-model:value="row.metric" :aria-label="`第 ${index + 1} 行 指标`" placeholder="TPS / P95 / 错误率…" />
          <a-input v-model:value="row.target" :aria-label="`第 ${index + 1} 行 目标值`" placeholder="如：≥ 300 ms" />
          <a-input v-model:value="row.caliber" :aria-label="`第 ${index + 1} 行 口径`" placeholder="如：5 分钟均值" />
          <button
            class="metric-edit-remove"
            type="button"
            :aria-label="`删除第 ${index + 1} 行`"
            title="删除本行"
            @click="removeRow(index)"
          >
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7h16M10 11v6M14 11v6M6 7l1 13a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1l1-13M9 7V5a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2" /></svg>
          </button>
        </div>
        <button class="metric-edit-add" type="button" @click="addRow">+ 添加指标</button>
      </div>
    </div>
    <p v-if="beforeText || afterText" class="metrics-keep">
      章节内表格外内容（{{ (beforeText + afterText).trim().length }} 字）保存后原样保留。
    </p>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { parseMarkdownTable, toMarkdownTable } from '../../utils/plan-markdown';

/**
 * 「三、测试指标」结构化编辑：固定字段（对象/指标/目标值/口径）表格表单，
 * 行可增删、表头锁定；保存时序列化回 markdown 表格并保留表格外的章节内容。
 * 列契约与后端 PlanAcceptanceParser 对齐：前三列按解析器必需列名归一，
 * 存量表格第 4 列（含旧「准出标准」表头）内容并入口径列，不丢数据。
 */
const props = defineProps<{ open: boolean; content: string }>();
const emit = defineEmits<{
  (e: 'update:open', value: boolean): void;
  (e: 'save', content: string): void;
}>();

interface MetricRow {
  object: string;
  metric: string;
  target: string;
  caliber: string;
}

const rows = ref<MetricRow[]>([]);
const beforeText = ref('');
const afterText = ref('');

watch(() => props.open, (open) => {
  if (open) parse();
});

function emptyRow(): MetricRow {
  return { object: '', metric: '', target: '', caliber: '' };
}

function at(cells: string[], index: number): string {
  return index >= 0 && index < cells.length ? cells[index].trim() : '';
}

function parse() {
  const lines = (props.content ?? '').split('\n');
  const start = lines.findIndex((line) => line.trim().startsWith('|'));
  if (start < 0) {
    beforeText.value = props.content ?? '';
    afterText.value = '';
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
  const objectCol = header.findIndex((h) => ['对象', '交易'].includes(h.trim().toLowerCase()));
  const metricCol = header.findIndex((h) => h.trim() === '指标');
  const targetCol = header.findIndex((h) => h.trim() === '目标值');
  const caliberCol = header.length > 3 ? 3 : -1;
  rows.value = (table?.rows ?? []).map((cells) => ({
    object: at(cells, objectCol),
    metric: at(cells, metricCol),
    target: at(cells, targetCol),
    caliber: at(cells, caliberCol),
  }));
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
  const table = toMarkdownTable(
    ['对象', '指标', '目标值', '口径'],
    rows.value.map((row) => [row.object, row.metric, row.target, row.caliber]),
  );
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
.metrics-hint {
  margin: 0 0 12px;
  font-size: 12.5px;
  color: var(--muted);
  line-height: 1.7;
}

.metric-edit-grid {
  border: 1px solid var(--line);
  border-radius: 10px;
  overflow: hidden;
}

.metric-edit-head {
  background: var(--surface-soft);
  border-bottom: 1px solid var(--line);
  padding: 7px 12px;
  color: var(--muted);
  font-size: 12px;
  font-weight: 700;
}

.metric-edit-head span {
  white-space: nowrap;
}

.metric-edit-body {
  max-height: 48vh;
  overflow-y: auto;
  scrollbar-width: thin;
}

.metric-edit-row {
  display: grid;
  grid-template-columns: 1.15fr 1.05fr 0.85fr 1.25fr 40px;
  gap: 8px;
  align-items: center;
  padding: 8px 12px;
}

.metric-edit-body .metric-edit-row {
  border-bottom: 1px solid color-mix(in srgb, var(--line) 55%, transparent);
  transition: background 0.15s;
}

.metric-edit-body .metric-edit-row:hover {
  background: var(--surface-soft);
}

.metric-edit-body .metric-edit-row:last-of-type {
  border-bottom: 0;
}

.metric-edit-remove {
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

.metric-edit-remove svg {
  width: 14px;
  height: 14px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.7;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.metric-edit-remove:hover {
  color: var(--danger);
  background: color-mix(in srgb, var(--danger) 10%, transparent);
}

.metric-edit-add {
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

.metric-edit-add:hover {
  color: var(--plan-accent-text);
  background: var(--surface-soft);
}

.metrics-keep {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--muted);
}
</style>
