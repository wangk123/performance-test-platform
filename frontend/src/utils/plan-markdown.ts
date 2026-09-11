export const CANONICAL_HEADINGS = [
  '一、背景', '二、测试目的', '三、测试指标', '四、测试范围', '五、测试资源', '六、测试约束',
  '七、测试策略', '八、场景设计', '九、风险与预案', '十、排期与协作', '十一、附录', '十二、结论',
];

const EXECUTION_RECORD_HEADING = '#### 执行记录';

export interface Section {
  /** 规范标题（平台逻辑用：批注锚定、结构化编辑、模块激活；改名章节按序号容错映射）。 */
  title: string;
  /** 文档真实标题文本（展示用：章节导航与正文标题跟随文档，而非规范注册表）。 */
  heading: string;
  content: string;
  line: number; // 标题行行号（0 基），TOC 滚动定位用
}

export interface ChecklistItem {
  text: string;
  auto: boolean;
  checked: boolean;
}

export interface MarkdownTable {
  header: string[];
  rows: string[][];
}

export interface ScenarioBlock {
  heading: string;
  name: string;
  testType: string;
  purpose: string;
  /** **场景设置** 标记行（含）至 #### 执行记录（不含）或块尾的原始行；无标记时为 ''。 */
  settings: string;
  records: string[];
}

function canonicalTitleOf(line: string): string | null {
  if (!line.startsWith('## ')) return null;
  const text = line.slice(3).trim();
  const exact = CANONICAL_HEADINGS.find((h) => text === h);
  if (exact) return exact;
  for (const heading of CANONICAL_HEADINGS) {
    const numeral = heading.slice(0, heading.indexOf('、') + 1);
    if (numeral !== '十二、' && text.startsWith(numeral)) return heading;
  }
  return null;
}

export function splitSections(body: string | null | undefined): Section[] {
  if (!body) return [];
  const lines = body.split('\n');
  const sections: Section[] = [];
  let current: Section | null = null;
  lines.forEach((line, index) => {
    const title = canonicalTitleOf(line);
    if (title) {
      if (current) sections.push(current);
      current = { title, heading: line.slice(3).trim(), content: '', line: index };
    } else if (current) {
      current.content += line + '\n';
    }
  });
  if (current) sections.push(current);
  return sections;
}

export function extractSection(body: string | null | undefined, title: string): string | null {
  return splitSections(body).find((s) => s.title === title)?.content ?? null;
}

export function replaceSection(body: string, title: string, newContent: string): string {
  const lines = body.split('\n');
  let start = -1;
  let end = lines.length;
  for (let i = 0; i < lines.length; i++) {
    if (canonicalTitleOf(lines[i]) === title) {
      start = i + 1;
    } else if (start >= 0 && canonicalTitleOf(lines[i])) {
      end = i;
      break;
    }
  }
  if (start < 0) throw new Error(`章节缺失：${title}`);
  const normalized = newContent.endsWith('\n') || newContent === '' ? newContent : newContent + '\n';
  const before = lines.slice(0, start).join('\n') + '\n';
  const after = lines.slice(end).join('\n');
  return before + normalized + (after === '' ? '' : after);
}

/** 任务清单行：`- [ ] `/`- [x] `（约束章规范格式）。 */
const TASK_ITEM_RE = /^- \[( |x)\] /;
/** 容错识别的普通清单行：`1. `/`1、` 编号行与 `- `/`*` 圆点行（存量文档常见写法）。 */
const PLAIN_ITEM_RE = /^(?:\d{1,2}[.、)）]\s*|[-*•]\s+)(.+)$/;

function checklistTextOf(trimmed: string): string {
  if (trimmed.startsWith('- [x] ')) return trimmed.slice(6).trim();
  if (trimmed.startsWith('- [ ] ')) return trimmed.slice(6).trim();
  return (trimmed.match(PLAIN_ITEM_RE)?.[1] ?? '').trim();
}

function isChecklistItemLine(line: string): boolean {
  const trimmed = line.trim();
  return TASK_ITEM_RE.test(trimmed) || PLAIN_ITEM_RE.test(trimmed);
}

function splitAuto(text: string): { text: string; auto: boolean } {
  const auto = text.endsWith('（自动）') || text.endsWith('(自动)');
  return { text, auto };
}

export function parseChecklistItems(content: string | null | undefined): ChecklistItem[] {
  return parseChecklistGroups(content).flatMap((group) =>
    group.items.map(({ text, auto, checked }) => ({ text, auto, checked })),
  );
}

/**
 * 勾选回写：index 是全章清单行序号（与 parseChecklistGroups 同一口径，含容错的编号/圆点行）。
 * 目标是任务行时原位翻转；目标是普通清单行时整章归一为任务清单格式再翻转
 * （编号/圆点在清单语义里不承载顺序，规范格式即 `- [ ] `）。
 */
export function toggleChecklistItem(content: string, index: number): string {
  let cursor = -1;
  const targetIsPlain = (() => {
    for (const line of content.split('\n')) {
      if (!isChecklistItemLine(line)) continue;
      cursor += 1;
      if (cursor === index) {
        const trimmed = line.trim();
        return !TASK_ITEM_RE.test(trimmed);
      }
    }
    return false;
  })();
  cursor = -1;
  return content
    .split('\n')
    .map((line) => {
      if (!isChecklistItemLine(line)) return line;
      cursor += 1;
      const trimmed = line.trim();
      const indent = line.slice(0, line.length - line.trimStart().length);
      if (targetIsPlain) {
        if (cursor === index) return `${indent}- [x] ${checklistTextOf(trimmed)}`;
        return `${indent}- [ ] ${checklistTextOf(trimmed)}`;
      }
      if (cursor === index) {
        return trimmed.startsWith('- [x] ') ? line.replace('- [x] ', '- [ ] ') : line.replace('- [ ] ', '- [x] ');
      }
      return line;
    })
    .join('\n');
}

/** 分组清单条目：index 是全章 checkbox 行序号（toggleChecklistItem 口径）。 */
export interface GroupedChecklistItem extends ChecklistItem {
  index: number;
}

export interface ChecklistGroup {
  title: string | null;
  items: GroupedChecklistItem[];
}

/** 按 `### 标题`（或整行 `**标题**`）分组解析清单；任务行与容错的编号/圆点行都算条目。 */
export function parseChecklistGroups(content: string | null | undefined): ChecklistGroup[] {
  if (!content) return [];
  const groups: ChecklistGroup[] = [];
  let current: ChecklistGroup | null = null;
  let cursor = 0;
  for (const raw of content.split('\n')) {
    const line = raw.trim();
    const h3 = line.match(/^###\s+(.+)$/);
    const bold = line.match(/^\*\*([^*]+)\*\*：?$/);
    if (h3 || bold) {
      if (current && current.items.length) groups.push(current);
      current = { title: (h3?.[1] ?? bold?.[1] ?? '').trim(), items: [] };
      continue;
    }
    if (isChecklistItemLine(line)) {
      if (!current) current = { title: null, items: [] };
      const checked = line.startsWith('- [x] ');
      const { text, auto } = splitAuto(checklistTextOf(line));
      current.items.push({ text, auto, checked, index: cursor });
      cursor += 1;
    }
  }
  if (current && current.items.length) groups.push(current);
  return groups;
}

/** `|---|` 分隔行识别（与后端 PlanAcceptanceParser 口径一致）：全部单元格为 --- 形态。 */
function isSeparatorRow(cells: string[]): boolean {
  const meaningful = cells.map((cell) => cell.trim()).filter((cell) => cell !== '');
  return meaningful.length > 0 && meaningful.every((cell) => /^:?-{2,}:?$/.test(cell));
}

export function parseMarkdownTable(content: string | null | undefined): MarkdownTable | null {
  if (!content) return null;
  const rows = content
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line.startsWith('|') && line.endsWith('|'))
    .map(splitMarkdownRow)
    .filter((cells) => !isSeparatorRow(cells));
  if (rows.length < 2) return null;
  return { header: rows[0], rows: rows.slice(1) };
}

/** 按未转义的 `|` 切分单元格，并反转义 `\|`（toCell 序列化口径的逆，保证转义单元格往返不漂移）。 */
function splitMarkdownRow(line: string): string[] {
  return line
    .slice(1, -1)
    .split(/(?<!\\)\|/g)
    .map((cell) => cell.trim().replace(/\\\|/g, '|'));
}

/** 单元格内联化：竖线转义、换行压空格，保证序列化回的表格行数与单元格数不漂移。 */
function toCell(text: string): string {
  return (text ?? '').replace(/\|/g, '\\|').replace(/\r?\n/g, ' ').trim();
}

/** Markdown 表序列化（parseMarkdownTable 的逆操作）：表头 + 分隔线 + 数据行。 */
export function toMarkdownTable(header: string[], rows: string[][]): string {
  const width = header.length;
  const line = (cells: string[]) =>
    `| ${Array.from({ length: width }, (_, i) => toCell(cells[i] ?? '')).join(' | ')} |`;
  const separator = `| ${Array.from({ length: width }, () => '---').join(' | ')} |`;
  return [line(header), separator, ...rows.map((row) => line(row))].join('\n');
}

export function parseScenarioBlocks(body: string | null | undefined): ScenarioBlock[] {
  const section = extractSection(body, '八、场景设计');
  if (!section) return [];
  const lines = section.split('\n');
  const blocks: ScenarioBlock[] = [];
  let current: { heading: string; lines: string[] } | null = null;
  for (const line of lines) {
    if (line.startsWith('### ')) {
      if (current) blocks.push(toBlock(current));
      current = { heading: line.slice(4).trim(), lines: [] };
    } else if (current) {
      current.lines.push(line);
    }
  }
  if (current) blocks.push(toBlock(current));
  return blocks;
}

const SETTINGS_MARKER = '**场景设置**';

function toBlock(raw: { heading: string; lines: string[] }): ScenarioBlock {
  const parts = raw.heading.split(' · ');
  const body = raw.lines.join('\n');
  const purpose = body.match(/\*\*场景目的\*\*：(.*)/)?.[1]?.trim() ?? '';
  const settings = extractSettings(body);
  const records = body
    .split('\n')
    .filter((line) => line.trim().startsWith('- ') && !line.trim().startsWith('- ['))
    .map((line) => line.trim().slice(2));
  return {
    heading: raw.heading,
    name: (parts[0] ?? '').replace(/^S\d+\s*/, '').trim(),
    testType: parts[1] ?? '',
    purpose,
    settings,
    records,
  };
}

function extractSettings(body: string): string {
  const lines = body.split('\n');
  const start = lines.findIndex((line) => line.trim().startsWith(SETTINGS_MARKER));
  if (start < 0) return '';
  let end = lines.length;
  for (let i = start + 1; i < lines.length; i++) {
    if (lines[i].trim().startsWith(EXECUTION_RECORD_HEADING)) {
      end = i;
      break;
    }
  }
  return lines.slice(start, end).join('\n');
}

export function parseExecutionRecords(body: string | null | undefined, scenarioName: string): string[] {
  return parseScenarioBlocks(body).find((b) => b.name === scenarioName)?.records ?? [];
}

// ---------- 块切分（行级批注的锚定单元，spec §5.1） ----------

/** 归一化锚点匹配文本（spec §5.3）：去空白与 Markdown 修饰符（含全角逗号）、转小写。plan-anchors 同源复用。 */
export function normalizeForMatch(text: string): string {
  return text.replace(/[\s#*>`|~_[\]()\\，-]/g, '').toLowerCase();
}

// ---------- 章内容块模型（Pretty 行内编辑）：文本段 / 表格 / 独立图片行 ----------

export interface SectionTextBlock { kind: 'text'; content: string }
export interface SectionTableBlock { kind: 'table'; header: string[]; rows: string[][] }
export interface SectionImageBlock { kind: 'image'; alt: string; url: string }
export type SectionBlock = SectionTextBlock | SectionTableBlock | SectionImageBlock;

/** 独立图片行：`![alt](url)`（URL 含空格等非常规写法不识别，保持为文本块）。 */
const IMAGE_LINE_RE = /^!\[([^\]]*)\]\((\S+)\)$/;

/**
 * 章内容 → 块序列：连续 `|` 行为表格（parseMarkdownTable 解析失败如列数不齐时降级为文本块，不阻塞编辑），
 * 独立图片行为图片块，其余行为文本段。段内空行结构原样保留。
 */
export function parseSectionBlocks(content: string): SectionBlock[] {
  const blocks: SectionBlock[] = [];
  const lines = content.split('\n');
  let textRun: string[] = [];
  const flushText = () => {
    const text = textRun.join('\n').replace(/^\n+|\n+$/g, '');
    if (text.trim()) blocks.push({ kind: 'text', content: text });
    textRun = [];
  };
  for (let i = 0; i < lines.length; i++) {
    const trimmed = lines[i].trim();
    if (trimmed.startsWith('|')) {
      let end = i;
      while (end < lines.length && lines[end].trim().startsWith('|')) end += 1;
      const table = parseMarkdownTable(lines.slice(i, end).join('\n'));
      if (table) {
        flushText();
        blocks.push({ kind: 'table', header: table.header, rows: table.rows });
      } else {
        textRun.push(...lines.slice(i, end));
      }
      i = end - 1;
      continue;
    }
    const image = trimmed.match(IMAGE_LINE_RE);
    if (image) {
      flushText();
      blocks.push({ kind: 'image', alt: image[1], url: image[2] });
      continue;
    }
    textRun.push(lines[i]);
  }
  flushText();
  return blocks;
}

/** 块序列 → 章内容（parseSectionBlocks 的逆操作）：块间空行分隔；空文本段、无行表格、无 URL 图片不产出。 */
export function serializeSectionBlocks(blocks: SectionBlock[]): string {
  const parts: string[] = [];
  for (const block of blocks) {
    if (block.kind === 'text') {
      const text = block.content.replace(/^\n+|\n+$/g, '');
      if (text.trim()) parts.push(text);
    } else if (block.kind === 'table') {
      if (block.header.length > 0 && block.rows.length > 0) parts.push(toMarkdownTable(block.header, block.rows));
    } else if (block.url.trim()) {
      parts.push(`![${block.alt}](${block.url.trim()})`);
    }
  }
  return parts.length > 0 ? `${parts.join('\n\n')}\n` : '';
}
