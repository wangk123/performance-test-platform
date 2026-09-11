import { describe, expect, it } from 'vitest';
import MarkdownIt from 'markdown-it';
import { alignBlocks, blockAnchorLines, splitBlocks, splitSections } from './plan-markdown';

// markdown-it 为 md-editor-v3 的既有传递依赖（MdPreview 渲染内核），此处复用其类型与实现
// 做块模型一致性回归：splitBlocks/alignBlocks 的自研块模型必须与真实渲染器的顶层结构一致，
// 否则批注无法锚定（历史上松散列表、有序列表标记两轮实测问题均源于此）。
const md = new MarkdownIt({ html: true });

const DOC = `## 一、背景

背景段落，介绍容量验证目标。

- 无序甲：POST /api/a（含前置校验）
- 无序乙：GET /api/b

1. 有序一：验证基准容量
2. 有序二：验证混合运行
3. 有序三：摸清系统拐点

## 二、指标

| 对象 | 指标 | 目标值 |
| --- | --- | --- |
| 放款提交 | TPS | ≥ 120 |
| 放款接口 | P95 | ≤ 800 ms |

## 三、约束

- [ ] 指标已定义（自动）
- [x] 脚本已关联

范围说明紧跟表格无空行时，渲染器与块模型的差异必须被容错对齐吸收。

| 阶段 | 内容 |
| --- | --- |
| 准备期 | 环境与数据 |

- 松散一

- 松散二

> 引用一行注意事项

\`\`\`json
{"threads": 50}
\`\`\`
`;

interface TopBlock { kind: string; text: string; liCount: number; tableDataRows: number }

/** markdown-it 顶层块walk：收集每个顶层块的类型、纯文本、li 数、表格数据行数。 */
function topLevelBlocks(content: string): TopBlock[] {
  const tokens = md.parse(content, {});
  const out: TopBlock[] = [];
  const skips = new Set([
    'paragraph_close', 'heading_close', 'bullet_list_close', 'ordered_list_close',
    'table_close', 'blockquote_close', 'fence', 'inline',
  ]);
  for (let i = 0; i < tokens.length; i++) {
    const t = tokens[i];
    if (t.level !== 0 || t.nesting !== 1 || skips.has(t.type)) continue;
    const kind = t.type.replace('_open', '');
    let text = '';
    let liCount = 0;
    let tableDataRows = 0;
    let inThead = false;
    for (let j = i + 1; j < tokens.length && tokens[j].level > 0; j++) {
      if (tokens[j].type === 'inline') text += `${tokens[j].content}\n`;
      if (tokens[j].type === 'list_item_open') liCount++;
      if (tokens[j].type === 'thead_open') inThead = true;
      if (tokens[j].type === 'thead_close') inThead = false;
      if (tokens[j].type === 'tr_open' && !inThead) tableDataRows++;
    }
    out.push({ kind, text: text.trimEnd(), liCount, tableDataRows });
  }
  return out;
}

describe('块模型与 markdown-it 渲染对齐一致性（spec §5.1 回归）', () => {
  it('每个渲染顶层块都有匹配的模型块；列表项数/表格数据行数与行级锚点数一致', () => {
    for (const section of splitSections(DOC)) {
      const content = section.content.trimEnd();
      if (!content.trim()) continue;
      const rendered = topLevelBlocks(content);
      const blocks = splitBlocks(content);
      const alignment = alignBlocks(rendered.map((r) => r.text), blocks);
      rendered.forEach((r, i) => {
        const matched = alignment[i];
        expect(matched, `${section.title} 渲染块[${i}] ${r.kind}「${r.text.slice(0, 20)}」未匹配任何模型块`).not.toBeNull();
        const anchorLines = blockAnchorLines(blocks[matched as number]);
        if (r.kind === 'bullet_list' || r.kind === 'ordered_list') {
          expect(anchorLines.length, `${section.title} ${r.kind} 列表项数 vs 行锚点数`).toBe(r.liCount);
        }
        if (r.kind === 'table') {
          expect(anchorLines.length, `${section.title} 表格数据行数 vs 行锚点数`).toBe(r.tableDataRows);
        }
      });
    }
  });

  it('有序列表的块文本剥离标记后与渲染文本前缀一致（实测：数字标记不剥离则整章匹配失败）', () => {
    const blocks = splitBlocks('1. 有序一：验证基准容量\n2. 有序二：验证混合运行');
    const renderedText = '有序一：验证基准容量有序二：验证混合运行';
    const alignment = alignBlocks([renderedText], blocks);
    expect(alignment).toEqual([0]);
  });
});
