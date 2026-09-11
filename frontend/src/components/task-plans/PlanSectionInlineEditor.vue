<template>
  <div ref="rootRef" class="inline-editor" role="group" :aria-label="`编辑章节：${heading}`" @keydown.capture="onKeydown">
    <div class="ie-toolbar">
      <span class="ie-state"><i class="ie-pulse"></i>正在编辑 · {{ heading }}</span>
      <span class="ie-spacer"></span>
      <button class="ie-act" type="button" title="取消编辑（Esc）" aria-label="取消编辑" @click="requestCancel">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18" /></svg>
      </button>
      <button class="ie-act primary" type="button" :disabled="busy" title="保存本章（⌘/Ctrl+S）" aria-label="保存本章" @click="save">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12.5l4.5 4.5L19 7.5" /></svg>
      </button>
    </div>

    <template v-for="(block, index) in blocks" :key="block.key">
      <!-- 文本块：聚焦时浮现格式工具条 -->
      <div v-if="block.kind === 'text'" class="ie-text-block">
        <div class="fmt-bar">
          <button class="fmt-btn" type="button" title="加粗" aria-label="加粗" @mousedown.prevent @click="fmtBold(block)"><svg viewBox="0 0 24 24"><path d="M7 5h6a3.5 3.5 0 0 1 0 7H7zM7 12h7a3.5 3.5 0 0 1 0 7H7z" /></svg></button>
          <button class="fmt-btn" type="button" title="斜体" aria-label="斜体" @mousedown.prevent @click="fmtItalic(block)"><svg viewBox="0 0 24 24"><path d="M10 5h8M6 19h8M14 5l-4 14" /></svg></button>
          <button class="fmt-btn" type="button" title="下划线" aria-label="下划线" @mousedown.prevent @click="fmtWrap(block, '<u>', '</u>', '下划线文字')"><svg viewBox="0 0 24 24"><path d="M7 4v7a5 5 0 0 0 10 0V4M5 20h14" /></svg></button>
          <button class="fmt-btn" type="button" title="删除线" aria-label="删除线" @mousedown.prevent @click="fmtWrap(block, '~~', '~~', '删除线文字')"><svg viewBox="0 0 24 24"><path d="M5 12h14M8 7a3 3 0 0 1 3-2h3.5a3 3 0 0 1 0 6M16 17a3 3 0 0 1-3 2h-3.5a3 3 0 0 1-3-2" /></svg></button>
          <span class="fmt-sep" aria-hidden="true"></span>
          <button class="fmt-btn" type="button" title="小节标题（###）" aria-label="小节标题" @mousedown.prevent @click="fmtPrefix(block, '### ', true)"><svg viewBox="0 0 24 24"><path d="M5 5v14M17 5v14M5 12h12M17.5 16.5l2.5 3 2.5-3" /></svg></button>
          <span class="fmt-sep" aria-hidden="true"></span>
          <button class="fmt-btn" type="button" title="引用" aria-label="引用" @mousedown.prevent @click="fmtPrefix(block, '> ')"><svg viewBox="0 0 24 24"><path d="M9 7H5v4h4zM9 11c0 3-2 5-4 6M19 7h-4v4h4zM19 11c0 3-2 5-4 6" /></svg></button>
          <button class="fmt-btn" type="button" title="无序列表" aria-label="无序列表" @mousedown.prevent @click="fmtPrefix(block, '- ')"><svg viewBox="0 0 24 24"><path d="M9 6h11M9 12h11M9 18h11" /><circle cx="4.5" cy="6" r="1.1" fill="currentColor" stroke="none" /><circle cx="4.5" cy="12" r="1.1" fill="currentColor" stroke="none" /><circle cx="4.5" cy="18" r="1.1" fill="currentColor" stroke="none" /></svg></button>
          <button class="fmt-btn" type="button" title="有序列表" aria-label="有序列表" @mousedown.prevent @click="fmtPrefix(block, '1. ')"><svg viewBox="0 0 24 24"><path d="M10 6h10M10 12h10M10 18h10" /><text x="3.2" y="8" font-size="6.5" fill="currentColor" stroke="none">1</text><text x="3.2" y="14.2" font-size="6.5" fill="currentColor" stroke="none">2</text><text x="3.2" y="20.4" font-size="6.5" fill="currentColor" stroke="none">3</text></svg></button>
          <button class="fmt-btn" type="button" title="任务列表" aria-label="任务列表" @mousedown.prevent @click="fmtPrefix(block, '- [ ] ')"><svg viewBox="0 0 24 24"><rect x="3" y="4" width="7" height="7" rx="1.5" /><path d="M5 7.5l1.6 1.6L9.4 6.4M14 7.5h7" /><rect x="3" y="13" width="7" height="7" rx="1.5" /><path d="M14 16.5h7" /></svg></button>
          <span class="fmt-sep" aria-hidden="true"></span>
          <button class="fmt-btn" type="button" title="行内代码" aria-label="行内代码" @mousedown.prevent @click="fmtWrap(block, '`', '`', '代码')"><svg viewBox="0 0 24 24"><path d="M9 8l-5 4 5 4M15 8l5 4-5 4" /></svg></button>
          <button class="fmt-btn" type="button" title="代码块" aria-label="代码块" @mousedown.prevent @click="fmtCodeBlock(block)"><svg viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="16" rx="2" /><path d="M9 10.5l-2.5 2.5L9 15.5M15 10.5l2.5 2.5L15 15.5" /></svg></button>
          <button class="fmt-btn" type="button" title="链接" aria-label="链接" @mousedown.prevent @click="fmtLink(block)"><svg viewBox="0 0 24 24"><path d="M10.5 13.5a4 4 0 0 0 5.7 0l2.6-2.6a4 4 0 0 0-5.7-5.7l-1.3 1.3M13.5 10.5a4 4 0 0 0-5.7 0l-2.6 2.6a4 4 0 0 0 5.7 5.7l1.3-1.3" /></svg></button>
          <button class="fmt-btn" type="button" title="表格" aria-label="表格" @mousedown.prevent @click="fmtTableTemplate(block)"><svg viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="16" rx="2" /><path d="M3 10h18M3 15h18M12 10v10" /></svg></button>
          <button class="fmt-btn" type="button" title="图片" aria-label="图片" @mousedown.prevent @click="fmtImage(block)"><svg viewBox="0 0 24 24"><rect x="3" y="5" width="18" height="14" rx="2" /><circle cx="9" cy="10" r="1.6" /><path d="M21 15l-5-5-9 9" /></svg></button>
          <span class="fmt-sep" aria-hidden="true"></span>
          <button class="fmt-btn" type="button" title="撤销（⌘Z）" aria-label="撤销" @mousedown.prevent @click="fmtExec(block, 'undo')"><svg viewBox="0 0 24 24"><path d="M8 5L4 9l4 4M4 9h10a5 5 0 0 1 0 10h-3" /></svg></button>
          <button class="fmt-btn" type="button" title="重做（⌘⇧Z）" aria-label="重做" @mousedown.prevent @click="fmtExec(block, 'redo')"><svg viewBox="0 0 24 24"><path d="M16 5l4 4-4 4M20 9H10a5 5 0 0 0 0 10h3" /></svg></button>
          <button
            class="fmt-btn ai"
            type="button"
            :disabled="!hasSelection(block) || polishBusy"
            :title="hasSelection(block) ? '润色选中文本' : '划选文本后可润色'"
            @mousedown.prevent
            @click="polishSelection(block)"
          >
            <svg viewBox="0 0 24 24"><path d="M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9L12 3z" /></svg>{{ polishBusy && polishBlockKey === block.key ? '润色中…' : '润色' }}
          </button>
        </div>
        <textarea
          ref="textareaRefs"
          :data-key="block.key"
          v-model="block.content"
          class="ie-textarea"
          placeholder="输入 Markdown 文本…"
          @input="autoGrow"
          @select="syncSelection(block, $event)"
          @mouseup="syncSelection(block, $event)"
          @keyup="syncSelection(block, $event)"
        ></textarea>
        <!-- 选区润色对比：采纳只替换选中片段 -->
        <div v-if="polishCompare && polishCompare.blockKey === block.key" class="ie-polish">
          <div class="ie-polish-pane">
            <div class="ie-polish-tag">选中原文 <span class="ie-len">{{ polishCompare.original.length }} 字</span></div>
            <div class="ie-polish-body">{{ polishCompare.original }}</div>
          </div>
          <div class="ie-polish-pane ai">
            <div class="ie-polish-tag">✨ 润色稿
              <span class="ie-polish-actions">
                <button class="ie-act primary sm" type="button" title="采纳：仅替换选中片段" aria-label="采纳润色稿" @click="adoptPolish"><svg viewBox="0 0 24 24"><path d="M5 12.5l4.5 4.5L19 7.5" /></svg></button>
                <button class="ie-act sm" type="button" title="放弃" aria-label="放弃润色稿" @click="polishCompare = null"><svg viewBox="0 0 24 24"><path d="M6 6l12 12M18 6L6 18" /></svg></button>
              </span>
            </div>
            <div class="ie-polish-body">{{ polishCompare.result }}</div>
          </div>
        </div>
      </div>

      <!-- 表格块：锁定表头（固定列表章节）或通用可编辑表格 -->
      <div v-else-if="block.kind === 'table'" class="ie-table-block">
        <PlanEditableTable
          :schema="block.schema"
          :header="block.header"
          :rows="block.rows"
          :aria-label="`${heading}表`"
          @update:header="block.header = $event"
          @update:rows="block.rows = $event"
        />
      </div>

      <!-- 图片块：空 URL 时编辑 alt/url，否则预览 + hover 替换/删除 -->
      <div v-else class="ie-image-block">
        <template v-if="editingImageKey === block.key">
          <div class="ie-image-form">
            <a-input v-model:value="block.alt" placeholder="图片描述（alt）" :aria-label="'图片描述'" />
            <a-input v-model:value="block.url" placeholder="图片 URL（https://…）" :aria-label="'图片 URL'" />
            <button class="ie-act primary sm" type="button" :disabled="!block.url.trim()" title="确定" aria-label="确定" @click="editingImageKey = null"><svg viewBox="0 0 24 24"><path d="M5 12.5l4.5 4.5L19 7.5" /></svg></button>
            <button class="ie-act sm" type="button" title="删除图片" aria-label="删除图片" @click="removeBlock(block.key)"><svg viewBox="0 0 24 24"><path d="M4 7h16M10 11v6M14 11v6M6 7l1 13a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1l1-13M9 7V5a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2" /></svg></button>
          </div>
        </template>
        <template v-else>
          <img class="ie-image" :src="block.url" :alt="block.alt" />
          <div class="ie-image-overlay">
            <button class="ie-act sm" type="button" title="替换图片" aria-label="替换图片" @click="editingImageKey = block.key"><svg viewBox="0 0 24 24"><path d="M4 12a8 8 0 0 1 13.6-5.7L20 8M20 4v4h-4M20 12a8 8 0 0 1-13.6 5.7L4 16M4 20v-4h4" /></svg></button>
            <button class="ie-act sm" type="button" title="删除图片" aria-label="删除图片" @click="removeBlock(block.key)"><svg viewBox="0 0 24 24"><path d="M4 7h16M10 11v6M14 11v6M6 7l1 13a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1l1-13M9 7V5a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2" /></svg></button>
          </div>
        </template>
      </div>

      <!-- 块间插入点 -->
      <div v-if="index < blocks.length - 1" class="ie-insert" :class="{ open: insertAt === index + 1 }">
        <button class="ie-insert-btn" type="button" aria-label="在此插入内容" title="在此插入内容" @click="toggleInsert(index + 1)">＋</button>
        <div v-if="insertAt === index + 1" class="ie-insert-menu">
          <button type="button" title="插入 Markdown 文本段落" @click="insertBlock('text', index + 1)">文本</button>
          <button type="button" title="插入可编辑表格" @click="insertBlock('table', index + 1)">表格</button>
          <button type="button" title="插入图片（粘贴 URL）" @click="insertBlock('image', index + 1)">图片</button>
        </div>
      </div>
    </template>

    <!-- 章尾插入 -->
    <div class="ie-insert tail" :class="{ open: insertAt === blocks.length }">
      <button class="ie-tail-btn" type="button" @click="toggleInsert(blocks.length)">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 5v14M5 12h14" /></svg>插入…
      </button>
      <div v-if="insertAt === blocks.length" class="ie-insert-menu">
        <button type="button" title="插入 Markdown 文本段落" @click="insertBlock('text', blocks.length)">文本</button>
        <button type="button" title="插入可编辑表格" @click="insertBlock('table', blocks.length)">表格</button>
        <button type="button" title="插入图片（粘贴 URL）" @click="insertBlock('image', blocks.length)">图片</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import {
  parseSectionBlocks, serializeSectionBlocks, type SectionBlock,
} from '../../utils/plan-markdown';
import { mapTableToSchema, planTableCompatible, planTableSchemaOf, type PlanTableSectionSchema } from '../../utils/plan-table-schemas';
import { polishPlanSectionApi } from '../../api/plan-doc';
import PlanEditableTable from './PlanEditableTable.vue';

/**
 * 章节行内编辑器（Pretty 视图，替代原弹窗编辑）：章内容按块分解为「文本段 / 表格 / 图片」顺序编辑——
 * 文本块聚焦浮现 Markdown 格式工具条并支持划选 AI 润色（只润色选中片段）；
 * 表格块锁定表头（固定列表章节 schema）或通用可编辑（表头可改、行和列可增删）；
 * 图片块预览 + 替换/删除。保存时按块序列重组回 markdown（serializeSectionBlocks），
 * 整文提交链路与 revision 冲突保护在父级不变。
 */

interface TextBlock { kind: 'text'; content: string; key: number }
interface TableBlock { kind: 'table'; schema: PlanTableSectionSchema | null; header: string[]; rows: string[][]; key: number }
interface ImageBlock { kind: 'image'; alt: string; url: string; key: number }
type EditorBlock = TextBlock | TableBlock | ImageBlock;

const props = defineProps<{
  planId: number;
  /** 规范标题（schema 匹配、润色接口与写回 replaceSection 用）。 */
  title: string;
  /** 展示用真实标题（工具条文案）。 */
  heading: string;
  content: string;
  /** 父级保存进行中（冲突处理/网络），禁用保存按钮。 */
  busy?: boolean;
}>();

const emit = defineEmits<{
  (e: 'save', content: string): void;
  (e: 'cancel-request'): void;
  (e: 'update:dirty', value: boolean): void;
}>();

const rootRef = ref<HTMLElement | null>(null);
const textareaRefs = ref<HTMLTextAreaElement[]>([]);
const blocks = ref<EditorBlock[]>([]);
const baseline = ref('');
const insertAt = ref<number | null>(null);
const editingImageKey = ref<number | null>(null);

let keySeed = 0;
const nextKey = () => keySeed++;

function initBlocks() {
  const schema = planTableSchemaOf(props.title);
  const list: EditorBlock[] = [];
  let hasTable = false;
  for (const parsed of parseSectionBlocks(props.content)) {
    if (parsed.kind === 'text') {
      list.push({ kind: 'text', content: parsed.content, key: nextKey() });
    } else if (parsed.kind === 'table') {
      hasTable = true;
      if (schema && planTableCompatible(schema, parsed.header)) {
        // 锁定表头：header 即保存用表头（schema 列序 + 文档原列名），与重排后的行逐列对齐
        const mapped = mapTableToSchema(parsed, schema);
        list.push({ kind: 'table', schema, header: mapped.header, rows: mapped.rows, key: nextKey() });
      } else {
        list.push({ kind: 'table', schema: null, header: parsed.header, rows: parsed.rows, key: nextKey() });
      }
    } else {
      list.push({ kind: 'image', alt: parsed.alt, url: parsed.url, key: nextKey() });
    }
  }
  // 固定列表章节无表：以规范列表头空表起步（沿用原弹窗行为）
  if (schema && !hasTable) {
    list.push({ kind: 'table', schema, header: schema.columns.map((col) => col.label), rows: [schema.columns.map(() => '')], key: nextKey() });
  }
  blocks.value = list;
  baseline.value = serializeBlocks();
}

/** 块序列 → 章内容；指标章（canonicalHeader）强制规范表头（后端 PlanAcceptanceParser 契约），其余表头随网格数据。 */
function serializeBlocks(): string {
  const out: SectionBlock[] = [];
  for (const block of blocks.value) {
    if (block.kind === 'text') {
      out.push({ kind: 'text', content: block.content });
    } else if (block.kind === 'table') {
      if (block.rows.length === 0 || block.header.length === 0) continue;
      const header = block.schema?.canonicalHeader
        ? block.schema.columns.map((col) => col.label)
        : block.header;
      out.push({ kind: 'table', header, rows: block.rows });
    } else {
      out.push({ kind: 'image', alt: block.alt, url: block.url });
    }
  }
  return serializeSectionBlocks(out);
}

const dirty = computed(() => serializeBlocks() !== baseline.value);
watch(dirty, (value) => emit('update:dirty', value), { immediate: true });

function save() {
  if (props.busy) return;
  emit('save', serializeBlocks());
}

/** 取消请求上交父级统一裁决（脏稿确认在父级，与「切换他章」共用一套文案）。 */
function requestCancel() {
  emit('cancel-request');
}

function onKeydown(event: KeyboardEvent) {
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 's') {
    event.preventDefault();
    save();
  } else if (event.key === 'Escape') {
    event.stopPropagation();
    requestCancel();
  }
}

function removeBlock(key: number) {
  blocks.value = blocks.value.filter((block) => block.key !== key);
  if (editingImageKey.value === key) editingImageKey.value = null;
}

/* ---------- 插入点：块间 hover ＋ / 章尾固定入口 ---------- */

function toggleInsert(index: number) {
  insertAt.value = insertAt.value === index ? null : index;
}

function insertBlock(kind: 'text' | 'table' | 'image', index: number) {
  const schema = planTableSchemaOf(props.title);
  let block: EditorBlock;
  if (kind === 'text') {
    block = { kind: 'text', content: '', key: nextKey() };
  } else if (kind === 'table') {
    block = schema
      ? { kind: 'table', schema, header: schema.columns.map((col) => col.label), rows: [schema.columns.map(() => '')], key: nextKey() }
      : { kind: 'table', schema: null, header: ['', '', ''], rows: [['', '', ''], ['', '', '']], key: nextKey() };
  } else {
    block = { kind: 'image', alt: '', url: '', key: nextKey() };
    editingImageKey.value = block.key;
  }
  blocks.value.splice(index, 0, block);
  insertAt.value = null;
  if (kind === 'text') {
    void nextTick(() => textareaByKey(block.key)?.focus());
  }
}

function textareaByKey(key: number): HTMLTextAreaElement | null {
  return rootRef.value?.querySelector<HTMLTextAreaElement>(`textarea[data-key="${key}"]`) ?? null;
}

function autoGrow(event: Event) {
  autoGrowEl(event.target as HTMLTextAreaElement);
}

function autoGrowEl(ta: HTMLTextAreaElement) {
  ta.style.height = 'auto';
  ta.style.height = `${ta.scrollHeight}px`;
}

/* ---------- 文本块格式工具条（execCommand 保住 textarea 原生撤销栈） ---------- */

function withTextarea(block: TextBlock, fn: (ta: HTMLTextAreaElement) => void) {
  const ta = textareaByKey(block.key);
  if (ta) fn(ta);
}

/** 以 fn 的返回值替换当前选区（execCommand 走浏览器输入管线，⌘Z 可撤销）。 */
function transformSelection(ta: HTMLTextAreaElement, fn: (selected: string) => string) {
  const start = ta.selectionStart;
  const end = ta.selectionEnd;
  ta.focus();
  ta.setSelectionRange(start, end);
  document.execCommand('insertText', false, fn(ta.value.slice(start, end)));
}

/** 对选区涉及的整行加前缀（列表/引用/标题）；toggle 时已带前缀的行剥掉一层。 */
function transformLinePrefix(ta: HTMLTextAreaElement, prefix: string, toggle = false) {
  const start = ta.value.lastIndexOf('\n', ta.selectionStart - 1) + 1;
  let end = ta.value.indexOf('\n', ta.selectionEnd);
  if (end < 0) end = ta.value.length;
  const lines = ta.value.slice(start, end).split('\n');
  const applied = lines
    .map((line) => (toggle && line.trimStart().startsWith(prefix) ? line.slice(prefix.length) : prefix + line))
    .join('\n');
  ta.focus();
  ta.setSelectionRange(start, end);
  document.execCommand('insertText', false, applied);
}

function fmtBold(block: TextBlock) { withTextarea(block, (ta) => transformSelection(ta, (sel) => `**${sel || '加粗文字'}**`)); }
function fmtItalic(block: TextBlock) { withTextarea(block, (ta) => transformSelection(ta, (sel) => `*${sel || '斜体文字'}*`)); }
function fmtWrap(block: TextBlock, before: string, after: string, fallback: string) {
  withTextarea(block, (ta) => transformSelection(ta, (sel) => `${before}${sel || fallback}${after}`));
}
function fmtPrefix(block: TextBlock, prefix: string, toggle = false) {
  withTextarea(block, (ta) => transformLinePrefix(ta, prefix, toggle));
}
function fmtCodeBlock(block: TextBlock) {
  withTextarea(block, (ta) => transformSelection(ta, (sel) => `\`\`\`\n${sel || '代码'}\n\`\`\``));
}
function fmtLink(block: TextBlock) {
  withTextarea(block, (ta) => transformSelection(ta, (sel) => `[${sel || '链接文字'}](url)`));
}
function fmtImage(block: TextBlock) {
  withTextarea(block, (ta) => transformSelection(ta, (sel) => `![${sel || '描述'}](url)`));
}
function fmtTableTemplate(block: TextBlock) {
  withTextarea(block, (ta) => transformSelection(ta, () => '| 列1 | 列2 | 列3 |\n| --- | --- | --- |\n|  |  |  |'));
}
function fmtExec(block: TextBlock, command: 'undo' | 'redo') {
  withTextarea(block, (ta) => { ta.focus(); document.execCommand(command); });
}

/* ---------- 选区级 AI 润色：只把选中文本发给接口，采纳只替换选中片段 ---------- */

const polishBusy = ref(false);
const polishBlockKey = ref<number | null>(null);
const polishCompare = ref<{ blockKey: number; start: number; end: number; original: string; result: string } | null>(null);
const selection = ref<{ key: number; start: number; end: number } | null>(null);

function syncSelection(block: TextBlock, event: Event) {
  const ta = event.target as HTMLTextAreaElement;
  selection.value = { key: block.key, start: ta.selectionStart, end: ta.selectionEnd };
}

function hasSelection(block: TextBlock): boolean {
  const sel = selection.value;
  return sel != null && sel.key === block.key && sel.end > sel.start;
}

async function polishSelection(block: TextBlock) {
  const sel = selection.value;
  if (!sel || sel.key !== block.key || sel.end <= sel.start || polishBusy.value) return;
  const original = block.content.slice(sel.start, sel.end);
  polishBusy.value = true;
  polishBlockKey.value = block.key;
  try {
    const result = await polishPlanSectionApi(props.planId, props.title, original);
    // 过期守卫：请求返回前选区文本已被改动 → 丢弃
    if (block.content.slice(sel.start, sel.end) !== original) {
      message.info('选区已变化，已丢弃本次润色结果');
      return;
    }
    polishCompare.value = { blockKey: block.key, start: sel.start, end: sel.end, original, result: result.content };
  } catch (error) {
    message.error(error instanceof Error ? error.message : 'AI 润色失败，请稍后重试');
  } finally {
    polishBusy.value = false;
    polishBlockKey.value = null;
  }
}

function adoptPolish() {
  const compare = polishCompare.value;
  if (!compare) return;
  const block = blocks.value.find((b) => b.key === compare.blockKey);
  // 采纳时刻复核偏移：对比面板展示期间选区文本可能已被编辑，过期结果拼回会错位
  if (block?.kind !== 'text' || block.content.slice(compare.start, compare.end) !== compare.original) {
    polishCompare.value = null;
    message.info('内容已变化，本次润色结果已失效，请重新划选后再试');
    return;
  }
  block.content = block.content.slice(0, compare.start) + compare.result + block.content.slice(compare.end);
  polishCompare.value = null;
}

onMounted(() => {
  initBlocks();
  void nextTick(() => {
    for (const ta of textareaRefs.value) autoGrowEl(ta);
    textareaRefs.value[0]?.focus();
  });
});
</script>

<style scoped>
.inline-editor {
  border: 1px solid color-mix(in srgb, var(--accent) 40%, transparent);
  background: color-mix(in srgb, var(--accent) 6%, transparent);
  border-radius: 12px;
  padding: 12px 16px 14px;
}

/* 章级工具条 */
.ie-toolbar {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
}

.ie-state {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: var(--plan-accent-text);
  font-size: 12px;
  font-weight: 600;
}

.ie-pulse {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--accent);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent) 14%, transparent);
}

.ie-spacer {
  flex: 1;
}

.ie-act {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  flex: none;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
  transition: color 0.15s, background 0.15s;
}

.ie-act svg {
  width: 13px;
  height: 13px;
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.ie-act:hover {
  color: var(--ink);
  background: color-mix(in srgb, var(--ink) 7%, transparent);
}

.ie-act.primary {
  color: var(--accent);
  background: var(--accent-soft);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--accent) 40%, transparent);
}

.ie-act.primary:hover {
  background: color-mix(in srgb, var(--accent) 20%, transparent);
}

.ie-act.primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.ie-act.sm {
  width: 24px;
  height: 24px;
}

/* 文本块与格式工具条 */
.ie-text-block {
  position: relative;
}

.ie-text-block + .ie-insert,
.ie-table-block + .ie-insert,
.ie-image-block + .ie-insert {
  margin-top: 2px;
}

.fmt-bar {
  display: none;
  align-items: center;
  gap: 1px;
  flex-wrap: wrap;
  padding: 3px 5px;
  background: var(--surface-soft);
  border: 1px solid var(--line);
  border-bottom: 0;
  border-radius: 8px 8px 0 0;
}

.ie-text-block:focus-within .fmt-bar {
  display: flex;
}

.fmt-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  flex: none;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
  transition: color 0.12s, background 0.12s;
}

.fmt-btn svg {
  width: 14px;
  height: 14px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.fmt-btn:hover {
  color: var(--ink);
  background: color-mix(in srgb, var(--accent) 10%, transparent);
}

.fmt-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.fmt-btn:disabled:hover {
  color: var(--muted);
  background: transparent;
}

.fmt-sep {
  width: 1px;
  height: 15px;
  margin: 0 4px;
  background: var(--line);
}

.fmt-btn.ai {
  margin-left: auto;
  width: auto;
  gap: 4px;
  padding: 0 8px;
  color: var(--plan-accent-text);
}

.ie-textarea {
  display: block;
  width: 100%;
  min-height: 120px;
  max-height: 55vh;
  padding: 11px 14px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--surface);
  color: var(--ink);
  font-family: var(--font-data, ui-monospace, monospace);
  font-size: 12.5px;
  line-height: 1.8;
  overflow-y: auto;
  scrollbar-width: thin;
  resize: none;
  outline: none;
  caret-color: var(--accent);
  transition: border-color 0.15s;
}

.ie-text-block:focus-within .ie-textarea {
  border-color: color-mix(in srgb, var(--accent) 40%, transparent);
}

/* 选区润色对比 */
.ie-polish {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-top: 10px;
}

.ie-polish-pane {
  border: 1px solid var(--line);
  border-radius: 10px;
  overflow: hidden;
  background: var(--surface);
}

.ie-polish-pane.ai {
  border-color: color-mix(in srgb, var(--accent) 40%, transparent);
}

.ie-polish-tag {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  border-bottom: 1px solid var(--line);
  background: var(--surface-soft);
  color: var(--muted);
  font-size: 12px;
  font-weight: 600;
}

.ie-polish-pane.ai .ie-polish-tag {
  color: var(--plan-accent-text);
}

.ie-len {
  font-size: 10.5px;
  font-weight: 400;
  border: 1px solid var(--line);
  border-radius: 999px;
  padding: 0 7px;
}

.ie-polish-actions {
  margin-left: auto;
  display: flex;
  gap: 4px;
}

.ie-polish-body {
  max-height: 150px;
  overflow-y: auto;
  padding: 9px 13px;
  font-size: 12.5px;
  line-height: 1.8;
  color: var(--ink);
}

/* 表格块 / 图片块 */
.ie-table-block {
  border-radius: 10px;
}

.ie-image-block {
  position: relative;
  border: 1px solid var(--line);
  border-radius: 10px;
  overflow: hidden;
  background: var(--surface);
}

.ie-image {
  display: block;
  max-width: 100%;
  margin: 0 auto;
}

.ie-image-overlay {
  position: absolute;
  top: 8px;
  right: 8px;
  display: flex;
  gap: 6px;
  opacity: 0;
  transition: opacity 0.15s;
}

.ie-image-block:hover .ie-image-overlay {
  opacity: 1;
}

.ie-image-form {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 10px 12px;
}

.ie-image-form .ant-input-wrapper {
  flex: 1;
}

/* 插入点 */
.ie-insert {
  position: relative;
  display: flex;
  justify-content: center;
  min-height: 12px;
}

.ie-insert-btn {
  position: absolute;
  z-index: 2;
  top: -3px;
  display: none;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border: 1px solid color-mix(in srgb, var(--accent) 40%, transparent);
  border-radius: 50%;
  background: var(--surface);
  color: var(--accent);
  font-size: 13px;
  line-height: 1;
  cursor: pointer;
}

.ie-insert:hover .ie-insert-btn,
.ie-insert.open .ie-insert-btn {
  display: inline-flex;
}

.ie-insert-btn:hover {
  background: var(--accent);
  color: #fff;
}

.ie-insert-menu {
  position: absolute;
  z-index: 5;
  top: calc(100% + 2px);
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  gap: 4px;
  padding: 4px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 8px;
  box-shadow: 0 8px 24px rgb(16 24 40 / 12%);
}

.ie-insert-menu button {
  padding: 4px 12px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--ink);
  font-size: 12px;
  font-family: inherit;
  cursor: pointer;
}

.ie-insert-menu button:hover {
  background: color-mix(in srgb, var(--accent) 10%, transparent);
  color: var(--plan-accent-text);
}

/* 章尾插入 */
.ie-insert.tail {
  margin-top: 10px;
}

.ie-tail-btn {
  width: 100%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 7px 0;
  border: 1px dashed var(--line-strong);
  border-radius: 8px;
  background: transparent;
  color: var(--muted);
  font-size: 12px;
  font-family: inherit;
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s, background 0.15s;
}

.ie-tail-btn svg {
  width: 12px;
  height: 12px;
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linecap: round;
}

.ie-tail-btn:hover,
.ie-insert.tail.open .ie-tail-btn {
  color: var(--plan-accent-text);
  border-color: color-mix(in srgb, var(--accent) 40%, transparent);
  background: color-mix(in srgb, var(--accent) 6%, transparent);
}
</style>
