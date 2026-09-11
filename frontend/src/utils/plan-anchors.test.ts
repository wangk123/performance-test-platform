import { describe, expect, it } from 'vitest';
import { deriveAnchors, findBestLine, matchScore, normalizeForMatch, similarity } from './plan-anchors';
import type { PlanComment } from '../types';

const BODY = [
  '# 计划',
  '## 三、测试指标',
  '登录接口 TPS ≥ 1000，CPU < 70%。',
  '',
  '1. 有序条目一：验证混合运行',
  '2. 有序条目二：摸清拐点',
  '## 四、测试范围',
  '覆盖核心交易链路。',
].join('\n');

function root(overrides: Partial<PlanComment>): PlanComment {
  return {
    id: 1, planId: 1, author: 'reviewer', content: '批注', kind: 'REVIEW', createdAt: '',
    parentId: null, anchorLine: null, anchorText: null, sectionTitle: null, bodyRevision: null,
    resolved: false, resolvedBy: null, resolvedAt: null, canResolve: true, canDelete: true, canEdit: true,
    ...overrides,
  };
}

describe('normalizeForMatch / similarity / matchScore', () => {
  it('去空白与 Markdown 修饰符并小写', () => {
    expect(normalizeForMatch('**登录接口** TPS ≥ 1000，`CPU`')).toBe('登录接口tps≥1000cpu');
  });
  it('相同文本相似度 1，无关文本低于阈值', () => {
    expect(similarity('登录接口tps', '登录接口tps')).toBe(1);
    expect(similarity('登录接口tps', 'zzzzzzzz')).toBeLessThan(0.6);
  });
  it('双向包含（较短方 ≥8 字符）满分：吸收列表标记/徽标尾部等渲染差异', () => {
    // 渲染文本「1. 」标记、徽标「1」尾部都不影响命中
    expect(matchScore('1. 有序条目一：验证混合运行', '有序条目一：验证混合运行')).toBe(1);
    expect(matchScore('有序条目一：验证混合运行', '有序条目一：验证混合运行1')).toBe(1);
  });
});

describe('findBestLine', () => {
  it('精确命中源行（含全局行号与章节归属）', () => {
    const best = findBestLine(BODY, '登录接口 TPS ≥ 1000，CPU < 70%。');
    expect(best).toMatchObject({ line: 2, sectionTitle: '三、测试指标' });
  });

  it('渲染文本（无列表标记）经包含判定命中有序列表源行', () => {
    const best = findBestLine(BODY, '有序条目二：摸清拐点');
    expect(best).toMatchObject({ line: 5, sectionTitle: '三、测试指标' });
  });

  it('无相似行返回 null', () => {
    expect(findBestLine(BODY, '这段话已经完全不存在了哦')).toBeNull();
  });
});

describe('deriveAnchors', () => {
  it('匹配成功 → ok（含实际命中的行号与归属章）', () => {
    const map = deriveAnchors(BODY, [root({ id: 1, anchorLine: 20, anchorText: '覆盖核心交易链路', sectionTitle: '三、测试指标' })]);
    expect(map.get(1)).toMatchObject({ state: 'ok', line: 7, sectionTitle: '四、测试范围' });
  });

  it('彻底找不到 → broken，归属回原章', () => {
    const map = deriveAnchors(BODY, [root({ id: 2, anchorLine: 20, anchorText: '这段话已被删除干净', sectionTitle: '九、风险与预案' })]);
    expect(map.get(2)).toMatchObject({ state: 'broken', line: null, sectionTitle: '九、风险与预案' });
  });

  it('无锚点批注不入结果', () => {
    const map = deriveAnchors(BODY, [root({ id: 3, anchorLine: null, anchorText: null, sectionTitle: null })]);
    expect(map.size).toBe(0);
  });
});
