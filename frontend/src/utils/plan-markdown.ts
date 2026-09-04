export const CANONICAL_HEADINGS = [
  '一、背景', '二、测试目的与指标', '三、测试范围', '四、测试资源', '五、测试约束',
  '六、测试策略', '七、场景设计', '八、风险与预案', '九、排期与协作', '十、附录', '十一、结论',
];

const EXECUTION_RECORD_HEADING = '#### 执行记录';

export interface Section {
  title: string;
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
  records: string[];
}

function canonicalTitleOf(line: string): string | null {
  if (!line.startsWith('## ')) return null;
  const text = line.slice(3).trim();
  const exact = CANONICAL_HEADINGS.find((h) => text === h);
  if (exact) return exact;
  for (const heading of CANONICAL_HEADINGS) {
    const numeral = heading.slice(0, heading.indexOf('、') + 1);
    if (numeral !== '十一、' && text.startsWith(numeral)) return heading;
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
      current = { title, content: '', line: index };
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

export function parseChecklistItems(content: string | null | undefined): ChecklistItem[] {
  if (!content) return [];
  return content
    .split('\n')
    .filter((line) => /^- \[( |x)\] /.test(line.trim()))
    .map((line) => {
      const trimmed = line.trim();
      const checked = trimmed.startsWith('- [x] ');
      const text = trimmed.slice(6).trim();
      const auto = text.endsWith('（自动）') || text.endsWith('(自动)');
      return { text, auto, checked };
    });
}

export function toggleChecklistItem(content: string, index: number): string {
  let cursor = -1;
  return content
    .split('\n')
    .map((line) => {
      if (/^- \[( |x)\] /.test(line.trim())) {
        cursor += 1;
        if (cursor === index) {
          return line.trim().startsWith('- [x] ') ? line.replace('- [x] ', '- [ ] ') : line.replace('- [ ] ', '- [x] ');
        }
      }
      return line;
    })
    .join('\n');
}

export function parseMarkdownTable(content: string | null | undefined): MarkdownTable | null {
  if (!content) return null;
  const rows = content
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line.startsWith('|') && line.endsWith('|'))
    .map((line) => line.slice(1, -1).split('|').map((cell) => cell.trim()));
  if (rows.length < 2) return null;
  return { header: rows[0], rows: rows.slice(1) };
}

export function parseScenarioBlocks(body: string | null | undefined): ScenarioBlock[] {
  const section = extractSection(body, '七、场景设计');
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

function toBlock(raw: { heading: string; lines: string[] }): ScenarioBlock {
  const parts = raw.heading.split(' · ');
  const body = raw.lines.join('\n');
  const purpose = body.match(/\*\*场景目的\*\*：(.*)/)?.[1]?.trim() ?? '';
  const records = body
    .split('\n')
    .filter((line) => line.trim().startsWith('- ') && !line.trim().startsWith('- ['))
    .map((line) => line.trim().slice(2));
  return {
    heading: raw.heading,
    name: (parts[0] ?? '').replace(/^S\d+\s*/, '').trim(),
    testType: parts[1] ?? '',
    purpose,
    records,
  };
}

export function parseExecutionRecords(body: string | null | undefined, scenarioName: string): string[] {
  return parseScenarioBlocks(body).find((b) => b.name === scenarioName)?.records ?? [];
}
