import { describe, expect, it } from 'vitest';
import { deriveAnchors, normalizeForMatch, similarity } from './plan-anchors';
import type { PlanComment } from '../types';

const BODY = [
  '# 计划',
  '## 三、测试指标',
  '登录接口 TPS ≥ 1000，CPU < 70%。',
  '',
  '下单接口 P95 ≤ 200ms。',
  '## 四、测试范围',
  '覆盖核心交易链路。',
].join('\n');

function root(overrides: Partial<PlanComment>): PlanComment {
  return {
    id: 1, planId: 1, author: 'reviewer', content: '批注', kind: 'REVIEW', createdAt: '',
    parentId: null, anchorLine: null, anchorText: null, sectionTitle: null, bodyRevision: null,
    resolved: false, resolvedBy: null, resolvedAt: null, canResolve: true, canDelete: true,
    ...overrides,
  };
}

describe('normalizeForMatch / similarity', () => {
  it('去空白与 Markdown 修饰符并小写', () => {
    expect(normalizeForMatch('**登录接口** TPS ≥ 1000，`CPU`')).toBe('登录接口tps≥1000cpu');
  });
  it('相同文本相似度 1，无关文本低于阈值', () => {
    expect(similarity('登录接口tps', '登录接口tps')).toBe(1);
    expect(similarity('登录接口tps', 'zzzzzzzz')).toBeLessThan(0.6);
  });
});

describe('deriveAnchors', () => {
  it('精确命中 → ok', () => {
    const map = deriveAnchors(BODY, [root({ id: 1, anchorLine: 2, anchorText: '登录接口 TPS ≥ 1000', sectionTitle: '三、测试指标' })]);
    expect(map.get(1)).toMatchObject({ state: 'ok', line: 2, sectionTitle: '三、测试指标' });
  });

  it('行号漂移但章内文本可匹配 → remounted 到新行', () => {
    const map = deriveAnchors(BODY, [root({ id: 2, anchorLine: 6, anchorText: '下单接口 P95 ≤ 200ms', sectionTitle: '三、测试指标' })]);
    expect(map.get(2)?.state).toBe('remounted');
    expect(map.get(2)?.line).toBe(4);
  });

  it('章内找不到、全文可找 → remounted；带新章节归属', () => {
    const map = deriveAnchors(BODY, [root({ id: 3, anchorLine: 20, anchorText: '覆盖核心交易链路', sectionTitle: '三、测试指标' })]);
    expect(map.get(3)?.state).toBe('remounted');
    expect(map.get(3)?.line).toBe(6);
  });

  it('彻底找不到 → broken，归属回原章', () => {
    const map = deriveAnchors(BODY, [root({ id: 4, anchorLine: 20, anchorText: '这段话已被删除干净', sectionTitle: '九、风险与预案' })]);
    expect(map.get(4)?.state).toBe('broken');
    expect(map.get(4)?.line).toBeNull();
    expect(map.get(4)?.sectionTitle).toBe('九、风险与预案');
  });

  it('无锚点批注不入结果', () => {
    const map = deriveAnchors(BODY, [root({ id: 5, anchorLine: null, anchorText: null, sectionTitle: null })]);
    expect(map.size).toBe(0);
  });
});
