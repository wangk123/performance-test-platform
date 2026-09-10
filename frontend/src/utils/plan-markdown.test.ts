import { describe, expect, it } from 'vitest';
import { checklistItemLines, listItemOffsets, splitBlocks } from './plan-markdown';

describe('splitBlocks', () => {
  it('段落以空行分界并携带局部行号', () => {
    const content = '第一段第一行\n第一段第二行\n\n第二段';
    expect(splitBlocks(content)).toEqual([
      { startLine: 0, endLine: 1, raw: '第一段第一行\n第一段第二行' },
      { startLine: 3, endLine: 3, raw: '第二段' },
    ]);
  });

  it('标题/引用/表格各自成块，连续表格行聚合', () => {
    const content = '### 小标题\n\n| a | b |\n|---|---|\n| 1 | 2 |\n| 3 | 4 |\n\n> 引用';
    expect(splitBlocks(content)).toEqual([
      { startLine: 0, endLine: 0, raw: '### 小标题' },
      { startLine: 2, endLine: 5, raw: '| a | b |\n|---|---|\n| 1 | 2 |\n| 3 | 4 |' },
      { startLine: 7, endLine: 7, raw: '> 引用' },
    ]);
  });

  it('代码栅栏吞并到闭合行', () => {
    const content = '```json\n{"a":1}\n```';
    expect(splitBlocks(content)).toEqual([
      { startLine: 0, endLine: 2, raw: '```json\n{"a":1}\n```' },
    ]);
  });

  it('连续列表行（含缩进续行）聚合为一块', () => {
    const content = '- 第一项\n- 第二项\n  续行\n\n段落';
    expect(splitBlocks(content)).toEqual([
      { startLine: 0, endLine: 2, raw: '- 第一项\n- 第二项\n  续行' },
      { startLine: 4, endLine: 4, raw: '段落' },
    ]);
  });

  it('空内容返回空数组', () => {
    expect(splitBlocks(null)).toEqual([]);
    expect(splitBlocks('')).toEqual([]);
  });
});

describe('listItemOffsets', () => {
  it('返回列表项首行的块内偏移', () => {
    expect(listItemOffsets('- 第一项\n- 第二项\n  续行')).toEqual([0, 1]);
    expect(listItemOffsets('1. 甲\n2、乙')).toEqual([0, 1]);
  });
});

describe('checklistItemLines', () => {
  it('清单项行号序与 parseChecklistGroups 的 index 一致（跳过标题与空行）', () => {
    const content = '### 入口准则\n\n- [ ] 指标已定义（自动）\n\n普通文字\n- [x] 脚本已关联';
    expect(checklistItemLines(content)).toEqual([2, 5]);
  });
});
