import { describe, expect, it } from 'vitest';
import { parseSectionBlocks as parse, serializeSectionBlocks, type SectionBlock } from './plan-markdown';
import { mapTableToSchema, PLAN_TABLE_SECTION_SCHEMAS } from './plan-table-schemas';

describe('parseSectionBlocks', () => {
  it('混合内容按 文本/表格/图片 顺序切分', () => {
    const content = [
      '引导段落第一行，',
      '同段第二行。',
      '',
      '| 对象 | 指标 |',
      '| --- | --- |',
      '| 查询 | TPS |',
      '',
      '![架构图](https://example.com/arch.png)',
      '',
      '尾部说明。',
    ].join('\n');
    const blocks = parse(content);
    expect(blocks.map((b) => b.kind)).toEqual(['text', 'table', 'image', 'text']);
    expect(blocks[0]).toEqual({ kind: 'text', content: '引导段落第一行，\n同段第二行。' });
    expect(blocks[1]).toEqual({ kind: 'table', header: ['对象', '指标'], rows: [['查询', 'TPS']] });
    expect(blocks[2]).toEqual({ kind: 'image', alt: '架构图', url: 'https://example.com/arch.png' });
    expect(blocks[3]).toEqual({ kind: 'text', content: '尾部说明。' });
  });

  it('不成形表格（不足表头+分隔线+数据行）降级为文本块', () => {
    const content = '| a | b |';
    const blocks = parse(content);
    expect(blocks).toHaveLength(1);
    expect(blocks[0].kind).toBe('text');
  });

  it('空内容产出空块序列', () => {
    expect(parse('')).toEqual([]);
    expect(parse('  \n \n')).toEqual([]);
  });
});

describe('serializeSectionBlocks（parse 的逆操作）', () => {
  it('混合内容往返保持块序与表格形状', () => {
    const content = '段落。\n\n| 对象 | 指标 |\n| --- | --- |\n| 查询 | TPS |\n\n![图](https://a/b.png)\n';
    const roundTrip = serializeSectionBlocks(parse(content));
    expect(roundTrip).toBe(content);
  });

  it('空文本段、无行表格、无 URL 图片不产出', () => {
    const blocks: SectionBlock[] = [
      { kind: 'text', content: '  \n ' },
      { kind: 'table', header: [], rows: [] },
      { kind: 'image', alt: '占位', url: ' ' },
      { kind: 'text', content: '正文' },
    ];
    expect(serializeSectionBlocks(blocks)).toBe('正文\n');
  });

  it('单元格竖线转义，序列化行数与单元格数不漂移', () => {
    const blocks: SectionBlock[] = [{ kind: 'table', header: ['a', 'b'], rows: [['x|y', 'z']] }];
    const md = serializeSectionBlocks(blocks);
    expect(md).toContain('| x\\|y | z |');
    expect(parse(md)[0]).toEqual({ kind: 'table', header: ['a', 'b'], rows: [['x|y', 'z']] });
  });
});

describe('mapTableToSchema（锁定表头网格数据源）', () => {
  const schema = PLAN_TABLE_SECTION_SCHEMAS['五、测试资源'];

  it('按表头名匹配列序：行与保存表头同步重排为 schema 列序，保存不发生错列', () => {
    const mapped = mapTableToSchema(
      { header: ['规格', '资源', '数量', '用途'], rows: [['8C16G', '压测机', '3 台', '注入']] },
      schema,
    );
    expect(mapped.header).toEqual(['资源', '规格', '数量', '用途']);
    expect(mapped.rows).toEqual([['压测机', '8C16G', '3 台', '注入']]);
  });

  it('列数少于 schema 时缺失列回退规范列名并补空，不丢已有数据', () => {
    const mapped = mapTableToSchema({ header: ['资源', '规格'], rows: [['压测机', '8C16G']] }, schema);
    expect(mapped.header).toEqual(['资源', '规格', '数量', '用途']);
    expect(mapped.rows[0]).toEqual(['压测机', '8C16G', '', '']);
  });
});
